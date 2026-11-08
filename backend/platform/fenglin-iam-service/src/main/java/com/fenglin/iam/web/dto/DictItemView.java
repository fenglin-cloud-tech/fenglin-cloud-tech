package com.fenglin.iam.web.dto;

public record DictItemView(String itemValue, String itemLabel, int sort, int status, boolean overridden) {
}
