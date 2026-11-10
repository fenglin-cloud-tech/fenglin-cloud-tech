package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.Size;

/**
 * HUB-API-306 组织更新：仅更新非 null 字段。
 */
public record UpdateOrgRequest(
        @Size(max = 128) String name,
        Integer sort,
        Long leaderId,
        String status,
        Integer version) {
}
