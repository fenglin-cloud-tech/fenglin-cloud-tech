package com.fenglin.tenant.web.dto;

/**
 * HUB-API-204 租户状态变更：action=SUSPEND|RESUME|CLOSE。
 */
public record UpdateTenantStatusRequest(String action, String reason) {
}
