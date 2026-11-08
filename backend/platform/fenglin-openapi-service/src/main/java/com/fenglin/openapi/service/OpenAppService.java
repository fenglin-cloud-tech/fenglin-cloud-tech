package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.OpenApiErrorCode;
import com.fenglin.openapi.mapper.ApiCallLogMapper;
import com.fenglin.openapi.mapper.ApiQuotaMapper;
import com.fenglin.openapi.mapper.OpenAppMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppView;
import com.fenglin.openapi.web.dto.OpenApiDtos.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUB-API-1001 应用注册、1002 应用审核、1006 开发者门户应用列表。
 */
@Service
public class OpenAppService {

    private static final Set<String> OWNER_TYPES = Set.of("ISV", "INTERNAL", "CUSTOMER");

    private final OpenAppMapper appMapper;
    private final ApiCallLogMapper callLogMapper;
    private final ApiQuotaMapper quotaMapper;
    private final AuditSink auditSink;

    public OpenAppService(OpenAppMapper appMapper,
                          ApiCallLogMapper callLogMapper,
                          ApiQuotaMapper quotaMapper,
                          AuditSink auditSink) {
        this.appMapper = appMapper;
        this.callLogMapper = callLogMapper;
        this.quotaMapper = quotaMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public AppCreateResponse create(AppCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String name = requireText(request.name(), "name", 128);
        String ownerType = request.ownerType() == null || request.ownerType().isBlank()
                ? "INTERNAL" : request.ownerType().trim().toUpperCase();
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "owner_type 仅支持 ISV/INTERNAL/CUSTOMER");
        }
        if (appMapper.findByTenantAndName(tenantId, name) != null) {
            throw new BizException(OpenApiErrorCode.APP_EXISTS, "应用已存在: " + name);
        }
        String code = appMapper.nextCode();
        long id = IdWorker.getId();
        appMapper.insert(id, tenantId, code, name, ownerType, null,
                join(request.scopes(), 1024, "scopes"), join(request.redirectUris(), 1024, "redirect_uris"),
                operatorId);
        audit("OPENAPI_APP_CREATED", tenantId, operatorId, String.valueOf(id));
        return new AppCreateResponse(id, code, "PENDING");
    }

    @Transactional
    public ReviewResponse review(long appId, String action, String reason, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = requireApp(tenantId, appId);
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "action 仅支持 APPROVE/REJECT");
        }
        String reviewStatus = "APPROVE".equals(normalized) ? "APPROVED" : "REJECTED";
        String status = "APPROVE".equals(normalized) ? "ENABLED" : "DISABLED";
        int updated = appMapper.updateReview(tenantId, appId, reviewStatus, status, operatorId);
        if (updated == 0) {
            throw new BizException(CommonErrorCode.CONFLICT,
                    "应用当前状态不可审核: " + app.get("review_status"));
        }
        audit("APPROVE".equals(normalized) ? "OPENAPI_APP_APPROVED" : "OPENAPI_APP_REJECTED",
                tenantId, operatorId, String.valueOf(appId));
        return new ReviewResponse(appId, reviewStatus, status);
    }

    @Transactional(readOnly = true)
    public List<PortalAppView> listPortalApps() {
        long tenantId = TenantContext.requireTenantId();
        return appMapper.listByTenant(tenantId).stream()
                .map(row -> {
                    long appId = ((Number) row.get("id")).longValue();
                    return new PortalAppView(
                            appId,
                            (String) row.get("code"),
                            (String) row.get("status"),
                            callLogMapper.countToday(tenantId, appId),
                            quotaMapper.maxDailyLimit(tenantId, appId));
                })
                .toList();
    }

    Map<String, Object> requireApp(long tenantId, long appId) {
        Map<String, Object> app = appMapper.findById(tenantId, appId);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        return app;
    }

    private void audit(String eventType, long tenantId, Long operatorId, String resourceId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), eventType, "openapi", "WRITE", "MEDIUM",
                tenantId, operatorId, context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "OPEN_APP", resourceId, Instant.now()));
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String join(List<String> values, int maxLength, String field) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList());
        if (joined.length() > maxLength) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return joined;
    }
}
