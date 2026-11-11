package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.OpenApiErrorCode;
import com.fenglin.openapi.mapper.ApiQuotaRequestMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaApplyRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaItem;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequestReview;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequestView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OPEN-03：开发者自助提交配额申请，平台审批通过后写入 hub_api_quota。
 *
 * <p>状态机：PENDING → APPROVED / REJECTED；审批与配额写入在同一事务内，失败整体回滚。</p>
 */
@Service
public class OpenApiQuotaRequestService {

    private final ApiQuotaRequestMapper requestMapper;
    private final OpenAppService appService;
    private final OpenApiQuotaService quotaService;
    private final AuditSink auditSink;

    public OpenApiQuotaRequestService(ApiQuotaRequestMapper requestMapper, OpenAppService appService,
                                      OpenApiQuotaService quotaService, AuditSink auditSink) {
        this.requestMapper = requestMapper;
        this.appService = appService;
        this.quotaService = quotaService;
        this.auditSink = auditSink;
    }

    /** 开发者门户：提交配额申请。 */
    @Transactional
    public QuotaRequestView apply(QuotaApplyRequest request, long userId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null || request.appId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "app_id 不能为空");
        }
        appService.requireOwnedApp(request.appId(), userId);
        String apiPath = normalizePath(request.apiPath());
        Integer qps = request.qpsLimit();
        Long daily = request.dailyLimit();
        if (qps == null && daily == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "qps_limit 与 daily_limit 至少填写一项");
        }
        if (qps != null && qps <= 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "qps_limit 必须为正整数");
        }
        if (daily != null && daily < 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "daily_limit 不能为负数");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "申请理由不能为空");
        }
        if (reason.length() > 512) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "申请理由不能超过 512 字");
        }
        if (requestMapper.countPending(tenantId, request.appId(), apiPath) > 0) {
            throw new BizException(OpenApiErrorCode.QUOTA_REQUEST_EXISTS);
        }
        long id = IdWorker.getId();
        requestMapper.insert(id, tenantId, request.appId(), apiPath, qps, daily, reason, userId);
        publish("OPENAPI_QUOTA_REQUESTED", tenantId, userId, id);
        return toView(requestMapper.findById(tenantId, id));
    }

    /** 开发者门户：我的申请列表。 */
    @Transactional(readOnly = true)
    public PageResult<QuotaRequestView> listMine(long userId, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        List<QuotaRequestView> records = requestMapper
                .listByApplicant(tenantId, userId, (int) ((safePage - 1) * safeSize), (int) safeSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, requestMapper.countByApplicant(tenantId, userId), safePage, safeSize);
    }

    /** 平台管理端：按状态查看全部申请（待审批优先）。 */
    @Transactional(readOnly = true)
    public PageResult<QuotaRequestView> listForReview(String status, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<QuotaRequestView> records = requestMapper
                .listForReview(tenantId, normalized, (int) ((safePage - 1) * safeSize), (int) safeSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, requestMapper.countForReview(tenantId, normalized), safePage, safeSize);
    }

    /** 平台管理端：批准（同步写配额）或驳回。 */
    @Transactional
    public QuotaRequestView review(long id, QuotaRequestReview request, long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> row = requestMapper.findById(tenantId, id);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "配额申请不存在: " + id);
        }
        String action = request == null || request.action() == null ? "" : request.action().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "action 仅支持 APPROVE/REJECT");
        }
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.length() > 512) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "审批意见不能超过 512 字");
        }
        if ("REJECT".equals(action) && (comment == null || comment.isBlank())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "驳回时必须填写审批意见");
        }
        String status = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        if (requestMapper.decide(tenantId, id, status, comment, operatorId) == 0) {
            throw new BizException(OpenApiErrorCode.QUOTA_REQUEST_DECIDED);
        }
        if ("APPROVE".equals(action)) {
            long appId = ((Number) row.get("app_id")).longValue();
            String apiPath = String.valueOf(row.get("api_path"));
            Integer qps = toInteger(row.get("requested_qps"));
            Long daily = toLong(row.get("requested_daily"));
            quotaService.upsert(new QuotaRequest(appId,
                    List.of(new QuotaItem(apiPath, qps, daily))), operatorId);
        }
        publish("OPENAPI_QUOTA_REVIEWED", tenantId, operatorId, id);
        return toView(requestMapper.findById(tenantId, id));
    }

    private void publish(String action, long tenantId, long operatorId, long requestId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), action, "openapi",
                "UPDATE", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(),
                "API_QUOTA_REQUEST", String.valueOf(requestId), Instant.now()));
    }

    private String normalizePath(String apiPath) {
        String path = apiPath == null ? "" : apiPath.trim();
        if (path.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "api_path 不能为空");
        }
        if (path.length() > 256) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "api_path 不能超过 256 字符");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private QuotaRequestView toView(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new QuotaRequestView(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("app_id")).longValue(),
                String.valueOf(row.get("api_path")),
                toInteger(row.get("requested_qps")),
                toLong(row.get("requested_daily")),
                row.get("reason") == null ? null : String.valueOf(row.get("reason")),
                String.valueOf(row.get("status")),
                ((Number) row.get("applied_by")).longValue(),
                row.get("decided_by") == null ? null : ((Number) row.get("decided_by")).longValue(),
                toInstant(row.get("decided_at")),
                row.get("decision_comment") == null ? null : String.valueOf(row.get("decision_comment")),
                toInstant(row.get("created_at")));
    }

    private static Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof OffsetDateTime time) {
            return time.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
