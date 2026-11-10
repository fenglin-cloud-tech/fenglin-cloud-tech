package com.fenglin.auth.web.dto;

import java.util.List;

/**
 * PLAT-10 网关内部契约：按用户返回权威权限版本与权限点。
 * 账号非 ACTIVE 时 roles/permissions 返回空，由下游按空头产生 403。
 */
public record PrincipalPermissionPayload(
        Long userId,
        long permissionVersion,
        List<String> roles,
        List<String> permissions) {
}
