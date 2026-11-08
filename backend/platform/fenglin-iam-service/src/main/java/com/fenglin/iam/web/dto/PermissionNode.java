package com.fenglin.iam.web.dto;

import java.util.List;

public record PermissionNode(
        String code,
        String name,
        String type,
        String domain,
        String parentCode,
        List<PermissionNode> children) {
}
