package com.fenglin.iam.web.dto;

import java.util.List;

public record UserView(
        long userId,
        String username,
        String employeeNo,
        String realName,
        Long primaryOrgId,
        Long primaryPositionId,
        String status,
        List<String> roles) {
}
