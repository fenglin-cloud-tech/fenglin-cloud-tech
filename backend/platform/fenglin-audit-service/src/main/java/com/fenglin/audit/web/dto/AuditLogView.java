package com.fenglin.audit.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record AuditLogView(
        long logId,
        long tenantId,
        String logNo,
        String eventType,
        String module,
        String action,
        Long operatorId,
        String operatorName,
        String targetType,
        String targetId,
        String requestId,
        String traceId,
        String clientType,
        String result,
        String riskLevel,
        JsonNode detail,
        Instant occurredAt) {
}
