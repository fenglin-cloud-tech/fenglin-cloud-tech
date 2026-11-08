package com.fenglin.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 口令登录请求。
 * 说明：《接口文档》4.1 只定义了 OAuth2.1 的 authorize/token 端点，未定义首方口令登录接口，
 * 此接口作为 P1 契约缺口补充（汇报中已标注），字段命名沿用文档 snake_case 风格。
 */
public record LoginRequest(
        @NotBlank(message = "租户编码不能为空") String tenantCode,
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "口令不能为空") String password,
        @Size(max = 16) String clientType,
        String clientId,
        String deviceId) {
}
