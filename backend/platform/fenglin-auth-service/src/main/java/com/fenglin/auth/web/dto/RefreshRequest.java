package com.fenglin.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "refresh_token 不能为空") String refreshToken) {
}
