package com.fenglin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计落库与查询集成测试：内嵌真实 PostgreSQL + 完整迁移，应用以 fl_app 执行（RLS 生效）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditFlowIntegrationTest {

    private static final String READ_PERMISSION = "hub:audit:read";
    private static final String JDBC_URL;

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_audit_log, fenglin_hub.hub_audit_chain");
        }
    }

    @Test
    void tenantEventIsPersistedAndQueryable() throws Exception {
        HttpResult recorded = post("/internal/v1/audit/logs", event("TENANT_CREATED", "tenant", "1001"),
                Map.of("X-Tenant-Id", "1001", "X-Request-Id", "req-1"));
        assertThat(recorded.status()).isEqualTo(200);
        long logId = recorded.body().get("data").get("log_id").asLong();
        assertThat(recorded.body().get("data").get("log_no").asText()).startsWith("AUD");

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT tenant_id, event_type, module, action, target_type, target_id, request_id, detail::text
                      FROM fenglin_hub.hub_audit_log WHERE id = %d
                    """.formatted(logId));
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("tenant_id")).isEqualTo(1001L);
            assertThat(rs.getString("event_type")).isEqualTo("TENANT_CREATED");
            assertThat(rs.getString("module")).isEqualTo("tenant");
            assertThat(rs.getString("request_id")).isEqualTo("req-1");
            assertThat(rs.getString("detail")).contains("after");
        }

        HttpResult list = get("/api/hub/v1/admin/audits?eventType=TENANT_CREATED",
                Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get("data").get("total").asLong()).isEqualTo(1);
        JsonNode record = list.body().get("data").get("records").get(0);
        assertThat(record.get("event_type").asText()).isEqualTo("TENANT_CREATED");
        assertThat(record.get("operator_id").asLong()).isEqualTo(2001L);
        assertThat(record.get("detail").get("after").asText()).isEqualTo("ACTIVE");

        HttpResult detail = get("/api/hub/v1/admin/audits/" + logId,
                Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().get("data").get("log_no").asText()).isNotBlank();
    }

    @Test
    void queryRequiresAuditPermission() {
        HttpResult forbidden = get("/api/hub/v1/admin/audits", Map.of("X-Tenant-Id", "1001"));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().get("code").asText()).isEqualTo("FL-COMMON-0403");
    }

    @Test
    void auditLogsAreTenantIsolated() {
        post("/internal/v1/audit/logs", event("IAM_USER_CREATED", "iam", "3001"), Map.of("X-Tenant-Id", "1001"));
        post("/internal/v1/audit/logs", event("IAM_USER_CREATED", "iam", "3002"), Map.of("X-Tenant-Id", "1002"));

        HttpResult tenantA = get("/api/hub/v1/admin/audits",
                Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(tenantA.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(tenantA.body().get("data").get("records").get(0).get("target_id").asText()).isEqualTo("3001");

        HttpResult tenantB = get("/api/hub/v1/admin/audits",
                Map.of("X-Tenant-Id", "1002", "X-Permissions", READ_PERMISSION));
        assertThat(tenantB.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(tenantB.body().get("data").get("records").get(0).get("target_id").asText()).isEqualTo("3002");
    }

    @Test
    void platformLevelEventUsesTenantZero() throws Exception {
        HttpResult recorded = post("/internal/v1/audit/logs", event("TENANT_STATUS_CHANGED", "tenant", "1001"),
                Map.of());
        assertThat(recorded.status()).isEqualTo(200);

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT count(*) FROM fenglin_hub.hub_audit_log
                     WHERE tenant_id = 0 AND event_type = 'TENANT_STATUS_CHANGED'
                    """);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void appRoleCannotReadAuditWithoutContext() throws Exception {
        post("/internal/v1/audit/logs", event("TENANT_CREATED", "tenant", "1001"), Map.of("X-Tenant-Id", "1001"));
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_app");
            ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_audit_log");
            assertThat(rs.next()).isTrue();
            // 未设置租户上下文时仅可见平台级 tenant_id = 0
            assertThat(rs.getInt(1)).isZero();
        }
    }

    private Map<String, Object> event(String eventType, String module, String targetId) {
        Map<String, Object> body = new HashMap<>();
        body.put("event_type", eventType);
        body.put("module", module);
        body.put("action", eventType);
        body.put("risk_level", "HIGH");
        body.put("operator_id", 2001);
        body.put("operator_name", "系统管理员");
        body.put("target_type", "TENANT");
        body.put("target_id", targetId);
        body.put("request_id", "req-1");
        body.put("client_type", "ADMIN");
        body.put("result", "SUCCESS");
        body.put("detail", Map.of("before", "PENDING", "after", "ACTIVE"));
        return body;
    }

    private HttpResult post(String path, Object body, Map<String, String> headers) {
        return exchange(HttpMethod.POST, path, body, headers);
    }

    private HttpResult get(String path, Map<String, String> headers) {
        return exchange(HttpMethod.GET, path, null, headers);
    }

    private HttpResult exchange(HttpMethod method, String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, httpHeaders), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
