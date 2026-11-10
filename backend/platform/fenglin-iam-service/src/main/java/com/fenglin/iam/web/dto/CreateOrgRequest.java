package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HUB-API-306 组织创建。
 */
public record CreateOrgRequest(
        Long parentId,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 16) String orgType,
        Integer sort,
        Long leaderId) {
}
