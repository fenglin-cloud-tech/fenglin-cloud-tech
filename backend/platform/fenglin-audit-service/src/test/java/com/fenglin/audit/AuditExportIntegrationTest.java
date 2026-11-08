package com.fenglin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.service.AuditExportJob;
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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HUB-API-802 审计日志导出：真实 PostgreSQL + RLS + 异步任务 + CSV 下载。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fenglin.audit.partition-maintenance-enabled=false",
                "fenglin.audit.chain-verify-enabled=false",
                "fenglin.audit.export-job-enabled=false",
                "fenglin.audit.export-dir=target/audit-export-test"
        })
class AuditExportIntegrationTest {

    private static final String EXPORT_PERMISSION = "platform:audit:export";
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

    @Autowired
    private AuditExportJob exportJob;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_audit_log, fenglin_hub.hub_audit_chain, "
                    + "fenglin_hub.hub_audit_export_task");
        }
    }

    @Test
    void exportsOwnTenantLogsToCsv() {
        record("AUTH_LOGIN_SUCCESS", "7101", "登录成功");
        record("IAM_USER_CREATED", "7101", "创建用户");
        record("FILE_UPLOADED", "7101", "上传文件");
        record("TENANT_CREATED", "7102", "创建租户");

        ResponseEntity<String> forbidden = exportTask("7101", null, "CSV");
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> created = exportTask("7101", EXPORT_PERMISSION, "CSV");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode createdData = read(created.getBody()).get("data");
        long taskId = createdData.get("task_id").asLong();
        assertThat(createdData.get("status").asText()).isEqualTo("PENDING");
        assertThat(createdData.get("task_no").asText()).startsWith("AEX");

        assertThat(exportJob.runOnce()).isEqualTo(1);

        ResponseEntity<String> task = get("/api/hub/v1/admin/audit/export-tasks/" + taskId, "7101", EXPORT_PERMISSION);
        assertThat(task.getStatusCode().value()).isEqualTo(200);
        JsonNode taskData = read(task.getBody()).get("data");
        assertThat(taskData.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(taskData.get("row_count").asLong()).isEqualTo(4);

        ResponseEntity<byte[]> download = restTemplate.exchange(
                "http://localhost:" + port + "/api/hub/v1/admin/audit/export-tasks/" + taskId + "/download",
                HttpMethod.GET, new HttpEntity<>(headers("7101", EXPORT_PERMISSION)), byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        String csv = new String(download.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFlog_no,event_type");
        assertThat(csv).contains("AUTH_LOGIN_SUCCESS", "IAM_USER_CREATED", "FILE_UPLOADED", "AUDIT_EXPORT_CREATED");
        assertThat(csv).doesNotContain("TENANT_CREATED");

        ResponseEntity<String> otherTenant = get("/api/hub/v1/admin/audit/export-tasks/" + taskId,
                "7102", EXPORT_PERMISSION);
        assertThat(otherTenant.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsUnsupportedFormatAndInvalidRange() {
        record("AUTH_LOGIN_SUCCESS", "7103", "登录成功");

        assertThat(exportTask("7103", EXPORT_PERMISSION, "XLSX").getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = new HashMap<>();
        body.put("from", Instant.now().toString());
        body.put("to", Instant.now().minusSeconds(3600).toString());
        body.put("format", "CSV");
        ResponseEntity<String> invalid = restTemplate.exchange(
                "http://localhost:" + port + "/api/hub/v1/admin/audit/logs:export",
                HttpMethod.POST, new HttpEntity<>(body, headers("7103", EXPORT_PERMISSION)), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
    }

    private ResponseEntity<String> exportTask(String tenantId, String permission, String format) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", Instant.now().minusSeconds(60).toString());
        body.put("to", Instant.now().plusSeconds(60).toString());
        body.put("eventType", "ALL");
        body.put("format", format);
        return restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/admin/audit/logs:export",
                HttpMethod.POST, new HttpEntity<>(body, headers(tenantId, permission)), String.class);
    }

    private ResponseEntity<String> get(String path, String tenantId, String permission) {
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers(tenantId, permission)), String.class);
    }

    private void record(String eventType, String tenantId, String detailText) {
        Map<String, Object> body = new HashMap<>();
        body.put("event_type", eventType);
        body.put("module", "audit");
        body.put("action", eventType);
        body.put("risk_level", "LOW");
        body.put("operator_id", 2001);
        body.put("operator_name", "系统管理员");
        body.put("target_type", "TENANT");
        body.put("target_id", tenantId);
        body.put("detail", Map.of("memo", detailText));

        HttpHeaders headers = headers(tenantId, null);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/internal/v1/audit/logs",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
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
