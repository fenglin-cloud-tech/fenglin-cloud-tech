package com.fenglin.collab.support;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;

/**
 * 云协请求上下文与分页参数规范化。
 */
public final class CollabContext {

    private CollabContext() {
    }

    public static long tenantId() {
        return TenantContext.requireTenantId();
    }

    public static long userId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.userId() == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        return snapshot.userId();
    }

    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int size(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.min(Math.max(size, 1), 200);
    }
}
