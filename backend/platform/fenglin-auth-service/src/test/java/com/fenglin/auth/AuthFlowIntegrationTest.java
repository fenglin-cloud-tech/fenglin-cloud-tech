package com.fenglin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.auth.service.PasswordPolicy;
import com.fenglin.common.core.BizException;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.nimbusds.jwt.SignedJWT;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 认证链路集成测试：内嵌真实 PostgreSQL 15 + 完整 Flyway 迁移 + 应用以 fl_app 角色执行（RLS 生效）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest {

    private static final String TENANT_CODE = "fenglin";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Fenglin@2026Dev";
    private static final String TENANT_CODE_B = "acme";
    private static final String PASSWORD_B = "Acme@2026Secret";

    private static final EmbeddedPostgresSupport POSTGRES;
    private static final String JDBC_URL;

    static {
        POSTGRES = EmbeddedPostgresSupport.start();
        JDBC_URL = POSTGRES.jdbcUrl();
        Flyway.configure()
                .dataSource(POSTGRES.dataSource())
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
        registry.add("fenglin.auth.issuer", () -> "https://hub.test");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void resetData() throws Exception {
        // JDK 默认 HttpURLConnection 遇到 401 会尝试认证重试，改为 JDK HttpClient 以便断言 401/423 响应体
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE fenglin_hub.hub_auth_revocation, fenglin_hub.hub_auth_token,
                             fenglin_hub.hub_login_session, fenglin_hub.hub_user_role,
                             fenglin_hub.hub_role_permission, fenglin_hub.hub_user,
                             fenglin_hub.hub_account, fenglin_hub.hub_role,
                             fenglin_hub.hub_permission, fenglin_hub.hub_tenant
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_tenant (id, code, name, status, hash_salt_enc, deleted)
                    VALUES (1001, 'fenglin', '丰麟云创', 'ACTIVE', 'v1:test-salt', 0),
                           (1002, 'acme', 'Acme 租户', 'ACTIVE', 'v1:test-salt-b', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_user (id, tenant_id, employee_no, username, real_name, status, deleted)
                    VALUES (2001, 1001, 'FL0001', 'admin', '系统管理员', 'ACTIVE', 0),
                           (2002, 1002, 'AC0001', 'admin', 'Acme 管理员', 'ACTIVE', 0)
                    """);
            String hashA = passwordPolicy.encode(PASSWORD);
            String hashB = passwordPolicy.encode(PASSWORD_B);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_account (id, tenant_id, user_id, password_hash, deleted)
                    VALUES (3001, 1001, 2001, '%s', 0), (3002, 1002, 2002, '%s', 0)
                    """.formatted(hashA, hashB));
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_role (id, tenant_id, code, name, role_type, data_scope, status, deleted)
                    VALUES (4001, 1001, 'TENANT_ADMIN', '租户管理员', 'SYS', 'ALL', 'ENABLED', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, deleted)
                    VALUES (5001, 'hub:iam:user:read', '用户查看', 'API', 'iam', 0),
                           (5002, 'hub:audit:read', '审计查看', 'API', 'audit', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_role_permission (id, tenant_id, role_id, permission_code, deleted)
                    VALUES (6001, 1001, 4001, 'hub:iam:user:read', 0),
                           (6002, 1001, 4001, 'hub:audit:read', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_user_role (id, tenant_id, user_id, role_id, deleted)
                    VALUES (7001, 1001, 2001, 4001, 0)
                    """);
        }
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void discoveryAndJwksArePublished() throws Exception {
        JsonNode discovery = read(restTemplate.getForEntity(url("/.well-known/openid-configuration"), String.class));
        assertThat(discovery.get("issuer").asText()).isEqualTo("https://hub.test");
        assertThat(discovery.get("jwks_uri").asText()).contains("/.well-known/jwks.json");
        assertThat(discovery.get("id_token_signing_alg_values_supported").get(0).asText()).isEqualTo("RS256");

        JsonNode jwks = read(restTemplate.getForEntity(url("/.well-known/jwks.json"), String.class));
        assertThat(jwks.get("keys")).hasSize(1);
        assertThat(jwks.get("keys").get(0).get("kid").asText()).isEqualTo("hub-dev-1");
    }

    @Test
    void loginRefreshUserinfoAndLogoutFlow() throws Exception {
        HttpResult login = login(TENANT_CODE, USERNAME, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().get("code").asText()).isEqualTo("0");
        JsonNode tokens = login.body().get("data");
        String accessToken = tokens.get("access_token").asText();
        String refreshToken = tokens.get("refresh_token").asText();
        String idToken = tokens.get("id_token").asText();
        String sessionId = tokens.get("session_id").asText();
        assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expires_in").asLong()).isEqualTo(7200);

        var jwtClaims = SignedJWT.parse(accessToken).getJWTClaimsSet();
        assertThat(jwtClaims.getLongClaim("tenant_id")).isEqualTo(1001L);
        assertThat(jwtClaims.getStringClaim("tenant_code")).isEqualTo(TENANT_CODE);
        assertThat(jwtClaims.getStringClaim("token_use")).isEqualTo("access");

        HttpResult userinfo = get("/api/hub/v1/sso/userinfo", Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
        assertThat(userinfo.status()).isEqualTo(200);
        JsonNode info = userinfo.body().get("data");
        assertThat(info.get("username").asText()).isEqualTo(USERNAME);
        assertThat(info.get("tenant_code").asText()).isEqualTo(TENANT_CODE);
        assertThat(info.get("roles")).hasSize(1);
        assertThat(info.get("roles").get(0).asText()).isEqualTo("TENANT_ADMIN");
        assertThat(info.get("permissions")).hasSize(2);
        assertThat(info.get("permissions").get(0).asText()).isEqualTo("hub:audit:read");

        // 刷新：旧 refresh token 旋转后立即失效
        HttpResult refreshed = post("/api/hub/v1/sso/token:refresh", Map.of("refresh_token", refreshToken));
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.body().get("data").get("access_token").asText()).isNotEqualTo(accessToken);
        String refreshedIdToken = refreshed.body().get("data").get("id_token").asText();

        // 登出：会话置 LOGOUT，refresh 全撤销
        HttpResult logout = post("/api/hub/v1/sso/logout",
                Map.of("id_token_hint", refreshedIdToken, "all_sessions", false));
        assertThat(logout.status()).isEqualTo(200);
        assertThat(logout.body().get("data").get("updated")).hasSize(1);

        HttpResult status = get("/internal/v1/auth/sessions/" + sessionId + "/status",
                Map.of("X-Tenant-Id", "1001"));
        assertThat(status.body().get("data").get("online").asBoolean()).isFalse();

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT count(*) FROM fenglin_hub.hub_login_session
                     WHERE session_id = '%s' AND status = 'LOGOUT'
                    """.formatted(sessionId));
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void refreshTokenReuseTerminatesSession() throws Exception {
        JsonNode tokens = login(TENANT_CODE, USERNAME, PASSWORD).body().get("data");
        String refreshToken = tokens.get("refresh_token").asText();
        String sessionId = tokens.get("session_id").asText();

        assertThat(post("/api/hub/v1/sso/token:refresh",
                Map.of("refresh_token", refreshToken)).status()).isEqualTo(200);
        HttpResult reuse = post("/api/hub/v1/sso/token:refresh", Map.of("refresh_token", refreshToken));
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.body().get("code").asText()).isEqualTo("0000001");

        HttpResult status = get("/internal/v1/auth/sessions/" + sessionId + "/status",
                Map.of("X-Tenant-Id", "1001"));
        assertThat(status.body().get("data").get("online").asBoolean()).isFalse();
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT status FROM fenglin_hub.hub_login_session WHERE session_id = '%s'
                    """.formatted(sessionId));
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("KICKED");
        }
    }

    @Test
    void revokedAccessTokenIsRejectedByUserinfoAndInternalCheck() throws Exception {
        String accessToken = login(TENANT_CODE, USERNAME, PASSWORD).body().get("data").get("access_token").asText();
        String jti = SignedJWT.parse(accessToken).getJWTClaimsSet().getJWTID();

        HttpResult revoked = post("/api/hub/v1/sso/token:revoke", Map.of("token", accessToken));
        assertThat(revoked.status()).isEqualTo(200);
        assertThat(revoked.body().get("data").get("revoked").asBoolean()).isTrue();

        HttpResult userinfo = get("/api/hub/v1/sso/userinfo", Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
        assertThat(userinfo.status()).isEqualTo(401);

        HttpResult check = get("/internal/v1/auth/revocations/" + jti, Map.of("X-Tenant-Id", "1001"));
        assertThat(check.body().get("data").get("revoked").asBoolean()).isTrue();
    }

    @Test
    void accountIsLockedAfterFiveFailures() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpResult failed = login(TENANT_CODE, USERNAME, "Wrong@Password1");
            assertThat(failed.status()).isEqualTo(401);
            assertThat(failed.body().get("code").asText()).isEqualTo("0001001");
        }
        HttpResult fifth = login(TENANT_CODE, USERNAME, "Wrong@Password1");
        assertThat(fifth.status()).isEqualTo(423);
        assertThat(fifth.body().get("code").asText()).isEqualTo("0001002");

        HttpResult correctWhileLocked = login(TENANT_CODE, USERNAME, PASSWORD);
        assertThat(correctWhileLocked.status()).isEqualTo(423);

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT fail_count, locked_until FROM fenglin_hub.hub_account
                     WHERE tenant_id = 1001 AND user_id = 2001
                    """);
            rs.next();
            assertThat(rs.getInt("fail_count")).isEqualTo(5);
            assertThat(rs.getTimestamp("locked_until")).isNotNull();
        }
    }

    @Test
    void passwordPolicyEnforcesStrengthAndBcryptCost() {
        assertThatThrownBy(() -> passwordPolicy.validate("Short1!"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("alllowercase123!"))
                .isInstanceOf(BizException.class);
        String hash = passwordPolicy.encode(PASSWORD);
        assertThat(hash).startsWith("$2a$12$");
        assertThat(passwordPolicy.matches(PASSWORD, hash)).isTrue();
        assertThat(passwordPolicy.matches("Wrong@Password1", hash)).isFalse();
    }

    @Test
    void tenantIsolationHoldsForAppRole() throws Exception {
        // 同一用户名在不同租户下互不可见
        HttpResult wrongTenant = login(TENANT_CODE_B, USERNAME, PASSWORD);
        assertThat(wrongTenant.status()).isEqualTo(401);
        assertThat(wrongTenant.body().get("code").asText()).isEqualTo("0001001");

        HttpResult tenantB = login(TENANT_CODE_B, USERNAME, PASSWORD_B);
        assertThat(tenantB.status()).isEqualTo(200);
        String accessB = tenantB.body().get("data").get("access_token").asText();
        assertThat(SignedJWT.parse(accessB).getJWTClaimsSet().getLongClaim("tenant_id")).isEqualTo(1002L);

        // 以 fl_app（非属主）执行：无租户上下文查询到 0 行，SET LOCAL 后只看到本租户
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_app");
            assertThat(countUsersWithoutTenant(statement)).isZero();
            statement.execute("BEGIN");
            statement.execute("SELECT set_config('app.tenant_id', '1001', true)");
            assertThat(queryVisibleUsers(statement)).isEqualTo(1);
            statement.execute("COMMIT");
            statement.execute("BEGIN");
            statement.execute("SELECT set_config('app.tenant_id', '1002', true)");
            assertThat(queryVisibleUsers(statement)).isEqualTo(1);
            statement.execute("COMMIT");
        }
    }

    private int countUsersWithoutTenant(Statement statement) throws Exception {
        ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_user");
        rs.next();
        return rs.getInt(1);
    }

    private int queryVisibleUsers(Statement statement) throws Exception {
        ResultSet rs = statement.executeQuery("SELECT count(*) FROM fenglin_hub.hub_user");
        rs.next();
        return rs.getInt(1);
    }

    private HttpResult login(String tenantCode, String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("tenant_code", tenantCode);
        body.put("username", username);
        body.put("password", password);
        body.put("client_type", "ADMIN");
        return post("/api/hub/v1/sso/login", body);
    }

    private HttpResult post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, Map.of());
    }

    private HttpResult get(String path, Map<String, String> headers) {
        return exchange(HttpMethod.GET, path, null, headers);
    }

    private HttpResult exchange(HttpMethod method, String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        ResponseEntity<String> response = restTemplate.exchange(url(path), method,
                new HttpEntity<>(body, httpHeaders), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response));
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + response.getBody(), e);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
