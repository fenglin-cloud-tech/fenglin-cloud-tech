package com.fenglin.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.tenant.service.FinanceProvisioner;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户开通/状态流转/上下文贯穿集成测试：内嵌真实 PostgreSQL + 完整迁移，应用以 fl_app 角色执行（RLS 生效）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantFlowIntegrationTest {

    private static final String WRITE_PERMISSION = "platform:tenant:manage";
    private static final String JDBC_URL;
    private static final List<Long> FINANCE_PROVISIONED = new CopyOnWriteArrayList<>();

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

    /** FIN-15 财务域初始化走 HTTP 调用，测试内不启动财务服务，用替身隔离并记录调用。 */
    @TestConfiguration
    static class TestDoublesConfig {

        @Bean
        @Primary
        FinanceProvisioner recordingFinanceProvisioner() {
            return new FinanceProvisioner("http://localhost:1", "test") {
                @Override
                public void initialize(long tenantId, String tenantName) {
                    FINANCE_PROVISIONED.add(tenantId);
                }
            };
        }
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
        FINANCE_PROVISIONED.clear();
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE fenglin_hub.hub_subscription, fenglin_hub.hub_user_role,
                             fenglin_hub.hub_role_permission, fenglin_hub.hub_user,
                             fenglin_hub.hub_account, fenglin_hub.hub_role, fenglin_hub.hub_tenant,
                             fenglin_hub.hub_package_item, fenglin_hub.hub_package
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_package (id, code, name, status)
                    VALUES (9101, 'TRIAL', '试用套餐', 'ACTIVE')
                    """);
        }
    }

    @Test
    void createTenantProvisionsAdminRoleAndSubscription() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "云测试租户");
        body.put("package_code", "TRIAL");
        body.put("trial_days", 14);
        body.put("admin_username", "owner");
        body.put("contact_name", "联系人");

        HttpResult created = post("/api/hub/v1/admin/tenants", body, Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(created.status()).isEqualTo(200);
        JsonNode data = created.body().get("data");
        long tenantId = data.get("tenant_id").asLong();
        String tenantCode = data.get("code").asText();
        assertThat(tenantCode).startsWith("T");
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("initial_password").asText()).hasSize(16);
        assertThat(FINANCE_PROVISIONED).containsExactly(tenantId);

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet tenant = statement.executeQuery(
                    "SELECT code, status, expire_at FROM fenglin_hub.hub_tenant WHERE id = " + tenantId);
            assertThat(tenant.next()).isTrue();
            assertThat(tenant.getString("status")).isEqualTo("ACTIVE");
            assertThat(tenant.getTimestamp("expire_at")).isNotNull();

            ResultSet user = statement.executeQuery("""
                    SELECT u.username, u.status, a.password_hash, a.pwd_reset_required
                      FROM fenglin_hub.hub_user u
                      JOIN fenglin_hub.hub_account a ON a.tenant_id = u.tenant_id AND a.user_id = u.id
                     WHERE u.tenant_id = %d AND u.username = 'owner'
                    """.formatted(tenantId));
            assertThat(user.next()).isTrue();
            assertThat(user.getString("status")).isEqualTo("ACTIVE");
            assertThat(user.getString("password_hash")).startsWith("$2a$12$");
            assertThat(user.getInt("pwd_reset_required")).isEqualTo(1);

            ResultSet role = statement.executeQuery("""
                    SELECT r.id, r.code, count(rp.permission_code) AS grants
                      FROM fenglin_hub.hub_role r
                      LEFT JOIN fenglin_hub.hub_role_permission rp
                             ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                     WHERE r.tenant_id = %d AND r.code = 'TENANT_ADMIN'
                     GROUP BY r.id, r.code
                    """.formatted(tenantId));
            assertThat(role.next()).isTrue();
            assertThat(role.getInt("grants")).isGreaterThan(0);

            ResultSet subscription = statement.executeQuery(
                    "SELECT trial, status FROM fenglin_hub.hub_subscription WHERE tenant_id = " + tenantId);
            assertThat(subscription.next()).isTrue();
            assertThat(subscription.getInt("trial")).isEqualTo(1);
            assertThat(subscription.getString("status")).isEqualTo("ACTIVE");
        }
    }

    @Test
    void createTenantRequiresPlatformPermission() {
        HttpResult forbidden = post("/api/hub/v1/admin/tenants",
                Map.of("name", "x", "admin_username", "owner"), Map.of());
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().get("code").asText()).isEqualTo("FL-COMMON-0403");
    }

    @Test
    void duplicateTenantCodeConflicts() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "租户一");
        body.put("code", "acme-tenant");
        body.put("admin_username", "owner");
        assertThat(post("/api/hub/v1/admin/tenants", body,
                Map.of("X-Permissions", WRITE_PERMISSION)).status()).isEqualTo(200);

        HttpResult duplicate = post("/api/hub/v1/admin/tenants", body,
                Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("code").asText()).isEqualTo("FL-COMMON-0409");
    }

    @Test
    void statusTransitionsFollowSuspendResumeClose() {
        long tenantId = createTenant("状态租户");

        HttpResult suspended = post("/api/hub/v1/admin/tenants/" + tenantId + "/status",
                Map.of("action", "SUSPEND", "reason", "欠费"), Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(suspended.body().get("data").get("status").asText()).isEqualTo("SUSPENDED");

        HttpResult resumed = post("/api/hub/v1/admin/tenants/" + tenantId + "/status",
                Map.of("action", "RESUME"), Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(resumed.body().get("data").get("status").asText()).isEqualTo("ACTIVE");

        HttpResult closed = post("/api/hub/v1/admin/tenants/" + tenantId + "/status",
                Map.of("action", "CLOSE"), Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(closed.body().get("data").get("status").asText()).isEqualTo("DISABLED");

        HttpResult illegal = post("/api/hub/v1/admin/tenants/" + tenantId + "/status",
                Map.of("action", "SUSPEND"), Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(illegal.status()).isEqualTo(409);
    }

    @Test
    void listAndGetTenant() {
        long tenantId = createTenant("列表租户");

        HttpResult detail = get("/api/hub/v1/admin/tenants/" + tenantId, Map.of());
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().get("data").get("name").asText()).isEqualTo("列表租户");

        HttpResult page = get("/api/hub/v1/admin/tenants?keyword=" + tenantId, Map.of());
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body().get("data").get("total").asLong()).isGreaterThanOrEqualTo(0);

        HttpResult byName = get("/api/hub/v1/admin/tenants?keyword=列表", Map.of());
        assertThat(byName.body().get("data").get("total").asLong()).isEqualTo(1);
    }

    @Test
    void currentTenantComesFromGatewayHeader() {
        long tenantId = createTenant("上下文租户");
        HttpResult current = get("/api/hub/v1/admin/tenants/current", Map.of("X-Tenant-Id", String.valueOf(tenantId)));
        assertThat(current.status()).isEqualTo(200);
        assertThat(current.body().get("data").get("id").asLong()).isEqualTo(tenantId);

        HttpResult missing = get("/api/hub/v1/admin/tenants/current", Map.of());
        assertThat(missing.status()).isEqualTo(400);
    }

    @Test
    void appRoleSeesNoTenantRowsWithoutContext() throws Exception {
        createTenant("隔离租户");
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_app");
            ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void grantAndRevokePlatformAdminClosesLoop() throws Exception {
        long tenantId = createTenant("平台管理员租户");
        long ownerId = userIdOf(tenantId, "owner");
        long secondId = insertUser(tenantId, "ops", 880000000000001001L);
        Map<String, String> headers = Map.of(
                "X-Permissions", WRITE_PERMISSION,
                "X-Tenant-Id", String.valueOf(tenantId));

        HttpResult before = get("/api/hub/v1/admin/platform-admins/" + ownerId, headers);
        assertThat(before.status()).isEqualTo(200);
        assertThat(before.body().get("data").get("platform_admin").asBoolean()).isFalse();

        HttpResult granted = post("/api/hub/v1/admin/platform-admins",
                Map.of("user_id", ownerId), headers);
        assertThat(granted.status()).isEqualTo(200);
        assertThat(granted.body().get("data").get("platform_admin").asBoolean()).isTrue();

        HttpResult afterGrant = get("/api/hub/v1/admin/platform-admins/" + ownerId, headers);
        assertThat(afterGrant.body().get("data").get("platform_admin").asBoolean()).isTrue();

        // 重复授权幂等：仍为 200，库中只有一条有效绑定
        assertThat(post("/api/hub/v1/admin/platform-admins", Map.of("user_id", ownerId), headers)
                .status()).isEqualTo(200);
        assertThat(countUserRole(tenantId, ownerId)).isEqualTo(1);

        // 唯一管理员不可撤销
        HttpResult lastAdmin = delete("/api/hub/v1/admin/platform-admins/" + ownerId, headers);
        assertThat(lastAdmin.status()).isEqualTo(409);

        // 授予第二名后可撤销第一名
        assertThat(post("/api/hub/v1/admin/platform-admins", Map.of("user_id", secondId), headers)
                .status()).isEqualTo(200);
        HttpResult revoked = delete("/api/hub/v1/admin/platform-admins/" + ownerId, headers);
        assertThat(revoked.status()).isEqualTo(200);
        assertThat(revoked.body().get("data").get("platform_admin").asBoolean()).isFalse();

        // 撤销后校验失效，第二名仍有效
        assertThat(get("/api/hub/v1/admin/platform-admins/" + ownerId, headers)
                .body().get("data").get("platform_admin").asBoolean()).isFalse();
        assertThat(get("/api/hub/v1/admin/platform-admins/" + secondId, headers)
                .body().get("data").get("platform_admin").asBoolean()).isTrue();
        assertThat(countUserRole(tenantId, ownerId)).isZero();

        // 只剩第二名时再撤销仍被拒绝
        assertThat(delete("/api/hub/v1/admin/platform-admins/" + secondId, headers).status())
                .isEqualTo(409);
    }

    @Test
    void platformAdminAssignmentRequiresPlatformPermission() throws Exception {
        long tenantId = createTenant("鉴权租户");
        long ownerId = userIdOf(tenantId, "owner");

        HttpResult forbidden = post("/api/hub/v1/admin/platform-admins",
                Map.of("user_id", ownerId), Map.of("X-Tenant-Id", String.valueOf(tenantId)));
        assertThat(forbidden.status()).isEqualTo(403);

        HttpResult forbiddenGet = get("/api/hub/v1/admin/platform-admins/" + ownerId,
                Map.of("X-Tenant-Id", String.valueOf(tenantId)));
        assertThat(forbiddenGet.status()).isEqualTo(403);
    }

    @Test
    void platformAdminGrantRejectsUnknownUser() {
        long tenantId = createTenant("未知用户租户");
        Map<String, String> headers = Map.of(
                "X-Permissions", WRITE_PERMISSION,
                "X-Tenant-Id", String.valueOf(tenantId));
        HttpResult missing = post("/api/hub/v1/admin/platform-admins",
                Map.of("user_id", 880000000000009999L), headers);
        assertThat(missing.status()).isEqualTo(404);
    }

    private long userIdOf(long tenantId, String username) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id FROM fenglin_hub.hub_user WHERE tenant_id = " + tenantId
                             + " AND username = '" + username + "'")) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private long insertUser(long tenantId, String username, long userId) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO fenglin_hub.hub_user
                        (id, tenant_id, uuid, username, real_name, user_type, status,
                         mfa_enabled, created_at, updated_at, deleted)
                    VALUES (%d, %d, gen_random_uuid(), '%s', '%s', 'INTERNAL', 'ACTIVE',
                            0, now(), now(), 0)
                    """.formatted(userId, tenantId, username, username));
        }
        return userId;
    }

    private int countUserRole(long tenantId, long userId) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT count(*) FROM fenglin_hub.hub_user_role ur
                       JOIN fenglin_hub.hub_role r ON r.id = ur.role_id
                      WHERE ur.tenant_id = %d AND ur.user_id = %d
                        AND r.code = 'PLATFORM_ADMIN' AND ur.deleted = 0
                     """.formatted(tenantId, userId))) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private long createTenant(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("admin_username", "owner");
        HttpResult result = post("/api/hub/v1/admin/tenants", body, Map.of("X-Permissions", WRITE_PERMISSION));
        assertThat(result.status()).isEqualTo(200);
        return result.body().get("data").get("tenant_id").asLong();
    }

    private HttpResult post(String path, Object body, Map<String, String> headers) {
        return exchange(HttpMethod.POST, path, body, headers);
    }

    private HttpResult get(String path, Map<String, String> headers) {
        return exchange(HttpMethod.GET, path, null, headers);
    }

    private HttpResult delete(String path, Map<String, String> headers) {
        return exchange(HttpMethod.DELETE, path, null, headers);
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
