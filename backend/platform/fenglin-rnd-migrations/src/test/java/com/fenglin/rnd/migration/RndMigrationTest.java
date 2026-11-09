package com.fenglin.rnd.migration;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云研迁移校验：真实内嵌 PostgreSQL 上执行全部脚本，验证表/索引/RLS/触发器/内置口径种子。
 */
class RndMigrationTest {

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_devops")
                .defaultSchema("fenglin_devops")
                .locations("classpath:db/rnd-migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void createsAllBusinessTables() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_devops' "
                + "AND tablename LIKE 'devops\\_%' ESCAPE '\\'")).isEqualTo(34);
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_devops' "
                + "AND tablename = 'devops_tenant_key'")).isEqualTo(1);
    }

    @Test
    void enablesRlsOnEveryTable() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_tables t JOIN pg_class c ON c.relname = t.tablename "
                + "WHERE t.schemaname = 'fenglin_devops' AND t.tablename LIKE 'devops\\_%' ESCAPE '\\' "
                + "AND c.relrowsecurity = true")).isEqualTo(34);
        assertThat(count("SELECT count(1) FROM pg_policies WHERE schemaname = 'fenglin_devops' "
                + "AND policyname = 'tenant_isolation'")).isEqualTo(34);
        assertThat(count("SELECT count(1) FROM pg_trigger WHERE tgname LIKE 'trg\\_devops\\_%' ESCAPE '\\' "
                + "AND NOT tgisinternal")).isEqualTo(34);
    }

    @Test
    void createsCriticalIndexes() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_devops' "
                + "AND indexname IN ('uk_commit_sha', 'uk_snapshot', 'uk_release_no', 'uk_event_id', "
                + "'uk_metric_config', 'uk_tenant_key')")).isEqualTo(6);
    }

    @Test
    void seedsPlatformMetricConfig() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_devops.devops_metric_config WHERE tenant_id = 0"))
                .isEqualTo(10);
    }

    @Test
    void enforcesTenantIsolation() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_devops_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_devops.devops_product "
                    + "(id, tenant_id, product_code, name, owner_id, created_by) "
                    + "VALUES (1, 9901, 'P9901', '租户9901产品', 1, 1)");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_devops.devops_product")).isEqualTo(1);

            statement.execute("SET app.tenant_id = '9902'");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_devops.devops_product")).isZero();

            // 平台内置度量口径对任意租户可读，租户覆盖行仍隔离
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_devops.devops_metric_config "
                    + "WHERE tenant_id = 0")).isEqualTo(10);
        }
    }

    private long countOn(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long count(String sql) throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
