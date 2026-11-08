package com.fenglin.iam.web.dto;

public record ParamView(String paramKey, String paramValue, String scope, String productCode,
                        String valueType, String remark, String source) {
}
