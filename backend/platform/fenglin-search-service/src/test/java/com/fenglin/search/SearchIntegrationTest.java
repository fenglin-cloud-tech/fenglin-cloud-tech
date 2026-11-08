package com.fenglin.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.search.service.SearchIndexTaskJob;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 搜索中心集成测试：真实 PostgreSQL（含 RLS）+ 真实 Elasticsearch 8.11 容器。
 * 覆盖 901 统一检索（租户/密级过滤）、902 建议、903 索引任务、904 分析报表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fenglin.search.index-task-job-enabled=false",
                "fenglin.search.es-uri=http://localhost:9200"
        })
class SearchIntegrationTest {

    private static final long TENANT_A = 7401L;
    private static final long TENANT_B = 7402L;
    private static final String QUERY_PERMISSION = "biz:search:query";
    private static final String MANAGE_PERMISSION = "platform:search:index:manage";
    private static final String DOMAIN_INDEX = "hub_hub";
    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
    }

    @TestConfiguration
    static class RecordingAuditConfig {

        @Bean
        @Primary
        AuditSink recordingAuditSink() {
            return AUDIT_EVENTS::add;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchIndexTaskJob indexTaskJob;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        AUDIT_EVENTS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_search_index_task, fenglin_hub.hub_search_query_log");
        }
        deleteIndex(DOMAIN_INDEX);
        deleteIndex("hub_" + TENANT_A + "_crm_lead");
        seedDocument("D1", TENANT_A, "差旅报销标准", "差旅报销制度与标准说明", 0);
        seedDocument("D2", TENANT_A, "差旅申请流程", "差旅申请流程指引", 2);
        seedDocument("D3", TENANT_B, "差旅报销标准", "差旅报销制度", 0);
        client.indices().refresh(r -> r.index(DOMAIN_INDEX));
    }

    @Test
    void searchesWithinTenantAndSecretLevel() {
        ResponseEntity<String> forbidden = query(TENANT_A, null, 0, "差旅报销");
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        JsonNode publicResult = read(query(TENANT_A, QUERY_PERMISSION, 0, "差旅报销").getBody()).get("data");
        JsonNode groups = publicResult.get("groups");
        assertThat(groups).hasSize(1);
        JsonNode hubGroup = findGroup(groups, "hub");
        assertThat(hubGroup.get("records")).hasSize(1);
        JsonNode record = hubGroup.get("records").get(0);
        assertThat(record.get("doc_id").asText()).isEqualTo("D1");
        assertThat(record.get("title").asText()).isEqualTo("差旅报销标准");
        assertThat(record.get("highlight").asText()).contains("<em>");
        assertThat(publicResult.get("latency_ms").asLong()).isGreaterThanOrEqualTo(0);

        JsonNode secretResult = read(query(TENANT_A, QUERY_PERMISSION, 2, "差旅报销").getBody()).get("data");
        assertThat(findGroup(secretResult.get("groups"), "hub").get("records")).hasSize(2);

        JsonNode otherTenant = read(query(TENANT_B, QUERY_PERMISSION, 0, "差旅报销").getBody()).get("data");
        assertThat(findGroup(otherTenant.get("groups"), "hub").get("records")).hasSize(1);
        assertThat(findGroup(otherTenant.get("groups"), "hub").get("records").get(0).get("doc_id").asText())
                .isEqualTo("D3");
    }

    @Test
    void suggestsTitlesWithinSecretLevel() {
        JsonNode data = read(suggest(TENANT_A, "差旅").getBody()).get("data");
        assertThat(data.get("suggestions")).hasSize(1);
        assertThat(data.get("suggestions").get(0).asText()).isEqualTo("差旅报销标准");

        JsonNode deep = read(suggestWithLevel(TENANT_A, "差旅", 2).getBody()).get("data");
        assertThat(deep.get("suggestions")).hasSize(2);
    }

    @Test
    void createsIndexTaskIdempotentlyAndExecutes() {
        ResponseEntity<String> missing = createIndexTask(TENANT_A, "crm_lead", "INCREMENTAL");
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(read(missing.getBody()).get("message").asText()).contains("0008001");

        ResponseEntity<String> created = createIndexTask(TENANT_A, "crm_lead", "FULL");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode first = read(created.getBody()).get("data");
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        long taskId = first.get("task_id").asLong();

        JsonNode duplicated = read(createIndexTask(TENANT_A, "crm_lead", "FULL").getBody()).get("data");
        assertThat(duplicated.get("task_id").asLong()).isEqualTo(taskId);

        assertThat(indexTaskJob.runOnce()).isEqualTo(1);

        JsonNode task = read(get("/api/hub/v1/admin/search/index-tasks/" + taskId,
                TENANT_A, MANAGE_PERMISSION).getBody()).get("data");
        assertThat(task.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(task.get("doc_count").asLong()).isZero();

        assertThat(AUDIT_EVENTS)
                .anyMatch(event -> "SEARCH_INDEX_TASK_CREATED".equals(event.eventType())
                        && event.tenantId() == TENANT_A);
    }

    @Test
    void reportsHotAndZeroResultKeywords() {
        query(TENANT_A, QUERY_PERMISSION, 0, "差旅报销");
        query(TENANT_A, QUERY_PERMISSION, 0, "不存在的词");

        JsonNode hot = read(get("/api/hub/v1/admin/search/analytics?type=HOT", TENANT_A, MANAGE_PERMISSION)
                .getBody()).get("data");
        assertThat(hot.get("type").asText()).isEqualTo("HOT");
        assertThat(hot.get("items")).hasSize(2);

        JsonNode zero = read(get("/api/hub/v1/admin/search/analytics?type=ZERO_RESULT", TENANT_A, MANAGE_PERMISSION)
                .getBody()).get("data");
        assertThat(zero.get("items")).hasSize(1);
        assertThat(zero.get("items").get(0).get("keyword").asText()).isEqualTo("不存在的词");

        assertThat(read(get("/api/hub/v1/admin/search/analytics?type=HOT", TENANT_B, MANAGE_PERMISSION)
                .getBody()).get("data").get("items")).isEmpty();
    }

    // ---------- helpers ----------

    private void seedDocument(String docId, long tenantId, String title, String content, int secretLevel)
            throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("doc_id", docId);
        document.put("tenant_id", tenantId);
        document.put("title", title);
        document.put("content", content);
        document.put("secret_level", secretLevel);
        document.put("updated_at", "2026-11-08T00:00:00Z");
        client.index(i -> i.index(DOMAIN_INDEX).id(docId).document(document));
    }

    private void deleteIndex(String index) throws Exception {
        client.indices().delete(d -> d.index(index).ignoreUnavailable(true));
    }

    private ResponseEntity<String> query(long tenantId, String permission, int secretLevel, String keyword) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", keyword);
        body.put("domains", List.of("hub"));
        body.put("mode", "HYBRID");
        body.put("page", 1);
        body.put("size", 10);
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/search/query",
                HttpMethod.POST, new HttpEntity<>(body, headers(tenantId, permission, secretLevel)), String.class);
    }

    private ResponseEntity<String> suggest(long tenantId, String keyword) {
        return suggestWithLevel(tenantId, keyword, 0);
    }

    private ResponseEntity<String> suggestWithLevel(long tenantId, String keyword, int secretLevel) {
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/search/suggest?q=" + keyword,
                HttpMethod.GET, new HttpEntity<>(headers(tenantId, null, secretLevel)), String.class);
    }

    private ResponseEntity<String> createIndexTask(long tenantId, String indexName, String taskType) {
        Map<String, Object> body = new HashMap<>();
        body.put("index_name", indexName);
        body.put("task_type", taskType);
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/admin/search/index-tasks",
                HttpMethod.POST, new HttpEntity<>(body, headers(tenantId, MANAGE_PERMISSION, 0)), String.class);
    }

    private ResponseEntity<String> get(String path, long tenantId, String permission) {
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers(tenantId, permission, 0)), String.class);
    }

    private HttpHeaders headers(long tenantId, String permission, int secretLevel) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", "2001");
        headers.set("X-Secret-Level", String.valueOf(secretLevel));
        if (permission != null) {
            headers.set("X-Permissions", permission);
        }
        return headers;
    }

    private JsonNode findGroup(JsonNode groups, String domain) {
        for (JsonNode group : groups) {
            if (domain.equals(group.get("domain").asText())) {
                return group;
            }
        }
        throw new IllegalStateException("未找到检索域: " + domain);
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }
}
