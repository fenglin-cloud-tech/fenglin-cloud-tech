package com.fenglin.auth.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * OPEN-06：平台跨租户只读切换。一期仅允许 ACTIVE 租户，reason 仅入审计。
 */
public record TenantSwitchRequest(
        @NotNull Long targetTenantId,
        String reason) {
}
