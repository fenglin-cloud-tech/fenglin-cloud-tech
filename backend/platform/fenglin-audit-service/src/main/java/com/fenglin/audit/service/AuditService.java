package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.mapper.AuditLogMapper;
import com.fenglin.audit.web.dto.AuditLogView;
import com.fenglin.audit.web.dto.ChainVerifyResult;
import com.fenglin.audit.web.dto.RecordAuditRequest;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuditService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter LOG_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter PARTITION_KEY = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final AuditLogMapper auditLogMapper;
    private final AuditChainService auditChainService;
    private final boolean chainEnabled;

    public AuditService(AuditLogMapper auditLogMapper, AuditChainService auditChainService,
                        @Value("${fenglin.audit.chain.enabled:true}") boolean chainEnabled) {
        this.auditLogMapper = auditLogMapper;
        this.auditChainService = auditChainService;
        this.chainEnabled = chainEnabled;
    }

    @Transactional
    public Map<String, Object> record(RecordAuditRequest request) {
        Long contextTenantId = TenantContext.getTenantId();
        long tenantId = contextTenantId == null ? 0L : contextTenantId;
        long id = IdWorker.getId();
        String logNo = "AUD" + LOG_NO_TIME.format(java.time.LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        Instant occurredAt = AuditChainHasher.normalizeInstant(
                request.occurredAt() == null ? Instant.now() : request.occurredAt());
        String result = normalize(request.result(), "SUCCESS");
        String riskLevel = normalize(request.riskLevel(), "LOW");
        String detailText = request.detail() == null ? null : request.detail().toString();

        auditLogMapper.insert(id, tenantId, logNo, request.eventType(), request.module(), request.action(),
                request.operatorId(), request.operatorName(), request.targetType(), request.targetId(),
                request.requestId(), request.traceId(), request.clientType(), null,
                result, riskLevel, detailText, occurredAt);

        if (chainEnabled) {
            String content = AuditChainHasher.content(id, tenantId, logNo, request.eventType(), request.module(),
                    request.action(), request.operatorId(), request.operatorName(), request.targetType(),
                    request.targetId(), request.requestId(), request.traceId(), request.clientType(),
                    result, riskLevel, AuditChainHasher.canonicalDetail(request.detail()), occurredAt);
            // 链写入失败直接抛异常，与日志插入同事务回滚（B1 裁决：不落半截审计）
            auditChainService.append(id, tenantId, PARTITION_KEY.format(occurredAt), content);
        }
        return Map.of("log_id", id, "log_no", logNo);
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> list(String eventType, Long operatorId, String targetType, String targetId,
                                   String requestId, String result, Instant from, Instant to,
                                   int page, int size) {
        long tenantId = TenantContext.requireTenantId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        return auditLogMapper.findPage(tenantId, normalize(eventType), operatorId, normalize(targetType),
                        normalize(targetId), normalize(requestId), normalize(result), from, to,
                        normalizedSize, (normalizedPage - 1) * normalizedSize)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long count(String eventType, Long operatorId, String targetType, String targetId,
                      String requestId, String result, Instant from, Instant to) {
        long tenantId = TenantContext.requireTenantId();
        return auditLogMapper.count(tenantId, normalize(eventType), operatorId, normalize(targetType),
                normalize(targetId), normalize(requestId), normalize(result), from, to);
    }

    @Transactional(readOnly = true)
    public AuditLogView get(long id) {
        Map<String, Object> row = auditLogMapper.findById(TenantContext.requireTenantId(), id);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "审计记录不存在: " + id);
        }
        return toView(row);
    }

    @Transactional(readOnly = true)
    public ChainVerifyResult verifyChain(long tenantId, Instant from, Instant to) {
        return auditChainService.verify(tenantId, from, to);
    }

    private AuditLogView toView(Map<String, Object> row) {
        return new AuditLogView(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("tenant_id")).longValue(),
                (String) row.get("log_no"),
                (String) row.get("event_type"),
                (String) row.get("module"),
                (String) row.get("action"),
                row.get("operator_id") == null ? null : ((Number) row.get("operator_id")).longValue(),
                (String) row.get("operator_name"),
                (String) row.get("target_type"),
                (String) row.get("target_id"),
                (String) row.get("request_id"),
                (String) row.get("trace_id"),
                (String) row.get("client_type"),
                (String) row.get("result"),
                (String) row.get("risk_level"),
                parseDetail(row.get("detail")),
                toInstant(row.get("occurred_at")));
    }

    private JsonNode parseDetail(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
