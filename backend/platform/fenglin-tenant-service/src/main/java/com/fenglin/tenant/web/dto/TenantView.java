package com.fenglin.tenant.web.dto;

import java.time.Instant;

public record TenantView(
        long id,
        String code,
        String name,
        String status,
        String contactName,
        Instant expireAt,
        Instant createdAt) {
}
