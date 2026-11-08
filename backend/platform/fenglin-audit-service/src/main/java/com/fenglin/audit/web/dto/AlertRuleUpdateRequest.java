package com.fenglin.audit.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 审计告警规则全量替换请求（HUB-API-804，强制幂等：items 即最终规则集）。
 */
public record AlertRuleUpdateRequest(
        @NotEmpty List<@Valid AlertRuleItem> items) {
}
