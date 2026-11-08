package com.fenglin.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开放平台集成测试：真实 PostgreSQL 15（RLS）+ 真实 Redis 7 容器。
 * 覆盖 HUB-API-1001~1006 与 HMAC 验签、防重放、配额、调用日志链路。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    private static final long TENANT_A = 7501L;
    private static final long TENANT_B = 7502L;
    private static final String REVIEW_PERMISSION = "platform:openapi:app:review";
    private static final String KEY_PERMISSION = "platform:openapi:key:manage";
    private static final String QUOTA_PERMISSION = "platform:openapi:quota:manage";
    private static final String CALL_LOG_PERMISSION = "platform:openapi:calllog:view";
    private static final String PORTAL_PERMISSION = "biz:openapi:portal:view";
    private static final String PING_PATH = "/openapi/ping/v1/ping";
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
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
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
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        AUDIT_EVENTS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_open_app, fenglin_hub.hub_app_key, "
                    + "fenglin_hub.hub_api_quota, fenglin_hub.hub_api_call_log");
        }
        Set<String> keys = redisTemplate.keys("openapi:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void registersAndReviewsApp() {
        assertThat(createApp(TENANT_A, null, "伙伴门户").getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> created = createApp(TENANT_A, REVIEW_PERMISSION, "伙伴门户");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        JsonNode app = read(created.getBody()).get("data");
        long appId = app.get("app_id").asLong();
        assertThat(appId).isPositive();
        assertThat(app.get("code").asText()).matches("APP\\d{10}");
        assertThat(app.get("review_status").asText()).isEqualTo("PENDING");

        ResponseEntity<String> duplicated = createApp(TENANT_A, REVIEW_PERMISSION, "伙伴门户");
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("0009001");

        assertThat(createApp(TENANT_B, REVIEW_PERMISSION, "伙伴门户").getStatusCode().value()).isEqualTo(200);

        JsonNode reviewed = read(review(TENANT_A, appId, "APPROVE").getBody()).get("data");
        assertThat(reviewed.get("review_status").asText()).isEqualTo("APPROVED");
        assertThat(reviewed.get("status").asText()).isEqualTo("ENABLED");
        assertThat(review(TENANT_A, appId, "REJECT").getStatusCode().value()).isEqualTo(409);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "OPENAPI_APP_CREATED".equals(event.eventType())
                && event.tenantId() == TENANT_A);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "OPENAPI_APP_APPROVED".equals(event.eventType())
                && event.tenantId() == TENANT_A);
    }

    @Test
    void issuesKeyOnceAndRotates() throws Exception {
        long appId = approvedApp(TENANT_A, "密钥应用");

        JsonNode created = read(issueKey(TENANT_A, appId, "CREATE", 0).getBody()).get("data");
        String appKey = created.get("app_key").asText();
        String appSecret = created.get("app_secret").asText();
        assertThat(appKey).startsWith("AK");
        assertThat(appSecret).startsWith("SK-");
        assertThat(created.get("secret_fingerprint").asText()).hasSize(6);

        assertThat(ping(appKey, appSecret, UUID.randomUUID().toString(), Instant.now().getEpochSecond())
                .getStatusCode().value()).isEqualTo(200);

        String stored = queryString("SELECT app_secret_enc FROM fenglin_hub.hub_app_key WHERE app_key = '" + appKey + "'");
        assertThat(stored).isNotEqualTo(appSecret);
        assertThat(stored).doesNotContain(appSecret);

        JsonNode rotated = read(issueKey(TENANT_A, appId, "ROTATE", 0).getBody()).get("data");
        assertThat(rotated.get("app_key").asText()).isNotEqualTo(appKey);
        ResponseEntity<String> oldKey = ping(appKey, appSecret, UUID.randomUUID().toString(),
                Instant.now().getEpochSecond());
        assertThat(oldKey.getStatusCode().value()).isEqualTo(401);
        assertThat(read(oldKey.getBody()).get("code").asText()).isEqualTo("0009002");

        assertThat(AUDIT_EVENTS).anyMatch(event -> "OPENAPI_APP_KEY_ISSUED".equals(event.eventType()));
    }

    @Test
    void verifiesSignatureAndBlocksReplay() {
        long appId = approvedApp(TENANT_A, "验签应用");
        JsonNode key = read(issueKey(TENANT_A, appId, "CREATE", 0).getBody()).get("data");
        String appKey = key.get("app_key").asText();
        String appSecret = key.get("app_secret").asText();

        ResponseEntity<String> ok = ping(appKey, appSecret, UUID.randomUUID().toString(),
                Instant.now().getEpochSecond());
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(read(ok.getBody()).get("data").get("app_id").asLong()).isEqualTo(appId);

        ResponseEntity<String> tampered = signedGet(appKey, appSecret, UUID.randomUUID().toString(),
                Instant.now().getEpochSecond(), "deadbeef");
        assertThat(tampered.getStatusCode().value()).isEqualTo(401);
        assertThat(read(tampered.getBody()).get("code").asText()).isEqualTo("0009002");

        String nonce = UUID.randomUUID().toString();
        assertThat(ping(appKey, appSecret, nonce, Instant.now().getEpochSecond()).getStatusCode().value())
                .isEqualTo(200);
        ResponseEntity<String> replay = ping(appKey, appSecret, nonce, Instant.now().getEpochSecond());
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
        assertThat(read(replay.getBody()).get("message").asText()).contains("nonce");

        ResponseEntity<String> stale = ping(appKey, appSecret, UUID.randomUUID().toString(),
                Instant.now().getEpochSecond() - 600);
        assertThat(stale.getStatusCode().value()).isEqualTo(401);

        JsonNode logs = read(get("/api/hub/v1/admin/openapi/call-logs?appId=" + appId, TENANT_A,
                CALL_LOG_PERMISSION).getBody()).get("data");
        assertThat(logs.get("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(logs.get("records").get(0).get("api_path").asText()).isEqualTo(PING_PATH);
        assertThat(logs.get("records").get(0).get("method").asText()).isEqualTo("GET");
    }

    @Test
    void enforcesDailyQuotaAndPortalQuotaView() {
        long appId = approvedApp(TENANT_A, "配额应用");
        JsonNode key = read(issueKey(TENANT_A, appId, "CREATE", 0).getBody()).get("data");
        String appKey = key.get("app_key").asText();
        String appSecret = key.get("app_secret").asText();

        Map<String, Object> quotaBody = new HashMap<>();
        quotaBody.put("app_id", appId);
        quotaBody.put("items", List.of(Map.of("api_path", PING_PATH, "qps_limit", 50, "daily_limit", 1)));
        ResponseEntity<String> quota = restTemplate.exchange(url("/api/hub/v1/admin/openapi/quotas"),
                HttpMethod.PUT, new HttpEntity<>(quotaBody, headers(TENANT_A, QUOTA_PERMISSION)), String.class);
        assertThat(quota.getStatusCode().value()).isEqualTo(200);
        assertThat(read(quota.getBody()).get("data").get(0).get("daily_limit").asLong()).isEqualTo(1);

        assertThat(ping(appKey, appSecret, UUID.randomUUID().toString(), Instant.now().getEpochSecond())
                .getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> exceeded = ping(appKey, appSecret, UUID.randomUUID().toString(),
                Instant.now().getEpochSecond());
        assertThat(exceeded.getStatusCode().value()).isEqualTo(429);
        assertThat(read(exceeded.getBody()).get("code").asText()).isEqualTo("0009003");

        JsonNode portal = read(get("/openapi/portal/apps", TENANT_A, PORTAL_PERMISSION).getBody()).get("data");
        assertThat(portal).hasSize(1);
        assertThat(portal.get(0).get("app_id").asLong()).isEqualTo(appId);
        assertThat(portal.get(0).get("quota_daily").asLong()).isEqualTo(1);
        assertThat(portal.get(0).get("call_today").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode logs = read(get("/api/hub/v1/admin/openapi/call-logs?appId=" + appId + "&status=429",
                TENANT_A, CALL_LOG_PERMISSION).getBody()).get("data");
        assertThat(logs.get("total").asLong()).isEqualTo(1);
        assertThat(logs.get("records").get(0).get("http_status").asInt()).isEqualTo(429);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "OPENAPI_QUOTA_UPDATED".equals(event.eventType()));
    }

    @Test
    void portalListIsTenantScoped() {
        approvedApp(TENANT_A, "门户应用");

        assertThat(read(get("/openapi/portal/apps", TENANT_A, PORTAL_PERMISSION).getBody()).get("data"))
                .hasSize(1);
        assertThat(read(get("/openapi/portal/apps", TENANT_B, PORTAL_PERMISSION).getBody()).get("data"))
                .isEmpty();
        assertThat(get("/openapi/portal/apps", TENANT_A, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void listsAdminAppsWithStatusFilters() {
        long approvedId = approvedApp(TENANT_A, "列表应用");
        createApp(TENANT_A, REVIEW_PERMISSION, "待审应用");

        JsonNode all = read(get("/api/hub/v1/admin/openapi/apps", TENANT_A, REVIEW_PERMISSION)
                .getBody()).get("data");
        assertThat(all.get("total").asLong()).isEqualTo(2);

        JsonNode pending = read(get("/api/hub/v1/admin/openapi/apps?reviewStatus=PENDING",
                TENANT_A, REVIEW_PERMISSION).getBody()).get("data");
        assertThat(pending.get("total").asLong()).isEqualTo(1);
        assertThat(pending.get("records").get(0).get("name").asText()).isEqualTo("待审应用");

        JsonNode enabled = read(get("/api/hub/v1/admin/openapi/apps?status=ENABLED",
                TENANT_A, REVIEW_PERMISSION).getBody()).get("data");
        assertThat(enabled.get("total").asLong()).isEqualTo(1);
        assertThat(enabled.get("records").get(0).get("app_id").asLong()).isEqualTo(approvedId);
        assertThat(enabled.get("records").get(0).get("created_at").asText()).isNotBlank();

        assertThat(read(get("/api/hub/v1/admin/openapi/apps", TENANT_B, REVIEW_PERMISSION)
                .getBody()).get("data").get("total").asLong()).isZero();
        assertThat(get("/api/hub/v1/admin/openapi/apps", TENANT_A, null)
                .getStatusCode().value()).isEqualTo(403);
    }

    // ---------- helpers ----------

    private long approvedApp(long tenantId, String name) {
        long appId = read(createApp(tenantId, REVIEW_PERMISSION, name).getBody()).get("data").get("app_id").asLong();
        assertThat(review(tenantId, appId, "APPROVE").getStatusCode().value()).isEqualTo(200);
        return appId;
    }

    private ResponseEntity<String> createApp(long tenantId, String permission, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("owner_type", "ISV");
        body.put("scopes", List.of("crm.lead:read"));
        body.put("redirect_uris", List.of("https://partner.example.com/cb"));
        return restTemplate.exchange(url("/api/hub/v1/admin/openapi/apps"), HttpMethod.POST,
                new HttpEntity<>(body, headers(tenantId, permission)), String.class);
    }

    private ResponseEntity<String> review(long tenantId, long appId, String action) {
        return restTemplate.exchange(url("/api/hub/v1/admin/openapi/apps/" + appId + ":review"), HttpMethod.POST,
                new HttpEntity<>(Map.of("action", action, "reason", "test"), headers(tenantId, REVIEW_PERMISSION)),
                String.class);
    }

    private ResponseEntity<String> issueKey(long tenantId, long appId, String action, int graceHours) {
        return restTemplate.exchange(url("/api/hub/v1/admin/openapi/apps/" + appId + "/keys"), HttpMethod.POST,
                new HttpEntity<>(Map.of("action", action, "grace_hours", graceHours),
                        headers(tenantId, KEY_PERMISSION)), String.class);
    }

    private ResponseEntity<String> ping(String appKey, String appSecret, String nonce, long timestamp) {
        return signedGet(appKey, appSecret, nonce, timestamp, null);
    }

    private ResponseEntity<String> signedGet(String appKey, String appSecret, String nonce, long timestamp,
                                             String signatureOverride) {
        String signature = signatureOverride != null
                ? signatureOverride
                : sign(appSecret, "GET", PING_PATH, timestamp, nonce, new byte[0]);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-App-Key", appKey);
        headers.set("X-Timestamp", String.valueOf(timestamp));
        headers.set("X-Nonce", nonce);
        headers.set("X-Signature", signature);
        return restTemplate.exchange(url(PING_PATH), HttpMethod.GET, new HttpEntity<>(headers), String.class);
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

    private String sign(String secret, String method, String path, long timestamp, String nonce, byte[] body) {
        try {
            String bodyHash = HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(body));
            String payload = method + path + timestamp + nonce + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, ex);
        }
    }
}
