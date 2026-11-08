package com.fenglin.audit.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * 审计事件写入（内部契约 /internal/v1/audit/logs）。tenant 由可信头 X-Tenant-Id 决定。
 */
public record RecordAuditRequest(
        String eventId,
        @NotBlank String eventType,
        @NotBlank String module,
        @NotBlank String action,
        String riskLevel,
        Long operatorId,
        String operatorName,
        String targetType,
        String targetId,
        String requestId,
        String traceId,
        String clientType,
        String ip,
        String result,
        JsonNode detail,
        Instant occurredAt) {
}
