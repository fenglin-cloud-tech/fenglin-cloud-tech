package com.fenglin.intelligence.client;

import com.fenglin.common.core.BizException;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.intelligence.IntelligenceErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台 AI 中台 HTTP 客户端：
 * - 对话/生成走 /api/hub/v1/ai/copilot/messages:stream（SSE）；
 * - 能力授权、计量、双模式校验走 /api/hub/v1/admin/ai/*（hub_ai_* 唯一源）。
 */
@Component
public class HttpAiGatewayClient implements AiGatewayClient {

    private static final String RAG_PERMISSION = "ai:chat:rag";
    private static final String GRANT_PERMISSION = "tenant:ai:grant:manage";

    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpAiGatewayClient(@Value("${fenglin.intelligence.ai-platform-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public ChatResult chat(String content, Double temperature, Integer secretLevel) {
        TenantContext.Snapshot snapshot = requireSnapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("use_kb", false);
        body.put("temperature", temperature);
        try {
            // text/event-stream 未声明 charset 时按 ISO-8859-1 解码会破坏中文，统一按 UTF-8 还原
            byte[] raw = restClient.post()
                    .uri(baseUrl + "/api/hub/v1/ai/copilot/messages:stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Client-Type", snapshot.clientType() == null ? "PORTAL" : snapshot.clientType())
                    .header("X-Permissions", RAG_PERMISSION)
                    .header("X-Caller-Product", "intelligence")
                    .header("X-Secret-Level", String.valueOf(secretLevel == null ? 1 : secretLevel))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            String sse = raw == null ? null : new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            return parseSse(sse);
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
    }

    @Override
    public ChatResult stream(String content, Double temperature, Integer secretLevel,
                             java.util.function.Consumer<String> onDelta) {
        TenantContext.Snapshot snapshot = requireSnapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("use_kb", false);
        body.put("temperature", temperature);
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(baseUrl + "/api/hub/v1/ai/copilot/messages:stream"))
                    .timeout(java.time.Duration.ofSeconds(180))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "text/event-stream")
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Client-Type", snapshot.clientType() == null ? "PORTAL" : snapshot.clientType())
                    .header("X-Permissions", RAG_PERMISSION)
                    .header("X-Caller-Product", "intelligence")
                    .header("X-Secret-Level", String.valueOf(secretLevel == null ? 1 : secretLevel))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            java.net.http.HttpResponse<java.io.InputStream> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
            }
            StringBuilder answer = new StringBuilder();
            int[] tokens = new int[3];
            boolean[] degraded = new boolean[1];
            String[] event = new String[1];
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = reader.readLine()) != null) {
                    String line = raw.strip();
                    if (line.startsWith("event:")) {
                        event[0] = line.substring(6).strip();
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5).strip();
                        if (data.isEmpty()) {
                            continue;
                        }
                        try {
                            Map<?, ?> payload = objectMapper.readValue(data, Map.class);
                            if ("delta".equals(event[0])) {
                                Object delta = payload.get("content");
                                if (delta != null) {
                                    answer.append(delta);
                                    if (onDelta != null) {
                                        onDelta.accept(String.valueOf(delta));
                                    }
                                }
                            } else if ("done".equals(event[0])) {
                                tokens[0] = intOf(payload.get("tokenIn"));
                                tokens[1] = intOf(payload.get("tokenOut"));
                                tokens[2] = intOf(payload.get("ttftMs"));
                                degraded[0] = Boolean.TRUE.equals(payload.get("degraded"));
                            }
                        } catch (Exception ignored) {
                            // 非 JSON 事件帧忽略
                        }
                    }
                }
            }
            if (answer.isEmpty()) {
                throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
            }
            return new ChatResult(answer.toString(), tokens[0], tokens[1], tokens[2], degraded[0],
                    new ArrayList<>());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> grants(String hostProduct) {
        TenantContext.Snapshot snapshot = requireSnapshot();
        try {
            Map<String, Object> response = restClient.get()
                    .uri(baseUrl + "/api/hub/v1/admin/ai/capability-grants?hostProduct={hostProduct}",
                            hostProduct == null ? "" : hostProduct)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Permissions", GRANT_PERMISSION)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> metering(String mode, String hostProduct, String from, String to) {
        TenantContext.Snapshot snapshot = requireSnapshot();
        try {
            Map<String, Object> response = restClient.get()
                    .uri(baseUrl + "/api/hub/v1/admin/ai/metering?mode={mode}&hostProduct={hostProduct}"
                                    + "&from={from}&to={to}",
                            nullSafe(mode), nullSafe(hostProduct), nullSafe(from), nullSafe(to))
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateMode() {
        TenantContext.Snapshot snapshot = requireSnapshot();
        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/api/hub/v1/admin/ai/mode:validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
    }

    private ChatResult parseSse(String sse) {
        StringBuilder answer = new StringBuilder();
        int tokenIn = 0;
        int tokenOut = 0;
        long ttft = 0;
        boolean degraded = false;
        if (sse != null) {
            String event = null;
            for (String raw : sse.split("\\r?\\n")) {
                String line = raw.strip();
                if (line.startsWith("event:")) {
                    event = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).strip();
                    if (data.isEmpty()) {
                        continue;
                    }
                    try {
                        Map<?, ?> payload = objectMapper.readValue(data, Map.class);
                        if ("delta".equals(event)) {
                            Object delta = payload.get("content");
                            if (delta != null) {
                                answer.append(delta);
                            }
                        } else if ("done".equals(event)) {
                            tokenIn = intOf(payload.get("tokenIn"));
                            tokenOut = intOf(payload.get("tokenOut"));
                            ttft = intOf(payload.get("ttftMs"));
                            degraded = Boolean.TRUE.equals(payload.get("degraded"));
                        }
                    } catch (Exception ignored) {
                        // 非 JSON 事件帧忽略
                    }
                }
            }
        }
        if (answer.isEmpty()) {
            throw new BizException(IntelligenceErrorCode.DEPENDENCY_DEGRADED);
        }
        return new ChatResult(answer.toString(), tokenIn, tokenOut, ttft, degraded, new ArrayList<>());
    }

    private int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private TenantContext.Snapshot requireSnapshot() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null) {
            throw new BizException(IntelligenceErrorCode.NO_PERMISSION);
        }
        return snapshot;
    }
}
