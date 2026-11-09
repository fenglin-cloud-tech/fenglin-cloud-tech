package com.fenglin.legal.support;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.legal.LegalErrorCode;

import java.util.UUID;

/**
 * 云法请求上下文与分页工具。
 */
public final class LegalContext {

    private LegalContext() {
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

    public static int offset(Integer page, Integer size) {
        return (page(page) - 1) * size(size);
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

    public static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
        return value.strip();
    }

    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static long requireLong(Object value) {
        if (value == null) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
    }

    public static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
    }

    public static String string(Object value) {
        return value == null ? null : text(String.valueOf(value));
    }
}
