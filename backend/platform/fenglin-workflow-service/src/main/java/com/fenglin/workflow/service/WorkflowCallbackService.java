package com.fenglin.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.workflow.config.WorkflowCallbackProperties;
import com.fenglin.workflow.mapper.WorkflowCallbackLogMapper;
import com.fenglin.workflow.mapper.WorkflowCallbackMapper;
import com.fenglin.workflow.mapper.WorkflowInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowCallbackService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCallbackService.class);

    private final WorkflowCallbackMapper callbackMapper;
    private final WorkflowCallbackLogMapper callbackLogMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowAuditor auditor;
    private final WorkflowCallbackProperties properties;

    public WorkflowCallbackService(WorkflowCallbackMapper callbackMapper,
                                   WorkflowCallbackLogMapper callbackLogMapper,
                                   WorkflowInstanceMapper instanceMapper,
                                   ObjectMapper objectMapper,
                                   WorkflowAuditor auditor,
                                   WorkflowCallbackProperties properties) {
        this.callbackMapper = callbackMapper;
        this.callbackLogMapper = callbackLogMapper;
        this.instanceMapper = instanceMapper;
        this.objectMapper = objectMapper;
        this.auditor = auditor;
        this.properties = properties;
    }

    @Transactional
    public void dispatch(long tenantId, long instanceId, String action) {
        Map<String, Object> instance = instanceMapper.findById(tenantId, instanceId);
        if (instance == null) {
            return;
        }
        String processKey = (String) instance.get("process_key");
        Map<String, Object> callback = callbackMapper.findEnabled(tenantId, processKey);
        if (callback == null) {
            return;
        }
        String events = (String) callback.get("events");
        if (events != null && !events.isBlank() && !events.contains(action)) {
            return;
        }
        String callbackUrl = (String) callback.get("callback_url");
        long logId = IdWorker.getId();
        int inserted = callbackLogMapper.insertIfAbsent(logId, tenantId, instanceId, "process", action,
                callbackUrl, null);
        if (inserted == 0) {
            return;
        }
        Long persistedId = callbackLogMapper.findId(tenantId, instanceId, "process", action);
        if (persistedId == null) {
            return;
        }

        int maxRetry = intValue(callback.get("max_retry"), properties.maxRetry());
        long timeoutMs = longValue(callback.get("timeout_ms"), properties.readTimeout().toMillis());
        String payload = payload(tenantId, instance, action);
        String idempotencyKey = tenantId + ":" + instanceId + ":process:" + action;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        int attempts = 0;
        Integer lastStatus = null;
        String lastError = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(callbackUrl))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .header("X-Fenglin-Event", action)
                        .header("Idempotency-Key", idempotencyKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                lastStatus = response.statusCode();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    callbackLogMapper.markResult(tenantId, persistedId, "SENT", attempts, lastStatus, null);
                    auditor.publish(tenantId, "WF_CALLBACK_DELIVERED", "WORKFLOW_CALLBACK",
                            idempotencyKey, "LOW");
                    return;
                }
                lastError = "HTTP " + response.statusCode();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                lastError = "interrupted";
                break;
            } catch (Exception ex) {
                lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            if (attempt < maxRetry) {
                if (!sleep(properties.retryBackoff().multipliedBy(1L << attempt))) {
                    lastError = "interrupted";
                    break;
                }
            }
        }
        callbackLogMapper.markResult(tenantId, persistedId, "FAILED", attempts, lastStatus,
                truncate(lastError));
        auditor.publish(tenantId, "WF_CALLBACK_FAILED", "WORKFLOW_CALLBACK", idempotencyKey, "HIGH");
        log.warn("workflow callback failed tenant={} instance={} action={} attempts={} error={}",
                tenantId, instanceId, action, attempts, lastError);
    }

    private String payload(long tenantId, Map<String, Object> instance, String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", action);
        payload.put("tenant_id", tenantId);
        payload.put("instance_id", instance.get("id"));
        payload.put("instance_no", instance.get("instance_no"));
        payload.put("process_key", instance.get("process_key"));
        payload.put("def_version", instance.get("def_version"));
        payload.put("business_key", instance.get("business_key"));
        payload.put("business_type", instance.get("business_type"));
        payload.put("title", instance.get("title"));
        payload.put("status", instance.get("status"));
        payload.put("result", instance.get("result"));
        payload.put("occurred_at", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"event\":\"" + action + "\"}";
        }
    }

    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private int intValue(Object value, int fallback) {
        return value == null ? fallback : ((Number) value).intValue();
    }

    private long longValue(Object value, long fallback) {
        return value == null ? fallback : ((Number) value).longValue();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 480 ? value : value.substring(0, 480);
    }
}
