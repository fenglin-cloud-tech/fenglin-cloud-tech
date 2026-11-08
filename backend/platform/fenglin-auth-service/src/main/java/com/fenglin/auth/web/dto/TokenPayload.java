package com.fenglin.auth.web.dto;

public record TokenPayload(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        String idToken,
        String scope,
        String sessionId,
        boolean pwdResetRequired) {
}
