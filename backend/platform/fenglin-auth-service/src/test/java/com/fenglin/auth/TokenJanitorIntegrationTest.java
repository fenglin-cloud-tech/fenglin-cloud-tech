package com.fenglin.auth;

import com.fenglin.auth.service.TokenJanitor;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2：过期 refresh token 清理验证（内嵌真实 PostgreSQL + 完整迁移 + fl_app 角色，RLS 生效）。
 */
@SpringBootTest
class TokenJanitorIntegrationTest {

    private static final String JDBC_URL;
    private static final EmbeddedPostgresSupport POSTGRES;

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
    }

    @Autowired
    private TokenJanitor tokenJanitor;

    @BeforeEach
    void resetData() throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_auth_token, fenglin_hub.hub_tenant");
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_tenant (id, code, name, status, hash_salt_enc, deleted)
                    VALUES (1001, 'fenglin', '丰麟云创', 'ACTIVE', 'v1:test-salt', 0),
                           (1002, 'acme', 'Acme 租户', 'ACTIVE', 'v1:test-salt-b', 0)
                    """);
        }
    }

    @Test
    void removesOnlyTokensExpiredBeyondRetentionAcrossTenants() throws Exception {
        Instant now = Instant.now();
        insertToken(1L, 1001L, "old-1001", now.minus(9, ChronoUnit.DAYS));
        insertToken(2L, 1001L, "recent-expired-1001", now.minus(1, ChronoUnit.DAYS));
        insertToken(3L, 1001L, "active-1001", now.plus(1, ChronoUnit.DAYS));
        insertToken(4L, 1002L, "old-1002", now.minus(8, ChronoUnit.DAYS));
        insertToken(5L, 1002L, "active-1002", now.plus(3, ChronoUnit.DAYS));

        int deleted = tokenJanitor.runOnce();

        assertThat(deleted).isEqualTo(2);
        assertThat(countByHash("old-1001")).isZero();
        assertThat(countByHash("old-1002")).isZero();
        assertThat(countByHash("recent-expired-1001")).isEqualTo(1);
        assertThat(countByHash("active-1001")).isEqualTo(1);
        assertThat(countByHash("active-1002")).isEqualTo(1);
    }

    @Test
    void deletesAllExpiredTokensInBatches() throws Exception {
        Instant expiredAt = Instant.now().minus(10, ChronoUnit.DAYS);
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            for (int i = 0; i < 1200; i++) {
                statement.execute("""
                        INSERT INTO fenglin_hub.hub_auth_token
                            (id, tenant_id, user_id, session_id, token_hash, token_type, client_type, expires_at, deleted)
                        VALUES (%d, 1001, 2001, 'sess-batch', 'batch-%d', 'REFRESH', 'ADMIN', '%s', 0)
                        """.formatted(10_000L + i, i, expiredAt));
            }
        }

        int deleted = tokenJanitor.runOnce();

        assertThat(deleted).isEqualTo(1200);
        assertThat(countByHashPrefix("batch-")).isZero();
    }

    private void insertToken(long id, long tenantId, String tokenHash, Instant expiresAt) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_auth_token
                        (id, tenant_id, user_id, session_id, token_hash, token_type, client_type, expires_at, deleted)
                    VALUES (%d, %d, 2001, 'sess-%d', '%s', 'REFRESH', 'ADMIN', '%s', 0)
                    """.formatted(id, tenantId, tenantId, tokenHash, expiresAt));
        }
    }

    private int countByHash(String tokenHash) throws Exception {
        return countWhere("token_hash = '" + tokenHash + "'");
    }

    private int countByHashPrefix(String prefix) throws Exception {
        return countWhere("token_hash LIKE '" + prefix + "%'");
    }

    private int countWhere(String condition) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM fenglin_hub.hub_auth_token WHERE " + condition)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }
}
