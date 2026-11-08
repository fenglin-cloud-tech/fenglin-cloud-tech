package com.fenglin.message.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record SendMessageRequest(
        @NotBlank String templateCode,
        @NotEmpty List<String> channels,
        @NotEmpty List<Long> receivers,
        Map<String, String> params,
        String bizType,
        String bizId) {
}
