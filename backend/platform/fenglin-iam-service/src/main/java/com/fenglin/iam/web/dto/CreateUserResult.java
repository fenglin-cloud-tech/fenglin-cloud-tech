package com.fenglin.iam.web.dto;

public record CreateUserResult(long userId, String employeeNo, String status, String initialPassword) {
}
