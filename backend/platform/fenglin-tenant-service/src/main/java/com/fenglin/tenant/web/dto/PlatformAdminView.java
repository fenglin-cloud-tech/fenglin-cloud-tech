package com.fenglin.tenant.web.dto;

/**
 * 平台管理员状态视图：platform_admin=true 表示该用户当前持有 PLATFORM_ADMIN 角色。
 */
public record PlatformAdminView(long userId, boolean platformAdmin, Long roleId) {
}
