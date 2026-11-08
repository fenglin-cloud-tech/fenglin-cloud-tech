package com.fenglin.iam.web.dto;

public record MenuView(
        String code,
        String name,
        String path,
        String component,
        String parentCode,
        String icon,
        int sort,
        String permissionCode) {
}
