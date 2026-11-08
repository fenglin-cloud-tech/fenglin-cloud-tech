package com.fenglin.audit;

import com.fenglin.audit.service.AuditPartitionMaintainer;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3：审计日志月分区维护验证（内嵌真实 PostgreSQL + 完整迁移）。
 * 应用以库属主连接执行 DDL，业务写入仍以 fl_app 执行（RLS 生效）。
 */
@SpringBootTest(properties = "fenglin.audit.partition-maintenance-enabled=true")
class AuditPartitionMaintenanceIntegrationTest {

    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
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
    private AuditPartitionMaintainer maintainer;

    @Test
    void startupCreatesCurrentAndNextMonthPartitions() throws Exception {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        assertThat(partitionExists(current)).isTrue();
        assertThat(partitionExists(current.plusMonths(1))).isTrue();
    }

    @Test
    void futureMonthRowsInDefaultPartitionAreMigratedAndGranted() throws Exception {
        YearMonth target = YearMonth.now(ZoneOffset.UTC).plusMonths(2);
        String targetTable = "hub_audit_log_" + target.format(SUFFIX);
        OffsetDateTime occurredAt = target.atDay(5).atStartOfDay().atOffset(ZoneOffset.UTC);
        long id = 9_000_001L;

        assertThat(partitionExists(target)).isFalse();
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_audit_log
                        (id, tenant_id, log_no, event_type, module, action, result, risk_level, occurred_at, deleted)
                    VALUES (%d, 1001, 'AUD-B3-1', 'TENANT_CREATED', 'tenant', 'CREATE', 'SUCCESS', 'LOW', '%s', 0)
                    """.formatted(id, occurredAt));
            assertThat(tableOf(id)).isEqualTo("fenglin_hub.hub_audit_log_default");
        }

        boolean created = maintainer.ensureMonth(target);

        assertThat(created).isTrue();
        assertThat(partitionExists(target)).isTrue();
        assertThat(tableOf(id)).isEqualTo("fenglin_hub." + targetTable);
        assertThat(defaultRowsIn(target)).isZero();
        assertThat(maintainer.ensureMonth(target)).as("幂等：重复维护不再建表").isFalse();
        assertThat(appRoleCanInsert(targetTable)).isTrue();

        OffsetDateTime appOccurredAt = target.atDay(6).atStartOfDay().atOffset(ZoneOffset.UTC);
        insertAsAppRole(9_000_002L, appOccurredAt);

        assertThat(tableOf(9_000_002L)).isEqualTo("fenglin_hub." + targetTable);
    }

    private void insertAsAppRole(long id, OffsetDateTime occurredAt) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_app");
            statement.execute("BEGIN");
            statement.execute("SELECT set_config('app.tenant_id', '1001', true)");
            try (var insert = connection.prepareStatement("""
                    INSERT INTO fenglin_hub.hub_audit_log
                        (id, tenant_id, log_no, event_type, module, action, result, risk_level, occurred_at, deleted)
                    VALUES (?, 1001, ?, 'AUTH_LOGIN_SUCCESS', 'auth', 'LOGIN', 'SUCCESS', 'LOW', ?, 0)
                    """)) {
                insert.setLong(1, id);
                insert.setString(2, "AUD-B3-" + id);
                insert.setObject(3, occurredAt);
                insert.executeUpdate();
            }
            statement.execute("COMMIT");
            statement.execute("RESET ROLE");
        }
    }

    private boolean partitionExists(YearMonth month) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT to_regclass('fenglin_hub.hub_audit_log_"
                     + month.format(SUFFIX) + "') IS NOT NULL")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private String tableOf(long id) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT tableoid::regclass::text FROM fenglin_hub.hub_audit_log "
                     + "WHERE id = " + id)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private long defaultRowsIn(YearMonth month) throws Exception {
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        try (Connection connection = ownerConnection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM fenglin_hub.hub_audit_log_default "
                             + "WHERE occurred_at >= ? AND occurred_at < ?")) {
            statement.setObject(1, start);
            statement.setObject(2, end);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean appRoleCanInsert(String table) throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT has_table_privilege('fl_app', "
                     + "'fenglin_hub." + table + "', 'INSERT')")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }
}
