package com.fenglin.iam.web.dto;

import java.util.List;

public record RoleView(
        long roleId,
        String code,
        String name,
        String roleType,
        String dataScope,
        String status,
        List<String> permissionCodes) {
}
