package com.fenglin.collab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组织架构只读客户端：按需调用云才 {@code GET /api/talent/v1/orgs/tree}。
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>超时：连接 800ms、整体 1500ms，避免拖慢 OKR 页面；</li>
 *   <li>短缓存：同租户 60 秒，降低跨服务调用频率；</li>
 *   <li>降级：云才超时/非 200/解析失败时返回空树并置 degraded=true，不抛异常。</li>
 * </ul>
 */
@Component
public class DeptTreeClient {

    private static final Logger log = LoggerFactory.getLogger(DeptTreeClient.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String PERMISSION = "talent:org:tree";

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public DeptTreeClient(@Value("${collab.talent-service-url:http://localhost:18097}") String baseUrl,
                          ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
    }

    public record Result(boolean degraded, String reason, List<JsonNode> departments) {
    }

    private record CacheEntry(Instant at, List<JsonNode> departments) {
    }

    public Result tree(long tenantId, long userId) {
        CacheEntry cached = cache.get(tenantId);
        if (cached != null && cached.at().isAfter(Instant.now().minus(CACHE_TTL))) {
            return new Result(false, null, cached.departments());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/talent/v1/orgs/tree?depth=6"))
                    .timeout(Duration.ofMillis(1500))
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Permissions", PERMISSION)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("dept tree degraded: talent status {}", response.statusCode());
                return new Result(true, "组织架构服务返回 " + response.statusCode(), List.of());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return new Result(true, "组织架构服务返回格式不符合预期", List.of());
            }
            List<JsonNode> departments = objectMapper.convertValue(data,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
            cache.put(tenantId, new CacheEntry(Instant.now(), departments));
            return new Result(false, null, departments);
        } catch (Exception ex) {
            log.warn("dept tree degraded: {}", ex.getMessage());
            return new Result(true, "组织架构服务不可用", List.of());
        }
    }
}
