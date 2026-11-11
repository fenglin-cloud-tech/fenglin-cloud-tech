package com.fenglin.iam.web.dto;

/**
 * 管理员重置口令请求：password 为空时由服务端生成 16 位随机强口令。
 */
public record ResetPasswordRequest(String password) {
}
