package com.fenglin.ai.client;

import com.fenglin.common.core.BizException;
import com.fenglin.ai.AiErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复用统一检索服务（HUB-API-901）作为 AI 召回底座：租户/密级过滤由检索服务强制注入。
 */
@Component
public class SearchServiceClient {

    private final RestClient restClient;

    public SearchServiceClient(
            @Value("${fenglin.ai.search-service-url:http://localhost:18088}") String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder().baseUrl(normalized).build();
    }

    public List<RetrievedDoc> search(long tenantId, Long userId, String query, String mode,
                                     int size, int secretLevel) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("domains", List.of("hub"));
        body.put("mode", mode);
        body.put("page", 1);
        body.put("size", Math.min(Math.max(size, 1), 50));
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/hub/v1/search/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-User-Id", userId == null ? "" : String.valueOf(userId))
                    .header("X-Client-Type", "PORTAL")
                    .header("X-Permissions", "biz:search:query")
                    .header("X-Secret-Level", String.valueOf(secretLevel))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parse(response);
        } catch (RestClientException ex) {
            throw new BizException(AiErrorCode.MODEL_DEGRADED,
                    "检索服务不可用: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<RetrievedDoc> parse(Map<String, Object> response) {
        List<RetrievedDoc> docs = new ArrayList<>();
        if (response == null || response.get("data") == null) {
            return docs;
        }
        Object groups = ((Map<String, Object>) response.get("data")).get("groups");
        if (!(groups instanceof List<?> list)) {
            return docs;
        }
        for (Object group : list) {
            Map<String, Object> groupMap = (Map<String, Object>) group;
            Object records = groupMap.get("records");
            if (!(records instanceof List<?> rows)) {
                continue;
            }
            for (Object row : rows) {
                Map<String, Object> record = (Map<String, Object>) row;
                Object docId = record.get("doc_id");
                Object title = record.get("title");
                Object score = record.get("score");
                docs.add(new RetrievedDoc(docId == null ? null : String.valueOf(docId),
                        title == null ? null : String.valueOf(title),
                        score instanceof Number number ? number.doubleValue() : 0d,
                        groupMap.get("domain") == null ? null : String.valueOf(groupMap.get("domain"))));
            }
        }
        return docs;
    }

    public record RetrievedDoc(String docId, String title, double score, String domain) {
    }
}
