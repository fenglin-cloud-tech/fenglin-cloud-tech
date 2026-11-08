package com.fenglin.auth;

import com.fenglin.auth.service.AuthService;
import com.fenglin.auth.web.dto.LoginRequest;
import com.fenglin.auth.web.dto.TokenPayload;
import com.fenglin.common.core.BizException;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3：同一 refresh token 并发刷新，仅一个成功，其余失败但会话不被误杀。
 */
@SpringBootTest
class ConcurrentRefreshIntegrationTest {

    private static final String PASSWORD = "Fenglin@2026Dev";
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
        registry.add("fenglin.auth.issuer", () -> "https://hub.test");
    }

    @Autowired
    private AuthService authService;

    @BeforeEach
    void resetData() throws Exception {
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
                    VALUES (1001, 'fenglin', '丰麟云创', 'ACTIVE', 'v1:test-salt', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_user (id, tenant_id, employee_no, username, real_name, status, deleted)
                    VALUES (2001, 1001, 'FL0001', 'admin', '系统管理员', 'ACTIVE', 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_account (id, tenant_id, user_id, password_hash, deleted)
                    VALUES (3001, 1001, 2001, '%s', 0)
                    """.formatted(PasswordPolicy.encode(PASSWORD)));
        }
    }

    @Test
    void concurrentRefreshSucceedsOnceWithoutKillingSession() throws Exception {
        TokenPayload login = authService.login(new LoginRequest("fenglin", "admin", PASSWORD, "ADMIN", null, null));
        String refreshToken = login.refreshToken();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<TokenPayload> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failed = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    succeeded.add(authService.refresh(refreshToken));
                } catch (BizException ex) {
                    failed.add(ex);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded).hasSize(1);
        assertThat(failed).hasSize(threads - 1);
        assertThat(sessionStatus(login.sessionId())).isEqualTo("ONLINE");

        TokenPayload winner = succeeded.get(0);
        TokenPayload afterRace = authService.refresh(winner.refreshToken());
        assertThat(afterRace.accessToken()).isNotBlank();
        assertThat(sessionStatus(login.sessionId())).isEqualTo("ONLINE");
    }

    private String sessionStatus(String sessionId) throws Exception {
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM fenglin_hub.hub_login_session WHERE session_id = ?")) {
            statement.setString(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }
}
