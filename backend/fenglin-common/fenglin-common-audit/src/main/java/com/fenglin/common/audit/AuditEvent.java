package com.fenglin.common.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(String eventId, String eventType, String module, String action, String riskLevel,
                         Long tenantId, Long userId, String clientType, String requestId,
                         String resourceType, String resourceId, Instant occurredAt,
                         Map<String, Object> detail) {

    /**
     * 平台级审计保留租户，必须与 {@code TenantContext.PLATFORM_TENANT_ID} 保持一致。
     * tenantId 为 null 表示事件不属于任何租户，由 HttpAuditSink 落到该保留租户。
     */
    public static final long PLATFORM_TENANT_ID = -1L;

    /** 兼容既有调用方：不携带 detail。 */
    public AuditEvent(String eventId, String eventType, String module, String action, String riskLevel,
                      Long tenantId, Long userId, String clientType, String requestId,
                      String resourceType, String resourceId, Instant occurredAt) {
        this(eventId, eventType, module, action, riskLevel, tenantId, userId, clientType, requestId,
                resourceType, resourceId, occurredAt, null);
    }
}
