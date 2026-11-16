package com.fenglin.common.tenant;

public final class TenantHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String CLIENT_TYPE = "X-Client-Type";
    public static final String CROSS_TENANT = "X-Cross-Tenant";
    /**
     * 平台审计作用域：仅审计服务内部写入链路使用，网关对外部请求一律剥离。
     */
    public static final String AUDIT_SCOPE = "X-Audit-Scope";

    private TenantHeaders() {
    }
}
