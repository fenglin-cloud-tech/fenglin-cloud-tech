package com.fenglin.file.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FastCheckRequest(
        @NotBlank String md5,
        @NotNull Long size,
        String name) {
}
