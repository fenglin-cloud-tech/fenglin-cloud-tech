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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2：审计哈希链校验接口（内嵌真实 PostgreSQL + 完整迁移，fl_app + RLS）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "fenglin.audit.partition-maintenance-enabled=false")
class AuditChainVerifyIntegrationTest {

    private static final String READ_PERMISSION = "hub:audit:read";
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
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
    void intactChainPassesVerification() {
        for (int i = 0; i < 3; i++) {
            assertThat(record("AUTH_LOGIN_SUCCESS", "auth", "1001").status()).isEqualTo(200);
        }

        HttpResult verified = verify("?tenantId=1001", Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(verified.status()).isEqualTo(200);
        JsonNode data = verified.body().get("data");
        assertThat(data.get("tenant_id").asLong()).isEqualTo(1001L);
        assertThat(data.get("checked").asLong()).isEqualTo(3L);
        assertThat(data.get("valid").asBoolean()).isTrue();
        // 全局 Jackson 省略 null 字段，此处兼容“缺省”与“显式 null”两种序列化
        assertThat(data.get("broken_seq") == null || data.get("broken_seq").isNull()).isTrue();
        assertThat(data.get("broken_log_id") == null || data.get("broken_log_id").isNull()).isTrue();
    }

    @Test
    void tamperedAuditDetailIsDetected() throws Exception {
        record("TENANT_CREATED", "tenant", "1001");
        record("AUTH_LOGIN_SUCCESS", "auth", "1001");
        record("IAM_USER_CREATED", "iam", "1001");

        long brokenLogId = chainLogId(1001L, 2L);
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE fenglin_hub.hub_audit_log SET detail = '{"tampered":true}'::jsonb WHERE id = %d
                    """.formatted(brokenLogId));
        }

        HttpResult verified = verify("?tenantId=1001", Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(verified.status()).isEqualTo(200);
        JsonNode data = verified.body().get("data");
        assertThat(data.get("valid").asBoolean()).isFalse();
        assertThat(data.get("broken_seq").asLong()).isEqualTo(2L);
        assertThat(data.get("broken_log_id").asLong()).isEqualTo(brokenLogId);
        assertThat(data.get("message").asText()).contains("content_hash");
    }

    @Test
    void crossTenantVerificationIsBlockedByRls() {
        assertThat(record("IAM_USER_CREATED", "iam", "1002").status()).isEqualTo(200);

        HttpResult verified = verify("?tenantId=1002", Map.of("X-Tenant-Id", "1001", "X-Permissions", READ_PERMISSION));
        assertThat(verified.status()).isEqualTo(200);
        JsonNode data = verified.body().get("data");
        assertThat(data.get("tenant_id").asLong()).isEqualTo(1002L);
        assertThat(data.get("checked").asLong()).isZero();
        assertThat(data.get("valid").asBoolean()).isTrue();
    }

    @Test
    void verificationRequiresAuditPermission() {
        HttpResult forbidden = verify("?tenantId=1001", Map.of("X-Tenant-Id", "1001"));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().get("code").asText()).isEqualTo("FL-COMMON-0403");
    }

    private HttpResult record(String eventType, String module, String targetId) {
        Map<String, Object> body = new HashMap<>();
        body.put("event_type", eventType);
        body.put("module", module);
        body.put("action", eventType);
        body.put("risk_level", "HIGH");
        body.put("operator_id", 2001);
        body.put("operator_name", "系统管理员");
        body.put("target_type", "TENANT");
        body.put("target_id", targetId);
        body.put("request_id", "req-" + targetId);
        body.put("client_type", "ADMIN");
        body.put("result", "SUCCESS");
        body.put("detail", Map.of("before", "PENDING", "after", "ACTIVE"));
        return exchange(HttpMethod.POST, "/internal/v1/audit/logs", body, Map.of("X-Tenant-Id", targetId));
    }

    private HttpResult verify(String query, Map<String, String> headers) {
        return exchange(HttpMethod.GET, "/api/hub/v1/admin/audits/chain/verify" + query, null, headers);
    }

    private HttpResult exchange(HttpMethod method, String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, httpHeaders), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private long chainLogId(long tenantId, long seqNo) throws Exception {
        String partitionKey = YearMonth.now(ZoneOffset.UTC).format(SUFFIX);
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT log_id FROM fenglin_hub.hub_audit_chain
                      WHERE tenant_id = %d AND partition_key = '%s' AND seq_no = %d
                     """.formatted(tenantId, partitionKey, seqNo))) {
            assertThat(rs.next()).isTrue();
            return rs.getLong("log_id");
        }
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
