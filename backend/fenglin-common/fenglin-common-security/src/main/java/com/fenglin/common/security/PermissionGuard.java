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
        Set<String> granted = permissionsHeader == null || permissionsHeader.isBlank()
                ? Set.of()
                : Arrays.stream(permissionsHeader.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
        if (!granted.contains(requiredPermission) && !granted.contains("*")) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "缺少权限点: " + requiredPermission);
        }
    }
}
