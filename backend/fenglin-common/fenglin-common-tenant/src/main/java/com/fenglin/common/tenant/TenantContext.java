package com.fenglin.common.tenant;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;

/**
 * 请求级租户上下文。只允许在服务入口（网关可信头）或测试中写入，业务代码只读。
 */
public final class TenantContext {

    /**
     * 平台级审计保留租户：只承载“无租户归属”的平台事件，不对任何真实租户可见。
     * 取值必须为负数，避免与雪花 ID / 业务租户号冲突。
     */
    public static final long PLATFORM_TENANT_ID = -1L;

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId, Long userId, String clientType, String requestId, boolean crossTenant) {
        if (tenantId != null && tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        HOLDER.set(new Snapshot(tenantId, userId, clientType, requestId, crossTenant));
    }

    /**
     * 平台审计作用域：仅由审计服务内部写入/平台运维只读入口设置，业务代码不得调用。
     */
    public static void setPlatform(String requestId) {
        HOLDER.set(new Snapshot(PLATFORM_TENANT_ID, null, "SYSTEM", requestId, false));
    }

    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(snapshot);
        }
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
