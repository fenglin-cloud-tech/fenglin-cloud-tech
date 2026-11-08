package com.fenglin.audit.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 审计告警规则项（HUB-API-804）。
 */
public record AlertRuleItem(
        @NotBlank String code,
        @NotBlank String eventType,
        @Min(1) Integer threshold,
        @NotBlank String receiverRole,
        List<String> channels) {
}
