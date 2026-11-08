package com.fenglin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.service.AuditChainVerifyJob;
import com.fenglin.audit.web.dto.ChainVerifyResult;
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
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定时任务：审计链完整性每小时校验（内嵌真实 PostgreSQL + 完整迁移 + RLS）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "fenglin.audit.partition-maintenance-enabled=false",
                "fenglin.audit.chain-verify-enabled=false"
        })
class AuditChainVerifyJobIntegrationTest {

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

    @Autowired
    private AuditChainVerifyJob job;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_audit_log, fenglin_hub.hub_audit_chain");
        }
    }

    @Test
    void verifiesEachTenantChainIndependently() {
        record("AUTH_LOGIN_SUCCESS", "7001");
        record("IAM_USER_CREATED", "7001");
        record("FILE_UPLOADED", "7001");
        record("TENANT_CREATED", "7002");

        AuditChainVerifyJob.VerifySummary summary = job.runOnce(Instant.now());

        assertThat(summary.checked()).isEqualTo(2);
        assertThat(summary.broken()).isZero();
        Map<Long, ChainVerifyResult> byTenant = new HashMap<>();
        summary.results().forEach(result -> byTenant.put(result.tenantId(), result));
        assertThat(byTenant.get(7001L).checked()).isEqualTo(3);
        assertThat(byTenant.get(7001L).valid()).isTrue();
        assertThat(byTenant.get(7002L).checked()).isEqualTo(1);
        assertThat(byTenant.get(7002L).valid()).isTrue();
    }

    @Test
    void detectsTamperedAuditRecord() throws Exception {
        record("TENANT_CREATED", "7001");
        record("AUTH_LOGIN_SUCCESS", "7001");
        record("IAM_USER_CREATED", "7001");

        long brokenLogId = chainLogId(7001L, 2L);
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE fenglin_hub.hub_audit_log SET detail = '{"tampered":true}'::jsonb WHERE id = %d
                    """.formatted(brokenLogId));
        }

        AuditChainVerifyJob.VerifySummary summary = job.runOnce(Instant.now());

        assertThat(summary.checked()).isEqualTo(1);
        assertThat(summary.broken()).isEqualTo(1);
        ChainVerifyResult broken = summary.results().get(0);
        assertThat(broken.valid()).isFalse();
        assertThat(broken.brokenSeq()).isEqualTo(2L);
        assertThat(broken.brokenLogId()).isEqualTo(brokenLogId);
        assertThat(broken.message()).contains("content_hash");
    }

    @Test
    void skipsTenantsWithoutChainRecords() {
        record("AUTH_LOGIN_SUCCESS", "7003");

        AuditChainVerifyJob.VerifySummary summary = job.runOnce(Instant.now());

        assertThat(summary.checked()).isEqualTo(1);
        assertThat(summary.results().get(0).tenantId()).isEqualTo(7003L);
    }

    private void record(String eventType, String tenantId) {
        Map<String, Object> body = new HashMap<>();
        body.put("event_type", eventType);
        body.put("module", "audit");
        body.put("action", eventType);
        body.put("risk_level", "LOW");
        body.put("operator_id", 2001);
        body.put("operator_name", "系统管理员");
        body.put("target_type", "TENANT");
        body.put("target_id", tenantId);
        body.put("request_id", "req-" + tenantId);
        body.put("client_type", "ADMIN");
        body.put("result", "SUCCESS");
        body.put("detail", Map.of("tenant", tenantId));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", tenantId);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/internal/v1/audit/logs",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(read(response.getBody()).get("data").get("log_id").asLong()).isPositive();
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
}
