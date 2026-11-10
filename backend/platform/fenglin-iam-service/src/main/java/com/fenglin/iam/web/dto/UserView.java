package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record UserView(
        @JsonSerialize(using = ToStringSerializer.class) long userId,
        String username,
        String employeeNo,
        String realName,
        @JsonSerialize(using = ToStringSerializer.class) Long primaryOrgId,
        @JsonSerialize(using = ToStringSerializer.class) Long primaryPositionId,
        String status,
        List<String> roles,
        String nickname,
        String mobileMasked,
        String primaryOrgName,
        @JsonSerialize(using = ToStringSerializer.class) Long employeeId) {
}
