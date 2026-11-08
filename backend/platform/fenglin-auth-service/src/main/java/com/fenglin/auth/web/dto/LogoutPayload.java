package com.fenglin.auth.web.dto;

import java.util.List;

public record LogoutPayload(List<ClientSession> updated) {

    public record ClientSession(String clientId, String status) {
    }
}
