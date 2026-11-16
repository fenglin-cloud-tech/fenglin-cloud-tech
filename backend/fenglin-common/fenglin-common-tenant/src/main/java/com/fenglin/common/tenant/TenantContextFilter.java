package com.fenglin.common.tenant;

import com.fenglin.common.core.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 从网关注入的可信头解析租户上下文。服务不直接对公网暴露，头信息由网关在 JWT 校验后覆盖写入。
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Set<String> CLIENT_TYPES = Set.of("ADMIN", "PORTAL", "SYSTEM");
    private static final String PLATFORM_AUDIT_SCOPE = "PLATFORM";

    /**
     * 平台审计作用域开关：只允许审计服务打开。其他服务打开会让内部伪造头能把租户上下文切成 -1。
     */
    private final boolean platformAuditScopeEnabled;

    public TenantContextFilter() {
        this(false);
    }

    public TenantContextFilter(boolean platformAuditScopeEnabled) {
        this.platformAuditScopeEnabled = platformAuditScopeEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = firstNonBlank(request.getHeader(TenantHeaders.REQUEST_ID), UUID.randomUUID().toString());
        RequestContext.setRequestId(requestId);
        response.setHeader(TenantHeaders.REQUEST_ID, requestId);

        Long tenantId;
        Long userId = null;
        String clientType = trimToNull(request.getHeader(TenantHeaders.CLIENT_TYPE));
        boolean platformScope = false;
        try {
            String auditScope = trimToNull(request.getHeader(TenantHeaders.AUDIT_SCOPE));
            if (auditScope != null) {
                if (!platformAuditScopeEnabled || !PLATFORM_AUDIT_SCOPE.equalsIgnoreCase(auditScope)) {
                    throw new IllegalArgumentException("X-Audit-Scope 仅支持 PLATFORM，且仅审计服务内部写入可用");
                }
                if (!String.valueOf(TenantContext.PLATFORM_TENANT_ID)
                        .equals(trimToNull(request.getHeader(TenantHeaders.TENANT_ID)))) {
                    throw new IllegalArgumentException("X-Audit-Scope=PLATFORM 时 X-Tenant-Id 必须为 "
                            + TenantContext.PLATFORM_TENANT_ID);
                }
                platformScope = true;
                tenantId = TenantContext.PLATFORM_TENANT_ID;
            } else {
                tenantId = parsePositiveLong(request.getHeader(TenantHeaders.TENANT_ID), TenantHeaders.TENANT_ID);
                userId = parsePositiveLong(request.getHeader(TenantHeaders.USER_ID), TenantHeaders.USER_ID);
            }
            if (clientType != null && !CLIENT_TYPES.contains(clientType)) {
                throw new IllegalArgumentException("X-Client-Type 仅支持 ADMIN/PORTAL/SYSTEM");
            }
        } catch (IllegalArgumentException ex) {
            reject(response, ex.getMessage());
            return;
        }
        boolean crossTenant = "on".equalsIgnoreCase(trimToNull(request.getHeader(TenantHeaders.CROSS_TENANT)));

        try {
            if (platformScope) {
                TenantContext.setPlatform(requestId);
            } else {
                TenantContext.set(tenantId, userId, clientType, requestId, crossTenant);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }

    private Long parsePositiveLong(String raw, String header) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(header + " 必须为正整数");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(header + " 必须为正整数", ex);
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"FL-COMMON-0400\",\"message\":\"" + message + "\"}");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String first, String fallback) {
        String value = trimToNull(first);
        return value == null ? fallback : value;
    }
}
