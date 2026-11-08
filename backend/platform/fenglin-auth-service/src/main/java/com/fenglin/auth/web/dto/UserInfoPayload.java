package com.fenglin.auth.web.dto;

import java.util.List;

public record UserInfoPayload(
        String sub,
        String username,
        String name,
        String employeeNo,
        Long tenantId,
        String tenantCode,
        String clientType,
        List<String> roles,
        List<String> permissions) {
}
