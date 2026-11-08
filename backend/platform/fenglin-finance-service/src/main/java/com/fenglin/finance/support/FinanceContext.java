package com.fenglin.finance.support;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 云财请求上下文、金额与业务编号工具。
 */
public final class FinanceContext {

    private FinanceContext() {
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

    public static String userName() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.userId() == null) {
            return null;
        }
        return String.valueOf(snapshot.userId());
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

    public static int offset(Integer page, Integer size) {
        return (page(page) - 1) * size(size);
    }

    /** 金额统一保留 4 位小数，禁止负零。 */
    public static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal scaled = value.setScale(4, RoundingMode.HALF_UP);
        return scaled.signum() == 0 ? BigDecimal.ZERO.setScale(4) : scaled;
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(4);
    }

    public static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    public static String idempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.length() > 64 ? header.substring(0, 64) : header;
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
