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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HUB-API-804 审计告警配置：真实 PostgreSQL + RLS + 全量替换幂等 + 配置变更落审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fenglin.audit.partition-maintenance-enabled=false",
                "fenglin.audit.chain-verify-enabled=false",
                "fenglin.audit.export-job-enabled=false"
        })
class AuditAlertRuleIntegrationTest {

    private static final String ALERT_PERMISSION = "platform:audit:alert:manage";
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
            statement.execute("TRUNCATE fenglin_hub.hub_audit_alert_rule, fenglin_hub.hub_audit_log, "
                    + "fenglin_hub.hub_audit_chain");
        }
    }

    @Test
    void replacesRuleSetIdempotentlyAndAuditsChange() {
        ResponseEntity<String> created = put("7201", ALERT_PERMISSION, List.of(
                rule("CROSS_TENANT_ACCESS", "CROSS_TENANT", 1, "platform_sec_admin", List.of("STATION", "EMAIL")),
                rule("MASS_DELETE", "DATA_CHANGE", 5, "platform_sec_admin", List.of("STATION"))));
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode data = read(created.getBody()).get("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).get("code").asText()).isEqualTo("CROSS_TENANT_ACCESS");
        assertThat(data.get(0).get("channels").get(1).asText()).isEqualTo("EMAIL");

        ResponseEntity<String> listed = get("7201", ALERT_PERMISSION);
        assertThat(read(listed.getBody()).get("data")).hasSize(2);

        ResponseEntity<String> replaced = put("7201", ALERT_PERMISSION, List.of(
                rule("CROSS_TENANT_ACCESS", "CROSS_TENANT", 3, "platform_sec_admin", List.of("EMAIL"))));
        assertThat(replaced.getStatusCode().value()).isEqualTo(200);
        JsonNode replacedData = read(replaced.getBody()).get("data");
        assertThat(replacedData).hasSize(1);
        assertThat(replacedData.get(0).get("threshold").asInt()).isEqualTo(3);

        assertThat(read(get("7201", ALERT_PERMISSION).getBody()).get("data")).hasSize(1);

        String auditsUrl = "http://localhost:" + port
                + "/api/hub/v1/admin/audits?eventType=AUDIT_ALERT_RULE_UPDATED";
        ResponseEntity<String> audits = restTemplate.exchange(auditsUrl, HttpMethod.GET,
                new HttpEntity<>(headers("7201", READ_PERMISSION)), String.class);
        assertThat(read(audits.getBody()).get("data").get("total").asLong()).isEqualTo(2);

        assertThat(read(get("7202", ALERT_PERMISSION).getBody()).get("data")).isEmpty();
    }

    @Test
    void rejectsForbiddenAndInvalidRules() {
        ResponseEntity<String> forbidden = put("7203", null, List.of(
                rule("CROSS_TENANT_ACCESS", "CROSS_TENANT", 1, "platform_sec_admin", List.of("STATION"))));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> badChannel = put("7203", ALERT_PERMISSION, List.of(
                rule("CROSS_TENANT_ACCESS", "CROSS_TENANT", 1, "platform_sec_admin", List.of("FAX"))));
        assertThat(badChannel.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> badThreshold = put("7203", ALERT_PERMISSION, List.of(
                rule("CROSS_TENANT_ACCESS", "CROSS_TENANT", 0, "platform_sec_admin", List.of("STATION"))));
        assertThat(badThreshold.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> duplicated = put("7203", ALERT_PERMISSION, List.of(
                rule("DUP", "CROSS_TENANT", 1, "platform_sec_admin", List.of("STATION")),
                rule("DUP", "CROSS_TENANT", 2, "platform_sec_admin", List.of("EMAIL"))));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(400);
    }

    private Map<String, Object> rule(String code, String eventType, int threshold,
                                     String receiverRole, List<String> channels) {
        return Map.of("code", code, "event_type", eventType, "threshold", threshold,
                "receiver_role", receiverRole, "channels", channels);
    }

    private ResponseEntity<String> put(String tenantId, String permission, List<Map<String, Object>> items) {
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/admin/audit/alert-rules",
                HttpMethod.PUT, new HttpEntity<>(Map.of("items", items), headers(tenantId, permission)),
                String.class);
    }

    private ResponseEntity<String> get(String tenantId, String permission) {
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/admin/audit/alert-rules",
                HttpMethod.GET, new HttpEntity<>(headers(tenantId, permission)), String.class);
    }

    private HttpHeaders headers(String tenantId, String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", tenantId);
        headers.set("X-User-Id", "2001");
        if (permission != null) {
            headers.set("X-Permissions", permission);
        }
        return headers;
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
