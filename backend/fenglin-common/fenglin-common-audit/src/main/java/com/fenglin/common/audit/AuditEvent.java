package com.fenglin.common.audit;

import java.time.Instant;

public record AuditEvent(String eventId, String eventType, String module, String action, String riskLevel,
                         Long tenantId, Long userId, String clientType, String requestId,
                         String resourceType, String resourceId, Instant occurredAt) {
}
