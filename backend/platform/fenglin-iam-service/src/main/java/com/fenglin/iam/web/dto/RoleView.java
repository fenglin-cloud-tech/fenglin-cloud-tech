package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record RoleView(
        @JsonSerialize(using = ToStringSerializer.class) long roleId,
        String code,
        String name,
        String roleType,
        String dataScope,
        String status,
        List<String> permissionCodes) {
}
