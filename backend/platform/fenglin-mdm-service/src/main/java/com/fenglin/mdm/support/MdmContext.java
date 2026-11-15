package com.fenglin.mdm.support;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;

/**
 * MDM 请求上下文与分页工具。
 */
public final class MdmContext {

    private MdmContext() {
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

    public static int size(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.min(Math.max(size, 1), 200);
    }

    public static int offset(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        return (p - 1) * size(size);
    }
}
