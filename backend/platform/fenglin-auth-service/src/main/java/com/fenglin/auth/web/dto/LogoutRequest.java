package com.fenglin.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "id_token_hint 不能为空") String idTokenHint,
        boolean allSessions) {
}
