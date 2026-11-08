package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * HUB-API-301 用户创建。契约缺口：文档未定义初始口令字段，password 缺省时由平台生成并仅返回一次。
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String realName,
        @NotBlank @Size(max = 64) String username,
        @Size(max = 32) String employeeNo,
        @NotBlank @Size(max = 32) String mobile,
        @Email @Size(max = 128) String email,
        @NotNull Long primaryOrgId,
        Long primaryPositionId,
        List<Long> roleIds,
        String password) {
}
