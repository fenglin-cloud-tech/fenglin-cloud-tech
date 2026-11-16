package com.fenglin.auth.web.dto;

/** PLAT-22 忘记密码：租户编码 + 登录名；无论账号是否存在都返回统一语义。 */
public record ForgotPasswordRequest(String tenantCode, String username) {
}
