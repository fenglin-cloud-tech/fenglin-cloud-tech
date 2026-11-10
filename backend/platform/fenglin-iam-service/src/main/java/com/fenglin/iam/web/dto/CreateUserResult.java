package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CreateUserResult(
        @JsonSerialize(using = ToStringSerializer.class) long userId,
        String employeeNo,
        String status,
        String initialPassword) {
}
