package com.fenglin.common.security;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;

public final class ClientTypeGuard {

    private ClientTypeGuard() {
    }

    public static void require(ClientType expected) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || !expected.name().equals(snapshot.clientType())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "调用端类型不允许访问该接口");
        }
    }
}
