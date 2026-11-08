package com.fenglin.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.ai.AiErrorCode;
import com.fenglin.common.core.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 模型网关：按 1301 配置的 endpoint 以 OpenAI 兼容 SSE 协议流式调用。
 * 契约缺口：1303 未约定模型选择规则，按租户启用模型 routing_weight 选取。
 */
@Component
public class ModelGatewayClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutMs;

    public ModelGatewayClient(ObjectMapper objectMapper,
                              @Value("${fenglin.ai.model-timeout-ms:15000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))
                .build();
    }

    /**
     * 逐段回调增量文本；无任何增量视为降级。
     */
    public void stream(String endpoint, String apiKey, String prompt, Double temperature,
                       Consumer<String> onDelta) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "default");
        body.put("messages", java.util.List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", temperature == null ? 0.3 : temperature);
        body.put("stream", true);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception ex) {
            throw new BizException(AiErrorCode.MODEL_DEGRADED, "模型地址不可用: " + endpoint);
        }
        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new BizException(AiErrorCode.MODEL_DEGRADED,
                        "模型服务返回 " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleLine(line, onDelta));
            }
        } catch (BizException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(AiErrorCode.MODEL_DEGRADED, "模型调用被中断");
        } catch (Exception ex) {
            throw new BizException(AiErrorCode.MODEL_DEGRADED,
                    "模型服务不可用: " + ex.getMessage());
        }
    }

    private void handleLine(String rawLine, Consumer<String> onDelta) {
        if (rawLine == null) {
            return;
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("event:")) {
            return;
        }
        String payload = line.startsWith("data:") ? line.substring(5).trim() : line;
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            String content = textOf(node);
            if (content != null && !content.isEmpty()) {
                onDelta.accept(content);
            }
        } catch (Exception ignored) {
            // 非 JSON 行（心跳/注释）直接跳过
        }
    }

    private String textOf(JsonNode node) {
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta").path("content");
            if (delta.isTextual()) {
                return delta.asText();
            }
            JsonNode message = choice.path("message").path("content");
            if (message.isTextual()) {
                return message.asText();
            }
        }
        JsonNode content = node.path("content");
        return content.isTextual() ? content.asText() : null;
    }
}
