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
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HUB-API-805 我的操作记录：真实 PostgreSQL + RLS，仅返回本人记录且无权限码要求。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fenglin.audit.partition-maintenance-enabled=false",
                "fenglin.audit.chain-verify-enabled=false",
                "fenglin.audit.export-job-enabled=false"
        })
class AuditMyRecordsIntegrationTest {

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
    void returnsOnlyCurrentUserRecordsWithinTenant() {
        record("AUTH_LOGIN_SUCCESS", "7301", 3001L);
        record("FILE_UPLOADED", "7301", 3001L);
        record("IAM_USER_CREATED", "7301", 3002L);
        record("AUTH_LOGIN_SUCCESS", "7302", 3001L);

        ResponseEntity<String> mine = get("7301", "3001", "");
        assertThat(mine.getStatusCode().value()).isEqualTo(200);
        JsonNode data = read(mine.getBody()).get("data");
        assertThat(data.get("total").asLong()).isEqualTo(2);
        assertThat(data.get("records")).hasSize(2);
        data.get("records").forEach(record ->
                assertThat(record.get("operator_id").asLong()).isEqualTo(3001L));

        JsonNode firstPage = read(get("7301", "3001", "?page=1&size=1").getBody()).get("data");
        assertThat(firstPage.get("records")).hasSize(1);
        assertThat(firstPage.get("total").asLong()).isEqualTo(2);
        JsonNode secondPage = read(get("7301", "3001", "?page=2&size=1").getBody()).get("data");
        assertThat(secondPage.get("records")).hasSize(1);
        assertThat(secondPage.get("records").get(0).get("log_no").asText())
                .isNotEqualTo(firstPage.get("records").get(0).get("log_no").asText());

        String future = "?from=" + Instant.now().plusSeconds(3600);
        assertThat(read(get("7301", "3001", future).getBody()).get("data").get("total").asLong()).isZero();

        assertThat(read(get("7302", "3001", "").getBody()).get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<String> anonymous = get("7301", null, "");
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> get(String tenantId, String userId, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", tenantId);
        if (userId != null) {
            headers.set("X-User-Id", userId);
        }
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/audit/my-records" + query,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void record(String eventType, String tenantId, long operatorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("event_type", eventType);
        body.put("module", "audit");
        body.put("action", eventType);
        body.put("risk_level", "LOW");
        body.put("operator_id", operatorId);
        body.put("operator_name", "用户" + operatorId);
        body.put("target_type", "TENANT");
        body.put("target_id", tenantId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", tenantId);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/internal/v1/audit/logs",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
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
}
