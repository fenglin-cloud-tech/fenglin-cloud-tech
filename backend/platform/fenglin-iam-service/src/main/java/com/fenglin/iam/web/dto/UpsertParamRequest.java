package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.Size;

public record UpsertParamRequest(
        String value,
        String scope,
        @Size(max = 64) String productCode,
        String valueType,
        @Size(max = 256) String remark) {
}
