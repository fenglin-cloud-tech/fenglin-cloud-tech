package com.fenglin.common.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(String eventId, String eventType, String module, String action, String riskLevel,
                         Long tenantId, Long userId, String clientType, String requestId,
                         String resourceType, String resourceId, Instant occurredAt,
                         Map<String, Object> detail) {

    /** 兼容既有调用方：不携带 detail。 */
    public AuditEvent(String eventId, String eventType, String module, String action, String riskLevel,
                      Long tenantId, Long userId, String clientType, String requestId,
                      String resourceType, String resourceId, Instant occurredAt) {
        this(eventId, eventType, module, action, riskLevel, tenantId, userId, clientType, requestId,
                resourceType, resourceId, occurredAt, null);
    }
}
