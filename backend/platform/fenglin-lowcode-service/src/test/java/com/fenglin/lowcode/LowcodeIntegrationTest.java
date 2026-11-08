package com.fenglin.lowcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.sun.net.httpserver.HttpServer;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 低代码集成测试：真实 PostgreSQL 15（RLS）+ 工作流服务 HTTP 桩（复用 HUB-API-701 发起实例契约）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LowcodeIntegrationTest {

    private static final long TENANT_A = 8601L;
    private static final long TENANT_B = 8602L;
    private static final String DESIGN = "tenant:lowcode:app:design";
    private static final String PUBLISH = "tenant:lowcode:app:publish";
    private static final String USE = "biz:lowcode:app:use";
    private static final String QUERY = "biz:lowcode:app:data:view";
    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_TENANTS = new CopyOnWriteArrayList<>();
    private static final HttpServer WORKFLOW_STUB;
    private static final int WORKFLOW_STUB_PORT;

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
        try {
            WORKFLOW_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            WORKFLOW_STUB.createContext("/api/hub/v1/workflow/instances", exchange -> {
                WORKFLOW_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                WORKFLOW_TENANTS.add(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
                byte[] body = ("{\"code\":\"0\",\"message\":\"success\",\"data\":{"
                        + "\"instance_id\":7001,\"instance_no\":\"WF-7001\","
                        + "\"status\":\"RUNNING\",\"first_task_id\":\"t1\"}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            WORKFLOW_STUB.start();
            WORKFLOW_STUB_PORT = WORKFLOW_STUB.getAddress().getPort();
        } catch (IOException ex) {
            throw new IllegalStateException("工作流服务桩启动失败", ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
        registry.add("fenglin.lowcode.workflow-service-url",
                () -> "http://localhost:" + WORKFLOW_STUB_PORT);
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

    @BeforeEach
    void resetFixture() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        AUDIT_EVENTS.clear();
        WORKFLOW_REQUESTS.clear();
        WORKFLOW_TENANTS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_lowcode_app, fenglin_hub.hub_lowcode_form, "
                    + "fenglin_hub.hub_lowcode_page, fenglin_hub.hub_lowcode_model, "
                    + "fenglin_hub.hub_lowcode_version, fenglin_hub.hub_lowcode_publish, "
                    + "fenglin_hub.hub_lowcode_record");
        }
    }

    @Test
    void createsAppWithSchemaIndexAndTenantIsolation() {
        assertThat(createApp(TENANT_A, null, "asset_apply").getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> created = createApp(TENANT_A, DESIGN, "asset_apply");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode app = read(created.getBody()).get("data");
        assertThat(app.get("code").asText()).isEqualTo("asset_apply");
        assertThat(app.get("status").asText()).isEqualTo("DRAFT");
        assertThat(app.get("app_type").asText()).isEqualTo("FORM");
        assertThat(app.get("schema").get("forms").get(0).get("key").asText()).isEqualTo("main");

        ResponseEntity<String> duplicated = createApp(TENANT_A, DESIGN, "asset_apply");
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("0011001");

        Map<String, Object> illegal = new HashMap<>();
        illegal.put("code", "asset_apply_v2");
        illegal.put("name", "非法模型");
        illegal.put("schema", Map.of("marker", "v1"));
        illegal.put("data_model", Map.of("fields",
                List.of(Map.of("name", "Bad Name", "type", "string"))));
        ResponseEntity<String> illegalModel = post("/api/hub/v1/admin/lowcode/apps",
                TENANT_A, DESIGN, illegal);
        assertThat(illegalModel.getStatusCode().value()).isEqualTo(400);

        JsonNode list = read(get("/api/hub/v1/admin/lowcode/apps?status=DRAFT", TENANT_A, null)
                .getBody()).get("data");
        assertThat(list.get("total").asLong()).isEqualTo(1);
        assertThat(list.get("records").get(0).get("latest_version").asInt()).isZero();

        JsonNode otherTenant = read(get("/api/hub/v1/admin/lowcode/apps", TENANT_B, null)
                .getBody()).get("data");
        assertThat(otherTenant.get("total").asLong()).isZero();

        assertThat(AUDIT_EVENTS).anyMatch(event -> "LOWCODE_APP_CREATED".equals(event.eventType()));
    }

    @Test
    void publishesThroughWorkflowAndIsIdempotent() {
        long appId = appId(createApp(TENANT_A, DESIGN, "asset_apply"));

        assertThat(publish(TENANT_A, null, appId).getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> published = publish(TENANT_A, PUBLISH, appId);
        assertThat(published.getStatusCode().value()).isEqualTo(200);
        JsonNode result = read(published.getBody()).get("data");
        assertThat(result.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(result.get("env").asText()).isEqualTo("PROD");
        assertThat(result.get("version_no").asInt()).isEqualTo(1);
        assertThat(result.get("process_instance_id").asText()).isEqualTo("WF-7001");
        assertThat(WORKFLOW_REQUESTS).hasSize(1);
        assertThat(WORKFLOW_REQUESTS.get(0)).contains("lowcode_publish");
        assertThat(WORKFLOW_TENANTS).containsOnly(String.valueOf(TENANT_A));

        ResponseEntity<String> replay = publish(TENANT_A, PUBLISH, appId);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(read(replay.getBody()).get("data").get("version_no").asInt()).isEqualTo(1);
        assertThat(WORKFLOW_REQUESTS).hasSize(1);

        JsonNode versions = read(get("/api/hub/v1/admin/lowcode/apps/" + appId + "/versions",
                TENANT_A, DESIGN).getBody()).get("data");
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("version_no").asInt()).isEqualTo(1);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "LOWCODE_APP_PUBLISHED".equals(event.eventType()));
    }

    @Test
    void submitsDataWithValidationAndIdempotency() {
        long appId = appId(createApp(TENANT_A, DESIGN, "asset_apply"));
        publish(TENANT_A, PUBLISH, appId);

        Map<String, Object> data = Map.of("asset_name", "MacBook Pro", "quantity", 2);
        assertThat(submit(TENANT_A, null, "asset_apply", data, null).getStatusCode().value())
                .isEqualTo(403);

        Map<String, Object> missing = Map.of("reason", "研发需要");
        ResponseEntity<String> required = submit(TENANT_A, USE, "asset_apply", missing, null);
        assertThat(required.getStatusCode().value()).isEqualTo(400);
        assertThat(read(required.getBody()).get("message").asText()).contains("asset_name");

        Map<String, Object> unknown = Map.of("asset_name", "X", "illegal_field", 1);
        assertThat(submit(TENANT_A, USE, "asset_apply", unknown, null).getStatusCode().value())
                .isEqualTo(400);

        ResponseEntity<String> first = submit(TENANT_A, USE, "asset_apply", data, "idem-1");
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        JsonNode firstData = read(first.getBody()).get("data");
        long recordId = firstData.get("record_id").asLong();
        assertThat(firstData.get("status").asText()).isEqualTo("SUBMITTED");

        ResponseEntity<String> replay = submit(TENANT_A, USE, "asset_apply", data, "idem-1");
        assertThat(read(replay.getBody()).get("data").get("record_id").asLong()).isEqualTo(recordId);

        JsonNode records = read(get("/api/hub/v1/lowcode/apps/asset_apply/data", TENANT_A, QUERY)
                .getBody()).get("data");
        assertThat(records.get("total").asLong()).isEqualTo(1);
        assertThat(records.get("records").get(0).get("data").get("asset_name").asText())
                .isEqualTo("MacBook Pro");

        assertThat(submit(TENANT_B, USE, "asset_apply", data, null).getStatusCode().value())
                .isEqualTo(404);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "LOWCODE_RECORD_SUBMITTED".equals(event.eventType()));
    }

    @Test
    void rollsBackToPublishedVersion() throws Exception {
        long appId = appId(createApp(TENANT_A, DESIGN, "asset_apply"));
        publish(TENANT_A, PUBLISH, appId);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO fenglin_hub.hub_lowcode_version "
                    + "(id, tenant_id, app_id, version_no, schema, data_model, status, published_at) "
                    + "VALUES (9902, " + TENANT_A + ", " + appId + ", 2, "
                    + "'{\"marker\":\"v2\"}'::jsonb, '{\"fields\":[]}'::jsonb, 'PUBLISHED', now())");
            statement.execute("UPDATE fenglin_hub.hub_lowcode_app SET schema = '{\"marker\":\"v2\"}'::jsonb "
                    + "WHERE id = " + appId);
        }

        ResponseEntity<String> rolledBack = post("/api/hub/v1/admin/lowcode/apps/" + appId + ":rollback",
                TENANT_A, PUBLISH, Map.of("version_no", 1));
        assertThat(rolledBack.getStatusCode().value()).isEqualTo(200);
        JsonNode app = read(rolledBack.getBody()).get("data");
        assertThat(app.get("schema").get("marker").asText()).isEqualTo("v1");
        assertThat(app.get("latest_version").asInt()).isEqualTo(2);
        assertThat(app.get("status").asText()).isEqualTo("PUBLISHED");

        ResponseEntity<String> missingVersion = post(
                "/api/hub/v1/admin/lowcode/apps/" + appId + ":rollback",
                TENANT_A, PUBLISH, Map.of("version_no", 99));
        assertThat(missingVersion.getStatusCode().value()).isEqualTo(404);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "LOWCODE_APP_ROLLED_BACK".equals(event.eventType()));
    }

    // ---------- helpers ----------

    private long appId(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return read(response.getBody()).get("data").get("id").asLong();
    }

    private ResponseEntity<String> createApp(long tenantId, String permission, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "资产申请");
        body.put("app_type", "FORM");
        body.put("env", "DEV");
        body.put("schema", Map.of(
                "marker", "v1",
                "forms", List.of(Map.of("key", "main", "name", "主表单",
                        "schema", Map.of("fields", List.of("asset_name")))),
                "pages", List.of(Map.of("key", "home", "name", "首页"))));
        body.put("data_model", Map.of(
                "publish_process_key", "lowcode_publish",
                "fields", List.of(
                        Map.of("name", "asset_name", "type", "string", "required", true, "max_length", 64),
                        Map.of("name", "quantity", "type", "number"),
                        Map.of("name", "reason", "type", "string"))));
        return post("/api/hub/v1/admin/lowcode/apps", tenantId, permission, body);
    }

    private ResponseEntity<String> publish(long tenantId, String permission, long appId) {
        return post("/api/hub/v1/admin/lowcode/apps/" + appId + ":publish", tenantId, permission, null);
    }

    private ResponseEntity<String> submit(long tenantId, String permission, String code,
                                          Map<String, Object> data, String idempotencyKey) {
        HttpHeaders headers = headers(tenantId, permission);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange(url("/api/hub/v1/lowcode/apps/" + code + "/data"),
                HttpMethod.POST, new HttpEntity<>(Map.of("data", data), headers), String.class);
    }

    private ResponseEntity<String> post(String path, long tenantId, String permission, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, headers(tenantId, permission)), String.class);
    }

    private ResponseEntity<String> get(String path, long tenantId, String permission) {
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(headers(tenantId, permission)), String.class);
    }

    private HttpHeaders headers(long tenantId, String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", "3001");
        headers.set("X-Client-Type", "ADMIN");
        if (permission != null) {
            headers.set("X-Permissions", permission);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, ex);
        }
    }
}
