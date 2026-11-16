package com.fenglin.gateway.security;

import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OPEN-06 一期：跨租户访问审计直写审计服务。
 *
 * <p>审计写在放行之前；审计失败时由调用方 fail-close（跨租户请求 503），
 * 普通租户请求不经过本类，不受影响。审计记录 tenant_id=目标租户，绝不写 0。</p>
 */
public class PlatformAccessAuditor {

    private final WebClient webClient;
    private final Duration timeout;

    public PlatformAccessAuditor(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    public Mono<Void> record(long targetTenantId, long operatorId, String operatorName,
                             long homeTenantId, String switchId, String decision,
                             ServerHttpRequest request, String requestId) {
        String path = request.getURI().getPath();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("home_tenant_id", homeTenantId);
        detail.put("target_tenant_id", targetTenantId);
        detail.put("switch_id", switchId);
        detail.put("operator_id", operatorId);
        detail.put("method", request.getMethod().name());
        detail.put("path", path);
        detail.put("query", request.getURI().getQuery());
        detail.put("decision", decision);
        detail.put("scope", "PLATFORM_GOVERNANCE_READ_ONLY");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_type", "PLATFORM_CROSS_TENANT_ACCESS");
        body.put("module", "platform");
        body.put("action", request.getMethod().name());
        body.put("risk_level", "HIGH");
        body.put("operator_id", operatorId);
        body.put("operator_name", operatorName);
        body.put("target_type", "HTTP");
        body.put("target_id", truncate(path, 128));
        body.put("request_id", requestId);
        body.put("client_type", "ADMIN");
        body.put("result", "ALLOW".equals(decision) ? "SUCCESS" : "FAILED");
        body.put("detail", detail);
        body.put("occurred_at", Instant.now().toString());

        return webClient.post()
                .uri("/internal/v1/audit/logs")
                .header("X-Tenant-Id", String.valueOf(targetTenantId))
                .header("X-User-Id", String.valueOf(operatorId))
                .header("X-Client-Type", "ADMIN")
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .then();
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
