package com.fenglin.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeRequest(
        @NotBlank(message = "token 不能为空") String token,
        String tokenTypeHint) {
}
