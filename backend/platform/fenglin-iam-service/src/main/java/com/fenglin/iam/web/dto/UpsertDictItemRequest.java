package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertDictItemRequest(
        @NotBlank @Size(max = 128) String itemLabel,
        Integer sort) {
}
