package com.fenglin.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.iam.client.AuthSessionClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * IAM 用户/角色/权限/菜单集成测试：内嵌真实 PostgreSQL + 完整迁移，应用以 fl_app 执行（RLS 生效）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamFlowIntegrationTest {

    private static final long TENANT_A = 1101L;
    private static final long TENANT_B = 1102L;
    private static final long ROLE_ADMIN_A = 2101L;
    private static final long USER_ADMIN_A = 2001L;

    private static final String CREATE_USER_PERMISSION = "tenant:iam:user:create";
    private static final String MANAGE_ROLE_PERMISSION = "tenant:iam:role:manage";
    private static final String ASSIGN_ROLE_PERMISSION = "tenant:iam:role:assign";
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

    @MockBean
    private AuthSessionClient authSessionClient;

    @MockBean
    private AuditSink auditSink;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE fenglin_hub.hub_user_role, fenglin_hub.hub_role_permission,
                             fenglin_hub.hub_user, fenglin_hub.hub_account,
                             fenglin_hub.hub_role, fenglin_hub.hub_tenant
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_tenant (id, code, name, status, hash_salt_enc, deleted)
                    VALUES (1101, 'fenglin', '丰麟云创', 'ACTIVE', 'v1:test-salt', 0),
                           (1102, 'acme', 'Acme 租户', 'ACTIVE', 'v1:test-salt-b', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_role (id, tenant_id, code, name, role_type, data_scope, status, deleted)
                    VALUES (2101, 1101, 'TENANT_ADMIN', '租户管理员', 'SYS', 'ALL', 'ENABLED', 0),
                           (2301, 1102, 'TENANT_ADMIN', '租户管理员', 'SYS', 'ALL', 'ENABLED', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_user (id, tenant_id, employee_no, username, real_name, status, deleted)
                    VALUES (2001, 1101, 'FL0001', 'admin', '系统管理员', 'ACTIVE', 0),
                           (2002, 1102, 'AC0001', 'bob', 'Acme 管理员', 'ACTIVE', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_account (id, tenant_id, user_id, password_hash, deleted)
                    VALUES (3001, 1101, 2001, '%s', 0), (3002, 1102, 2002, '%s', 0)
                    """.formatted(PasswordPolicy.encode("Fenglin@2026Dev"), PasswordPolicy.encode("Acme@2026Secret")));
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_user_role (id, tenant_id, user_id, role_id, deleted)
                    VALUES (2201, 1101, 2001, 2101, 0), (2401, 1102, 2002, 2301, 0)
                    """);
            statement.execute("""
                    UPDATE fenglin_hub.hub_account
                       SET fail_count = 3, locked_until = now() + interval '10 minutes'
                     WHERE tenant_id = 1101 AND user_id = 2001
                    """);
        }
    }

    @Test
    void createUserProvisionAccountAndRoles() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("real_name", "爱丽丝");
        body.put("username", "alice");
        body.put("mobile", "13800000000");
        body.put("email", "alice@example.com");
        body.put("primary_org_id", 20);
        body.put("role_ids", List.of(ROLE_ADMIN_A));

        HttpResult created = post("/api/hub/v1/admin/users", body,
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A), "X-Permissions", CREATE_USER_PERMISSION));
        assertThat(created.status()).isEqualTo(200);
        JsonNode data = created.body().get("data");
        long userId = data.get("user_id").asLong();
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("employee_no").asText()).startsWith("GLIN");
        assertThat(data.get("initial_password").asText()).hasSize(16);

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet user = statement.executeQuery("""
                    SELECT username, primary_org_id, status FROM fenglin_hub.hub_user
                     WHERE tenant_id = 1101 AND id = %d
                    """.formatted(userId));
            assertThat(user.next()).isTrue();
            assertThat(user.getString("username")).isEqualTo("alice");
            assertThat(user.getLong("primary_org_id")).isEqualTo(20);
            assertThat(user.getString("status")).isEqualTo("ACTIVE");

            ResultSet account = statement.executeQuery("""
                    SELECT password_hash FROM fenglin_hub.hub_account
                     WHERE tenant_id = 1101 AND user_id = %d
                    """.formatted(userId));
            assertThat(account.next()).isTrue();
            assertThat(account.getString("password_hash")).startsWith("$2a$12$");

            ResultSet roles = statement.executeQuery("""
                    SELECT count(*) FROM fenglin_hub.hub_user_role
                     WHERE tenant_id = 1101 AND user_id = %d AND role_id = 2101
                    """.formatted(userId));
            assertThat(roles.next()).isTrue();
            assertThat(roles.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void createUserRequiresPermissionFromGateway() {
        HttpResult forbidden = post("/api/hub/v1/admin/users",
                Map.of("real_name", "x", "username", "x", "mobile", "13800000000", "primary_org_id", 20),
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().get("code").asText()).isEqualTo("FL-COMMON-0403");
    }

    @Test
    void createRoleGrantPermissionsAndAssignToUser() {
        HttpHeaders headers = headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                "X-Permissions", MANAGE_ROLE_PERMISSION));

        Map<String, Object> roleBody = new HashMap<>();
        roleBody.put("code", "sales_mgr");
        roleBody.put("name", "销售经理");
        roleBody.put("role_type", "BIZ");
        roleBody.put("data_scope", "DEPT_AND_CHILD");
        HttpResult role = exchange(HttpMethod.POST, "/api/hub/v1/admin/roles", roleBody, headers);
        assertThat(role.status()).isEqualTo(200);
        long roleId = role.body().get("data").get("role_id").asLong();

        HttpResult granted = exchange(HttpMethod.PUT, "/api/hub/v1/admin/roles/" + roleId + "/permissions",
                Map.of("permission_codes", List.of(CREATE_USER_PERMISSION, "hub:audit:read")), headers);
        assertThat(granted.status()).isEqualTo(200);
        assertThat(granted.body().get("data").get("permission_codes")).hasSize(2);

        HttpResult assigned = exchange(HttpMethod.PUT,
                "/api/hub/v1/admin/users/" + USER_ADMIN_A + "/roles",
                Map.of("items", List.of(Map.of("role_id", roleId))),
                headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                        "X-Permissions", ASSIGN_ROLE_PERMISSION)));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().get("data").get("roles").toString()).contains("sales_mgr");
        // PLAT-10：分配角色递增权限版本
        assertThat(permissionVersionOf(USER_ADMIN_A)).isEqualTo(1L);

        HttpResult unknown = exchange(HttpMethod.PUT, "/api/hub/v1/admin/roles/" + roleId + "/permissions",
                Map.of("permission_codes", List.of("not:defined:code")), headers);
        assertThat(unknown.status()).isEqualTo(400);

        // PLAT-10：角色权限点变更递增该角色下用户的权限版本（失败的写入不递增）
        HttpResult regranted = exchange(HttpMethod.PUT, "/api/hub/v1/admin/roles/" + roleId + "/permissions",
                Map.of("permission_codes", List.of(CREATE_USER_PERMISSION)), headers);
        assertThat(regranted.status()).isEqualTo(200);
        assertThat(permissionVersionOf(USER_ADMIN_A)).isEqualTo(2L);

        HttpResult list = exchange(HttpMethod.GET, "/api/hub/v1/admin/roles", null,
                headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A))));
        assertThat(list.body().get("data").get("records").toString()).contains("sales_mgr");
    }

    @Test
    void grantPermissionsRejectsMissingOrUnknownFieldAndOnlyClearsOnExplicitEmptyArray() {
        HttpHeaders headers = headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                "X-Permissions", MANAGE_ROLE_PERMISSION));
        String path = "/api/hub/v1/admin/roles/" + ROLE_ADMIN_A + "/permissions";

        HttpResult seeded = exchange(HttpMethod.PUT, path,
                Map.of("permission_codes", List.of(CREATE_USER_PERMISSION, "hub:audit:read")), headers);
        assertThat(seeded.status()).isEqualTo(200);
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isEqualTo(2);

        // PLAT-25：驼峰字段被当成未知字段，400 且权限数不变（修复前会被静默清空）
        HttpResult camel = exchange(HttpMethod.PUT, path,
                Map.of("permissionCodes", List.of(CREATE_USER_PERMISSION)), headers);
        assertThat(camel.status()).isEqualTo(400);
        assertThat(camel.body().get("code").asText()).isEqualTo("FL-COMMON-0400");
        assertThat(camel.body().get("message").asText()).contains("permissionCodes");
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isEqualTo(2);

        // 缺少权限字段：400 并点名 permission_codes，权限数不变
        HttpResult missing = exchange(HttpMethod.PUT, path, Map.of(), headers);
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("message").asText()).contains("permission_codes");
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isEqualTo(2);

        // 显式 null 等同缺失，不按空数组处理
        HttpResult nullField = exchange(HttpMethod.PUT, path,
                java.util.Collections.singletonMap("permission_codes", null), headers);
        assertThat(nullField.status()).isEqualTo(400);
        assertThat(nullField.body().get("message").asText()).contains("permission_codes");
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isEqualTo(2);

        // 显式空数组才允许清空
        HttpResult empty = exchange(HttpMethod.PUT, path, Map.of("permission_codes", List.of()), headers);
        assertThat(empty.status()).isEqualTo(200);
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isZero();

        // 清空后可重新写入
        HttpResult restored = exchange(HttpMethod.PUT, path,
                Map.of("permission_codes", List.of(CREATE_USER_PERMISSION)), headers);
        assertThat(restored.status()).isEqualTo(200);
        assertThat(rolePermissionCount(ROLE_ADMIN_A)).isEqualTo(1);
    }

    @Test
    void roleGrantAuditCarriesBeforeAfterCountsAndCodesHash() {
        HttpHeaders headers = headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                "X-Permissions", MANAGE_ROLE_PERMISSION));
        String path = "/api/hub/v1/admin/roles/" + ROLE_ADMIN_A + "/permissions";

        HttpResult granted = exchange(HttpMethod.PUT, path,
                Map.of("permission_codes", List.of(CREATE_USER_PERMISSION, "hub:audit:read")), headers);
        assertThat(granted.status()).isEqualTo(200);
        HttpResult cleared = exchange(HttpMethod.PUT, path, Map.of("permission_codes", List.of()), headers);
        assertThat(cleared.status()).isEqualTo(200);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditSink, times(2)).publish(captor.capture());
        List<AuditEvent> grants = captor.getAllValues().stream()
                .filter(event -> "IAM_ROLE_PERMISSIONS_GRANTED".equals(event.eventType()))
                .toList();
        assertThat(grants).hasSize(2);

        Map<String, Object> first = grants.get(0).detail();
        assertThat(first).containsEntry("before_count", 0).containsEntry("after_count", 2);
        assertThat((String) first.get("after_codes_hash")).startsWith("sha256:").hasSize(71);
        assertThat(first.toString()).doesNotContain(CREATE_USER_PERMISSION);

        Map<String, Object> second = grants.get(1).detail();
        assertThat(second).containsEntry("before_count", 2).containsEntry("after_count", 0);
        assertThat(second.get("after_codes_hash")).isNotEqualTo(first.get("after_codes_hash"));
    }

    @Test
    void disablingUserBumpsPermissionVersion() {
        HttpResult updated = exchange(HttpMethod.PUT, "/api/hub/v1/admin/users/" + USER_ADMIN_A,
                Map.of("status", "DISABLED"),
                headers(Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                        "X-Permissions", "tenant:iam:user:update")));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(permissionVersionOf(USER_ADMIN_A)).isEqualTo(1L);
    }

    @Test
    void userListIsTenantScopedAndCarriesRoles() {
        HttpResult tenantA = get("/api/hub/v1/admin/users", Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(tenantA.status()).isEqualTo(200);
        assertThat(tenantA.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(tenantA.body().get("data").get("records").get(0).get("username").asText()).isEqualTo("admin");
        assertThat(tenantA.body().get("data").get("records").get(0).get("roles").get(0).asText())
                .isEqualTo("TENANT_ADMIN");

        HttpResult tenantB = get("/api/hub/v1/admin/users", Map.of("X-Tenant-Id", String.valueOf(TENANT_B)));
        assertThat(tenantB.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(tenantB.body().get("data").get("records").get(0).get("username").asText()).isEqualTo("bob");

        HttpResult keyword = get("/api/hub/v1/admin/users?keyword=nomatch",
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(keyword.body().get("data").get("total").asLong()).isZero();
    }

    @Test
    void menusAreFilteredByGrantedPermissions() {
        HttpResult filtered = get("/api/hub/v1/admin/menus?clientType=ADMIN",
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A), "X-Permissions", "hub:iam:user:read"));
        assertThat(filtered.status()).isEqualTo(200);
        String codes = filtered.body().get("data").toString();
        assertThat(codes).contains("hub.iam.user");
        assertThat(codes).doesNotContain("hub.gateway.route");

        HttpResult wildcard = get("/api/hub/v1/admin/menus?clientType=ADMIN",
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A), "X-Permissions", "*"));
        assertThat(wildcard.body().get("data")).hasSize(5);
    }

    @Test
    void permissionTreeSupportsDomainFilter() {
        HttpResult tree = get("/api/hub/v1/admin/permissions/tree?domain=iam",
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(tree.status()).isEqualTo(200);
        assertThat(tree.body().get("data").get("nodes").size()).isGreaterThan(0);
        assertThat(tree.body().get("data").get("nodes").toString()).doesNotContain("platform:tenant:manage");
    }

    @Test
    void internalUserLookupReturnsTenantUser() {
        HttpResult response = get("/internal/v1/iam/users/" + USER_ADMIN_A,
                Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("data").get("username").asText()).isEqualTo("admin");
        assertThat(response.body().get("data").get("employee_no").asText()).isEqualTo("FL0001");
    }

    @Test
    void appRoleCannotReadUsersWithoutTenantContext() throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_app");
            ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_user");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void resetPasswordGeneratesStrongPasswordAndClearsLockState() throws Exception {
        HttpResult result = post("/api/hub/v1/admin/users/" + USER_ADMIN_A + "/reset-password",
                Map.of(), resetPasswordHeaders());
        assertThat(result.status()).isEqualTo(200);
        String generated = result.body().get("data").get("new_password").asText();
        assertThat(generated).hasSize(16);
        Map<String, Object> account = accountOf(USER_ADMIN_A);
        assertThat(PasswordPolicy.matches(generated, (String) account.get("password_hash"))).isTrue();
        assertThat(account.get("fail_count")).isEqualTo(0);
        assertThat(account.get("locked_until")).isNull();
        assertThat(permissionVersionOf(USER_ADMIN_A)).isZero();
        verify(authSessionClient).revokeAllSessions(TENANT_A, USER_ADMIN_A);
    }

    @Test
    void resetPasswordAcceptsExplicitStrongPasswordWithoutEcho() throws Exception {
        HttpResult result = post("/api/hub/v1/admin/users/" + USER_ADMIN_A + "/reset-password",
                Map.of("password", "Reset@2026Strong"), resetPasswordHeaders());
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("data").has("new_password")).isFalse();
        assertThat(PasswordPolicy.matches("Reset@2026Strong",
                (String) accountOf(USER_ADMIN_A).get("password_hash"))).isTrue();
        verify(authSessionClient).revokeAllSessions(TENANT_A, USER_ADMIN_A);
    }

    @Test
    void resetPasswordRejectsWeakPassword() throws Exception {
        HttpResult result = post("/api/hub/v1/admin/users/" + USER_ADMIN_A + "/reset-password",
                Map.of("password", "short"), resetPasswordHeaders());
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().get("code").asText()).isEqualTo("FL-COMMON-0400");
        assertThat(PasswordPolicy.matches("Fenglin@2026Dev",
                (String) accountOf(USER_ADMIN_A).get("password_hash"))).isTrue();
        verify(authSessionClient, never()).revokeAllSessions(anyLong(), anyLong());
    }

    @Test
    void resetPasswordRequiresUpdatePermission() {
        HttpResult result = post("/api/hub/v1/admin/users/" + USER_ADMIN_A + "/reset-password",
                Map.of(), Map.of("X-Tenant-Id", String.valueOf(TENANT_A)));
        assertThat(result.status()).isEqualTo(403);
        verify(authSessionClient, never()).revokeAllSessions(anyLong(), anyLong());
    }

    @Test
    void resetPasswordReturnsNotFoundForOtherTenantOrUnknownUser() {
        HttpResult crossTenant = post("/api/hub/v1/admin/users/2002/reset-password",
                Map.of(), resetPasswordHeaders());
        assertThat(crossTenant.status()).isEqualTo(404);
        HttpResult unknown = post("/api/hub/v1/admin/users/9999/reset-password",
                Map.of(), resetPasswordHeaders());
        assertThat(unknown.status()).isEqualTo(404);
        verify(authSessionClient, never()).revokeAllSessions(anyLong(), anyLong());
    }

    private Map<String, String> resetPasswordHeaders() {
        return Map.of("X-Tenant-Id", String.valueOf(TENANT_A),
                "X-Permissions", "tenant:iam:user:update");
    }

    private Map<String, Object> accountOf(long userId) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT password_hash, fail_count, locked_until "
                     + "FROM fenglin_hub.hub_account WHERE user_id = " + userId)) {
            assertThat(rs.next()).isTrue();
            Map<String, Object> account = new HashMap<>();
            account.put("password_hash", rs.getString(1));
            account.put("fail_count", rs.getInt(2));
            account.put("locked_until", rs.getTimestamp(3));
            return account;
        }
    }

    private HttpHeaders headers(Map<String, String> values) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        values.forEach(headers::set);
        return headers;
    }

    private HttpResult post(String path, Object body, Map<String, String> headers) {
        return exchange(HttpMethod.POST, path, body, headers(headers));
    }

    private HttpResult get(String path, Map<String, String> headers) {
        return exchange(HttpMethod.GET, path, null, headers(headers));
    }

    private HttpResult exchange(HttpMethod method, String path, Object body, HttpHeaders headers) {
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private long permissionVersionOf(long userId) {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT permission_version FROM fenglin_hub.hub_user WHERE id = " + userId)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long rolePermissionCount(long roleId) {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM fenglin_hub.hub_role_permission WHERE role_id = " + roleId
                             + " AND deleted = 0")) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
