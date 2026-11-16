package com.fenglin.common.audit;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件上报实现：把 AuditEvent 投递到审计服务 /internal/v1/audit/logs。
 *
 * <p>平台级事件（无租户上下文）写入保留租户 -1，并携带 {@code X-Audit-Scope: PLATFORM}，
 * 审计服务只对平台运维身份开放读取。平台级事件与 HIGH 风险事件采用 fail-closed：
 * 审计写不进去时抛 {@link BizException}（HTTP 503），业务操作不落库；其余事件降级为
 * WARN + 本地结构化日志，不阻断业务。</p>
 */
public class HttpAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(HttpAuditSink.class);

    private static final String AUDIT_SCOPE_HEADER = "X-Audit-Scope";
    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String HIGH_RISK = "HIGH";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String endpoint;
    private final AuditSink fallback = new Slf4jAuditSink();

    public HttpAuditSink(String endpoint) {
        this.endpoint = endpoint;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void publish(AuditEvent event) {
        boolean platformScoped = event.tenantId() == null;
        boolean failClosed = platformScoped || HIGH_RISK.equalsIgnoreCase(event.riskLevel());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_id", event.eventId());
        body.put("event_type", event.eventType());
        body.put("module", event.module());
        body.put("action", event.action());
        body.put("risk_level", event.riskLevel());
        body.put("operator_id", event.userId());
        body.put("client_type", event.clientType());
        body.put("request_id", event.requestId());
        body.put("target_type", event.resourceType());
        body.put("target_id", event.resourceId());
        body.put("result", "SUCCESS");
        if (event.detail() != null && !event.detail().isEmpty()) {
            body.put("detail", event.detail());
        }
        Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        body.put("occurred_at", occurredAt.toString());

        long tenantId = platformScoped ? AuditEvent.PLATFORM_TENANT_ID : event.tenantId();
        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-Request-Id", event.requestId() == null ? event.eventId() : event.requestId())
                    .headers(headers -> {
                        if (platformScoped) {
                            headers.set(AUDIT_SCOPE_HEADER, PLATFORM_SCOPE);
                        } else {
                            headers.remove(AUDIT_SCOPE_HEADER);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            if (failClosed) {
                log.error("关键审计事件上报失败，已阻断业务 event={} tenant={} risk={} error={}",
                        event.eventType(), event.tenantId(), event.riskLevel(), e.toString());
                throw new BizException(CommonErrorCode.AUDIT_UNAVAILABLE,
                        "审计事件写入失败：" + event.eventType(), e);
            }
            log.warn("审计事件上报失败，已降级为本地日志 event={} tenant={} error={}",
                    event.eventType(), event.tenantId(), e.getMessage());
            fallback.publish(event);
        }
    }
}
