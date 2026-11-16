package com.fenglin.auth.web.dto;

/** PLAT-22 一次性令牌重置口令。 */
public record ResetPasswordRequest(String token, String newPassword) {
}
