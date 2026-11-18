package com.fenglin.collab.support;

import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;

import java.util.function.Supplier;

/**
 * 公开图片/分享路径的租户作用域：租户信息只能来自已验签的分享令牌或图片令牌，
 * 不信任客户端裸传的 X-Tenant-Id。执行结束后恢复原上下文，避免串租户。
 */
public final class TenantScope {

    private TenantScope() {
    }

    public static <T> T call(long tenantId, Supplier<T> action) {
        TenantContext.Snapshot previous = TenantContext.get();
        try {
            TenantContext.set(tenantId, null, "SYSTEM", RequestContext.requestId(), false);
            return action.get();
        } finally {
            TenantContext.restore(previous);
        }
    }

    /**
     * 解析 v2 分享令牌中的租户：格式 v2-{tenantId}-{uuid}；旧 UUID 令牌返回 null（需已登录上下文）。
     */
    public static Long tenantOfLinkToken(String token) {
        if (token == null || !token.startsWith("v2-")) {
            return null;
        }
        String[] parts = token.split("-", 3);
        if (parts.length < 3 || parts[1].isBlank()) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(parts[1]);
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
