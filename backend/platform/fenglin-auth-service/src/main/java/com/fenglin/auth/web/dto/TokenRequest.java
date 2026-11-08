package com.fenglin.auth.web.dto;

public record TokenRequest(
        String grantType,
        String refreshToken,
        String clientId,
        String clientType) {
}
