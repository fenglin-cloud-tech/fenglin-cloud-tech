package com.fenglin.tenant.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 平台管理员授予：把当前租户下的指定用户绑定到 PLATFORM_ADMIN 角色。
 */
public record GrantPlatformAdminRequest(@NotNull @Positive Long userId) {
}
