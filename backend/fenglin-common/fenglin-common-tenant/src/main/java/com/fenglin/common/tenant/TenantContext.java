package com.fenglin.common.tenant;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;

/**
 * 请求级租户上下文。只允许在服务入口（网关可信头）或测试中写入，业务代码只读。
 */
public final class TenantContext {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId, Long userId, String clientType, String requestId, boolean crossTenant) {
        if (tenantId != null && tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        HOLDER.set(new Snapshot(tenantId, userId, clientType, requestId, crossTenant));
    }

    public static Snapshot get() {
        return HOLDER.get();
    }

    public static Long getTenantId() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.tenantId();
    }

    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new BizException(CommonErrorCode.TENANT_REQUIRED);
        }
        return tenantId;
    }

    public static boolean isCrossTenant() {
        Snapshot snapshot = HOLDER.get();
        return snapshot != null && snapshot.crossTenant();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record Snapshot(Long tenantId, Long userId, String clientType, String requestId, boolean crossTenant) {
    }
}
