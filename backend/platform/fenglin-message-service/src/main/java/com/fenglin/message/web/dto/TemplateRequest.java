package com.fenglin.message.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record TemplateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String channel,
        String titleTpl,
        String contentTpl,
        List<String> variables,
        String locale,
        String status) {
}
