package com.fenglin.tenant.web.dto;

public record CreateTenantResult(long tenantId, String code, String status, String initialPassword) {
}
