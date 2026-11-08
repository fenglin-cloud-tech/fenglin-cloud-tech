package com.fenglin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.service.AuditChainHasher;
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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1：审计哈希链与审计日志同事务写入（内嵌真实 PostgreSQL + 完整迁移，fl_app + RLS）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "fenglin.audit.partition-maintenance-enabled=false")
class AuditChainIntegrationTest {

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
    void firstRecordStartsChainFromZeroHash() throws Exception {
        HttpResult recorded = record("TENANT_CREATED", "tenant", "1001", "1001");
        assertThat(recorded.status()).isEqualTo(200);

        String partitionKey = YearMonth.now(ZoneOffset.UTC).format(SUFFIX);
        Map<String, Object> chain = chainOf(1001L, partitionKey, 1);
        assertThat(chain.get("seq_no")).isEqualTo(1L);
        assertThat(chain.get("prev_hash")).isEqualTo("0".repeat(64));

        String contentHash = AuditChainHasher.sha256(contentOf((Long) chain.get("log_id")));
        assertThat(chain.get("content_hash")).isEqualTo(contentHash);
        assertThat(chain.get("chain_hash"))
                .isEqualTo(AuditChainHasher.chainHash("0".repeat(64), contentHash));
    }

    @Test
    void sequentialRecordsLinkHashes() throws Exception {
        assertThat(record("TENANT_CREATED", "tenant", "1001", "1001").status()).isEqualTo(200);
        assertThat(record("AUTH_LOGIN_SUCCESS", "auth", "1001", "1001").status()).isEqualTo(200);
        assertThat(record("IAM_USER_CREATED", "iam", "1001", "1001").status()).isEqualTo(200);

        List<Map<String, Object>> chain = chainList(1001L, YearMonth.now(ZoneOffset.UTC).format(SUFFIX));
        assertThat(chain).hasSize(3);
        String prev = "0".repeat(64);
        for (int i = 0; i < chain.size(); i++) {
            Map<String, Object> row = chain.get(i);
            assertThat(row.get("seq_no")).isEqualTo((long) (i + 1));
            assertThat(row.get("prev_hash")).isEqualTo(prev);
            String contentHash = AuditChainHasher.sha256(contentOf((Long) row.get("log_id")));
            assertThat(row.get("content_hash")).isEqualTo(contentHash);
            assertThat(row.get("chain_hash")).isEqualTo(AuditChainHasher.chainHash(prev, contentHash));
            prev = (String) row.get("chain_hash");
        }
    }

    @Test
    void concurrentRecordsHaveContiguousUniqueSeq() throws Exception {
        int total = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                return record("AUTH_LOGIN_SUCCESS", "auth", "concurrent-" + index, "1001").status();
            }));
        }
        start.countDown();
        try {
            for (Future<Integer> future : futures) {
                assertThat(future.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        List<Map<String, Object>> chain = chainList(1001L, YearMonth.now(ZoneOffset.UTC).format(SUFFIX));
        assertThat(chain).hasSize(total);
        Set<Long> seqs = new HashSet<>();
        chain.forEach(row -> seqs.add((Long) row.get("seq_no")));
        assertThat(seqs).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, total).boxed().toList());
    }

    @Test
    void platformTenantKeepsIndependentChain() throws Exception {
        assertThat(record("TENANT_CREATED", "tenant", "1001", "1001").status()).isEqualTo(200);
        assertThat(record("TENANT_STATUS_CHANGED", "tenant", "1001", null).status()).isEqualTo(200);
        assertThat(record("TENANT_STATUS_CHANGED", "tenant", "1002", null).status()).isEqualTo(200);

        String partitionKey = YearMonth.now(ZoneOffset.UTC).format(SUFFIX);
        assertThat(chainList(1001L, partitionKey)).hasSize(1);
        List<Map<String, Object>> platform = chainList(0L, partitionKey);
        assertThat(platform).hasSize(2);
        assertThat(platform.get(0).get("seq_no")).isEqualTo(1L);
        assertThat(platform.get(0).get("prev_hash")).isEqualTo("0".repeat(64));
        assertThat(platform.get(1).get("prev_hash")).isEqualTo(platform.get(0).get("chain_hash"));
    }

    @Test
    void chainFailureBlocksAuditPersistence() throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE fenglin_hub.hub_audit_chain RENAME TO hub_audit_chain_broken");
        }
        try {
            HttpResult recorded = record("TENANT_CREATED", "tenant", "1001", "1001");
            assertThat(recorded.status()).isEqualTo(500);
            assertThat(countLogs()).isZero();
        } finally {
            try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE fenglin_hub.hub_audit_chain_broken RENAME TO hub_audit_chain");
            }
        }
    }

    private HttpResult record(String eventType, String module, String targetId, String tenantHeader) {
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
        body.put("detail", Map.of("before", "PENDING", "after", "ACTIVE", "nested", Map.of("b", 2, "a", 1)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantHeader != null) {
            headers.set("X-Tenant-Id", tenantHeader);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/internal/v1/audit/logs", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode read(String body) {
        try {
            return body == null ? null : objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private String contentOf(long logId) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT id, tenant_id, log_no, event_type, module, action, operator_id, operator_name,
                            target_type, target_id, request_id, trace_id, client_type, result, risk_level,
                            detail::text AS detail, occurred_at
                       FROM fenglin_hub.hub_audit_log WHERE id = %d
                     """.formatted(logId))) {
            assertThat(rs.next()).isTrue();
            return AuditChainHasher.content(
                    rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("log_no"),
                    rs.getString("event_type"), rs.getString("module"), rs.getString("action"),
                    (Long) rs.getObject("operator_id"), rs.getString("operator_name"),
                    rs.getString("target_type"), rs.getString("target_id"),
                    rs.getString("request_id"), rs.getString("trace_id"), rs.getString("client_type"),
                    rs.getString("result"), rs.getString("risk_level"),
                    AuditChainHasher.canonicalDetail(rs.getString("detail")),
                    rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
        }
    }

    private Map<String, Object> chainOf(long tenantId, String partitionKey, long seqNo) throws Exception {
        return chainList(tenantId, partitionKey).stream()
                .filter(row -> ((Long) row.get("seq_no")) == seqNo)
                .findFirst()
                .orElseThrow();
    }

    private List<Map<String, Object>> chainList(long tenantId, String partitionKey) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT id, tenant_id, log_id, partition_key, seq_no, content_hash, prev_hash, chain_hash
                       FROM fenglin_hub.hub_audit_chain
                      WHERE tenant_id = %d AND partition_key = '%s'
                      ORDER BY seq_no
                     """.formatted(tenantId, partitionKey))) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("seq_no", rs.getLong("seq_no"));
                row.put("log_id", rs.getLong("log_id"));
                row.put("content_hash", rs.getString("content_hash"));
                row.put("prev_hash", rs.getString("prev_hash"));
                row.put("chain_hash", rs.getString("chain_hash"));
                rows.add(row);
            }
        }
        return rows;
    }

    private long countLogs() throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_audit_log")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
