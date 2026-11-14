package com.fenglin.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件上报实现：把 AuditEvent 投递到审计服务 /internal/v1/audit/logs。
 * 审计失败不阻断业务（记 WARN），事件 ID 用于幂等与链路追踪。
 */
public class HttpAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(HttpAuditSink.class);

    private final RestClient restClient;
    private final String endpoint;

    public HttpAuditSink(String endpoint) {
        this.endpoint = endpoint;
        this.restClient = RestClient.create();
    }

    @Override
    public void publish(AuditEvent event) {
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
        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", event.tenantId() == null ? "0" : String.valueOf(event.tenantId()))
                    .header("X-Request-Id", event.requestId() == null ? event.eventId() : event.requestId())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("审计事件上报失败 event={} tenant={} error={}",
                    event.eventType(), event.tenantId(), e.getMessage());
        }
    }
}
