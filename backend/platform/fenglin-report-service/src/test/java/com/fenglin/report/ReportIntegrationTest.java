package com.fenglin.report;

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
 * 报表集成测试：真实 PostgreSQL 15（RLS）+ 文件服务 HTTP 桩（复用 HUB-API-701 multipart 契约）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportIntegrationTest {

    private static final long TENANT_A = 7601L;
    private static final long TENANT_B = 7602L;
    private static final long DATASET_ID = 9101L;
    private static final long DASHBOARD_ID = 9201L;
    private static final long REPORT_ID = 9202L;
    private static final String METRIC_PERMISSION = "tenant:report:metric:manage";
    private static final String VIEW_PERMISSION = "biz:report:view";
    private static final String EXPORT_PERMISSION = "biz:report:export";
    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<byte[]> UPLOADS = new CopyOnWriteArrayList<>();
    private static final List<String> UPLOAD_TENANTS = new CopyOnWriteArrayList<>();
    private static final HttpServer FILE_STUB;
    private static final int FILE_STUB_PORT;

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
            FILE_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FILE_STUB.createContext("/api/hub/v1/files", exchange -> {
                UPLOADS.add(exchange.getRequestBody().readAllBytes());
                UPLOAD_TENANTS.add(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
                byte[] body = "{\"code\":\"0\",\"message\":\"success\",\"data\":{\"file_id\":\"9001\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            FILE_STUB.start();
            FILE_STUB_PORT = FILE_STUB.getAddress().getPort();
        } catch (IOException ex) {
            throw new IllegalStateException("文件服务桩启动失败", ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
        registry.add("fenglin.report.file-service-url", () -> "http://localhost:" + FILE_STUB_PORT);
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
        UPLOADS.clear();
        UPLOAD_TENANTS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fenglin_hub.report_test_fact (
                        region varchar(32), month varchar(16), gmv numeric(12,2), orders integer)
                    """);
            statement.execute("GRANT SELECT ON fenglin_hub.report_test_fact TO fl_app");
            statement.execute("TRUNCATE fenglin_hub.hub_metric, fenglin_hub.hub_report_def, "
                    + "fenglin_hub.hub_report_dataset, fenglin_hub.hub_report_dataset_field, "
                    + "fenglin_hub.report_test_fact");
            statement.execute("""
                    INSERT INTO fenglin_hub.report_test_fact VALUES
                      ('华东', '2026-09', 1200.00, 12),
                      ('华南', '2026-09', 800.00, 8),
                      ('华东', '2026-08', 900.00, 9)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_report_dataset
                      (id, tenant_id, code, name, sql_template, status)
                    VALUES (%d, %d, 'DS_SALES', '销售数据集',
                            'SELECT region, month, gmv, orders FROM fenglin_hub.report_test_fact', 'ENABLED')
                    """.formatted(DATASET_ID, TENANT_A));
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_report_dataset_field
                      (id, tenant_id, dataset_id, field_name, column_name, field_role, aggregate, data_type, sort)
                    VALUES
                      (9111, %d, %d, 'region', 'region', 'DIMENSION', NULL, 'STRING', 1),
                      (9112, %d, %d, 'month', 'month', 'DIMENSION', NULL, 'STRING', 2),
                      (9113, %d, %d, 'gmv', 'gmv', 'MEASURE', 'SUM', 'NUMBER', 3),
                      (9114, %d, %d, 'orders', 'orders', 'MEASURE', 'COUNT', 'NUMBER', 4)
                    """.formatted(TENANT_A, DATASET_ID, TENANT_A, DATASET_ID,
                    TENANT_A, DATASET_ID, TENANT_A, DATASET_ID));
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_report_def
                      (id, tenant_id, code, name, report_type, dataset_id, config, status)
                    VALUES (%d, %d, 'DB_SALES', '销售看板', 'DASHBOARD', NULL,
                      '{"charts":[{"chartId":1,"datasetId":%d,"dimensions":["region"],"measures":["gmv"],
                        "filters":[{"field":"month","op":"EQ","value":"$month"}]},
                        {"chartId":2,"datasetId":9999,"dimensions":["region"],"measures":["gmv"]}]}'::jsonb,
                      'PUBLISHED')
                    """.formatted(DASHBOARD_ID, TENANT_A, DATASET_ID));
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_report_def
                      (id, tenant_id, code, name, report_type, dataset_id, config, status)
                    VALUES (%d, %d, 'RPT_SALES', '销售报表', 'REPORT', %d,
                      '{"datasetId":%d,"dimensions":["region"],"measures":["gmv"]}'::jsonb, 'PUBLISHED')
                    """.formatted(REPORT_ID, TENANT_A, DATASET_ID, DATASET_ID));
        }
    }

    @Test
    void createsAndListsMetrics() {
        assertThat(createMetric(TENANT_A, null, "gmv_total").getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> created = createMetric(TENANT_A, METRIC_PERMISSION, "gmv_total");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode metric = read(created.getBody()).get("data");
        assertThat(metric.get("code").asText()).isEqualTo("gmv_total");
        assertThat(metric.get("dimensions").get(0).asText()).isEqualTo("region");
        assertThat(metric.get("lineage").get("upstream").get(0).asText()).isEqualTo("dwd_order");

        ResponseEntity<String> duplicated = createMetric(TENANT_A, METRIC_PERMISSION, "gmv_total");
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("0010001");

        JsonNode list = read(get("/api/hub/v1/admin/metrics?category=ATOMIC", TENANT_A, METRIC_PERMISSION)
                .getBody()).get("data");
        assertThat(list.get("total").asLong()).isEqualTo(1);
        assertThat(list.get("records").get(0).get("unit").asText()).isEqualTo("CNY");

        JsonNode otherTenant = read(get("/api/hub/v1/admin/metrics", TENANT_B, METRIC_PERMISSION).getBody())
                .get("data");
        assertThat(otherTenant.get("total").asLong()).isZero();

        assertThat(AUDIT_EVENTS).anyMatch(event -> "REPORT_METRIC_CREATED".equals(event.eventType()));
    }

    @Test
    void queriesDatasetWithWhitelistAndTenantIsolation() {
        Map<String, Object> body = Map.of(
                "dimensions", List.of("region"),
                "measures", List.of("gmv", "orders"),
                "filters", List.of(Map.of("field", "month", "op", "EQ", "value", "2026-09")));
        ResponseEntity<String> response = post("/api/hub/v1/report/datasets/" + DATASET_ID + "/query",
                TENANT_A, VIEW_PERMISSION, body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = read(response.getBody()).get("data");
        assertThat(data.get("columns").get(0).asText()).isEqualTo("region");
        assertThat(data.get("columns").get(1).asText()).isEqualTo("gmv");
        assertThat(data.get("rows")).hasSize(2);
        assertThat(data.get("rows").get(0).get(1).asDouble()).isEqualTo(1200.0);

        Map<String, Object> illegal = Map.of(
                "dimensions", List.of("password"),
                "measures", List.of("gmv"));
        ResponseEntity<String> rejected = post("/api/hub/v1/report/datasets/" + DATASET_ID + "/query",
                TENANT_A, VIEW_PERMISSION, illegal);
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> otherTenant = post("/api/hub/v1/report/datasets/" + DATASET_ID + "/query",
                TENANT_B, VIEW_PERMISSION, body);
        assertThat(otherTenant.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void dashboardReturnsPartialDataWhenChartFails() {
        Map<String, Object> body = Map.of("params", Map.of("month", "2026-09"));
        ResponseEntity<String> response = post("/api/hub/v1/report/dashboards/" + DASHBOARD_ID + "/data",
                TENANT_A, VIEW_PERMISSION, body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = read(response.getBody()).get("data");
        assertThat(data.get("degraded").asBoolean()).isTrue();
        assertThat(data.get("charts")).hasSize(2);
        assertThat(data.get("charts").get(0).get("degraded").asBoolean()).isFalse();
        assertThat(data.get("charts").get(0).get("rows")).hasSize(2);
        assertThat(data.get("charts").get(1).get("degraded").asBoolean()).isTrue();
        assertThat(data.get("charts").get(1).get("error_code").asText()).isEqualTo("0000008");
    }

    @Test
    void exportsAllFormatsThroughFileService() {
        assertThat(post("/api/hub/v1/report:export", TENANT_A, null,
                Map.of("report_id", REPORT_ID, "format", "CSV")).getStatusCode().value()).isEqualTo(403);

        JsonNode csv = export("CSV");
        assertThat(csv.get("file_id").asText()).isEqualTo("9001");
        assertThat(csv.get("download_url").asText()).contains("/api/hub/v1/files/9001/download");
        assertThat(csv.get("watermark").asBoolean()).isFalse();
        assertThat(new String(fileContent(0), StandardCharsets.UTF_8)).contains("region", "华东");

        JsonNode excel = export("EXCEL");
        assertThat(excel.get("watermark").asBoolean()).isTrue();
        assertThat(fileContent(1)).startsWith((byte) 'P', (byte) 'K');

        JsonNode pdf = export("PDF");
        assertThat(pdf.get("watermark").asBoolean()).isTrue();
        assertThat(new String(fileContent(2), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        JsonNode png = export("PNG");
        assertThat(png.get("watermark").asBoolean()).isTrue();
        assertThat(fileContent(3)[0] & 0xFF).isEqualTo(0x89);
        assertThat(UPLOAD_TENANTS).containsOnly(String.valueOf(TENANT_A));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "REPORT_EXPORTED".equals(event.eventType()));
    }

    // ---------- helpers ----------

    /** 从 multipart 原始报文中截取 file 分段内容（桩服务未做解析）。 */
    private byte[] fileContent(int index) {
        byte[] raw = UPLOADS.get(index);
        int start = indexOf(raw, new byte[]{13, 10, 13, 10}, 0) + 4;
        int end = indexOf(raw, new byte[]{13, 10, 45, 45}, start);
        return java.util.Arrays.copyOfRange(raw, start, end);
    }

    private int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalStateException("未找到 multipart 分段边界");
    }

    private JsonNode export(String format) {
        ResponseEntity<String> response = post("/api/hub/v1/report:export", TENANT_A, EXPORT_PERMISSION,
                Map.of("report_id", REPORT_ID, "format", format));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return read(response.getBody()).get("data");
    }

    private ResponseEntity<String> createMetric(long tenantId, String permission, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "GMV 总额");
        body.put("metric_type", "ATOMIC");
        body.put("formula", "sum(gmv)");
        body.put("unit", "CNY");
        body.put("grain", "DAY");
        body.put("dimensions", List.of("region"));
        body.put("lineage", Map.of("upstream", List.of("dwd_order")));
        return restTemplate.exchange(url("/api/hub/v1/admin/metrics"), HttpMethod.POST,
                new HttpEntity<>(body, headers(tenantId, permission)), String.class);
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
