package com.fenglin.openapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.service.OpenApiCallLogService;
import com.fenglin.openapi.service.OpenApiQuotaService;
import com.fenglin.openapi.service.OpenApiSignatureService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 开放 API 统一验签网关：作用于 /openapi/**（开发者门户 /openapi/portal/** 除外）。
 * 处理顺序：时间戳与 nonce 防重放 → HMAC 验签 → QPS/日配额 → 透传业务 → 记录调用日志。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpenApiSignatureFilter extends OncePerRequestFilter {

    public static final String ATTR_APP_ID = "fenglin.openapi.app-id";
    public static final String ATTR_TENANT_ID = "fenglin.openapi.tenant-id";

    private static final Logger log = LoggerFactory.getLogger(OpenApiSignatureFilter.class);

    private final OpenApiSignatureService signatureService;
    private final OpenApiQuotaService quotaService;
    private final OpenApiCallLogService callLogService;
    private final ObjectMapper objectMapper;

    public OpenApiSignatureFilter(OpenApiSignatureService signatureService,
                                  OpenApiQuotaService quotaService,
                                  OpenApiCallLogService callLogService,
                                  ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.quotaService = quotaService;
        this.callLogService = callLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/openapi/") || uri.startsWith("/openapi/portal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        TenantContext.Snapshot previous = TenantContext.get();
        String previousRequestId = RequestContext.requestId();
        RequestContext.setRequestId(requestId);

        long start = System.currentTimeMillis();
        byte[] body = request.getInputStream().readAllBytes();
        OpenApiSignatureService.VerifiedApp app;
        try {
            app = signatureService.verify(
                    request.getHeader("X-App-Key"),
                    request.getHeader("X-Timestamp"),
                    request.getHeader("X-Nonce"),
                    request.getHeader("X-Signature"),
                    request.getMethod(),
                    request.getRequestURI(),
                    body);
        } catch (BizException ex) {
            writeError(response, ex, requestId);
            restore(previous, previousRequestId);
            return;
        }
        try {
            TenantContext.set(app.tenantId(), null, "PORTAL", requestId, false);
            quotaService.checkAndConsume(app.tenantId(), app.appId(), request.getRequestURI());
        } catch (BizException ex) {
            writeError(response, ex, requestId);
            recordCall(app, request, response, requestId, start, previous, previousRequestId);
            return;
        }

        request.setAttribute(ATTR_APP_ID, app.appId());
        request.setAttribute(ATTR_TENANT_ID, app.tenantId());
        TenantContext.set(app.tenantId(), null, "PORTAL", requestId, false);
        HttpServletRequest downstream = new OpenApiRequestWrapper(new CachedBodyRequest(request, body), app.tenantId());
        try {
            chain.doFilter(downstream, response);
        } finally {
            recordCall(app, request, response, requestId, start, previous, previousRequestId);
        }
    }

    private void recordCall(OpenApiSignatureService.VerifiedApp app, HttpServletRequest request,
                            HttpServletResponse response, String requestId, long start,
                            TenantContext.Snapshot previous, String previousRequestId) {
        try {
            TenantContext.set(app.tenantId(), null, "PORTAL", requestId, false);
            RequestContext.setRequestId(requestId);
            callLogService.record(app.tenantId(), app.appId(), app.appKey(), request.getRequestURI(),
                    request.getMethod(), response.getStatus(), (int) (System.currentTimeMillis() - start),
                    requestId);
        } catch (RuntimeException ex) {
            log.error("开放 API 调用日志落库失败：appId={} path={}", app.appId(), request.getRequestURI(), ex);
        } finally {
            restore(previous, previousRequestId);
        }
    }

    private void writeError(HttpServletResponse response, BizException ex, String requestId)
            throws IOException {
        RequestContext.setRequestId(requestId);
        response.setStatus(ex.getErrorCode().httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    private void restore(TenantContext.Snapshot previous, String previousRequestId) {
        if (previous == null) {
            TenantContext.clear();
        } else {
            TenantContext.set(previous.tenantId(), previous.userId(), previous.clientType(),
                    previous.requestId(), previous.crossTenant());
        }
        if (previousRequestId == null) {
            RequestContext.clear();
        } else {
            RequestContext.setRequestId(previousRequestId);
        }
    }

    /**
     * 下游注入可信租户头，并移除外部可伪造的权限头。
     */
    private static final class OpenApiRequestWrapper extends HttpServletRequestWrapper {

        private final long tenantId;

        private OpenApiRequestWrapper(HttpServletRequest request, long tenantId) {
            super(request);
            this.tenantId = tenantId;
        }

        @Override
        public String getHeader(String name) {
            if ("X-Tenant-Id".equalsIgnoreCase(name)) {
                return String.valueOf(tenantId);
            }
            if ("X-Client-Type".equalsIgnoreCase(name)) {
                return "PORTAL";
            }
            if ("X-Permissions".equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }
    }
}
