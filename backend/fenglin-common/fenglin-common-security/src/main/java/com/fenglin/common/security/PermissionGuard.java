package com.fenglin.common.security;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口级鉴权：网关校验 JWT 后把权限点以 X-Permissions 传入，下游服务对写接口做二次校验。
 */
public final class PermissionGuard {

    private PermissionGuard() {
    }

    public static void require(String permissionsHeader, String requiredPermission) {
        if (requiredPermission == null || requiredPermission.isBlank()) {
            return;
        }
        Set<String> granted = granted(permissionsHeader);
        if (!granted.contains(requiredPermission) && !granted.contains("*")) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "缺少权限点: " + requiredPermission);
        }
    }

    /**
     * 多个权限点任一满足即可；用于"管理视角"与"本人视角"二选一的读接口。
     */
    public static void requireAny(String permissionsHeader, String... requiredPermissions) {
        Set<String> granted = granted(permissionsHeader);
        for (String required : requiredPermissions) {
            if (required == null || required.isBlank()) {
                continue;
            }
            if (granted.contains(required)) {
                return;
            }
        }
        if (granted.contains("*")) {
            return;
        }
        throw new BizException(CommonErrorCode.FORBIDDEN,
                "缺少权限点: " + String.join(" 或 ", requiredPermissions));
    }

    public static boolean has(String permissionsHeader, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        Set<String> granted = granted(permissionsHeader);
        return granted.contains("*") || granted.contains(permission);
    }

    private static Set<String> granted(String permissionsHeader) {
        return permissionsHeader == null || permissionsHeader.isBlank()
                ? Set.of()
                : Arrays.stream(permissionsHeader.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
    }
}
