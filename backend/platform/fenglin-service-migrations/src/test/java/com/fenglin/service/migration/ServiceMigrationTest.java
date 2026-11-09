package com.fenglin.service.migration;

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
 * 云服迁移校验：真实内嵌 PostgreSQL 上执行全部脚本，验证表/索引/RLS/触发器/内置目录与 SLA 种子。
 */
class ServiceMigrationTest {

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_service")
                .defaultSchema("fenglin_service")
                .locations("classpath:db/service-migration")
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
        // 文档 37 张业务表 + 租户盐表 + 幂等记录表 + 配置兜底表 + 脚本兜底表 = 41
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_service' "
                + "AND tablename LIKE 'svc\\_%' ESCAPE '\\'")).isEqualTo(41);
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_service' "
                + "AND tablename IN ('svc_tenant_key', 'svc_idempotency_record', "
                + "'svc_tenant_config', 'svc_script_definition')")).isEqualTo(4);
    }

    @Test
    void enablesRlsOnEveryTable() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_tables t JOIN pg_class c ON c.relname = t.tablename "
                + "WHERE t.schemaname = 'fenglin_service' AND t.tablename LIKE 'svc\\_%' ESCAPE '\\' "
                + "AND c.relrowsecurity = true")).isEqualTo(41);
        assertThat(count("SELECT count(1) FROM pg_policies WHERE schemaname = 'fenglin_service' "
                + "AND policyname = 'tenant_isolation'")).isEqualTo(41);
        assertThat(count("SELECT count(1) FROM pg_trigger WHERE tgname LIKE 'trg\\_svc\\_%' ESCAPE '\\' "
                + "AND NOT tgisinternal")).isEqualTo(41);
    }

    @Test
    void createsCriticalIndexes() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_service' "
                + "AND indexname IN ('uk_ticket_no', 'uk_ci_code', 'uk_kb_title_ver', 'uk_csat_ticket', "
                + "'uk_app_code', 'uk_form_key', 'uk_flow_key', 'uk_page_key', 'uk_release_no', "
                + "'uk_robot_code', 'uk_rt_no', 'uk_rt_idem', 'uk_tag', 'uk_tag_rel', "
                + "'uk_tenant_key', 'uk_idem', 'uk_asset_no', 'uk_problem_no', 'uk_change_no')")).isEqualTo(19);
    }

    @Test
    void seedsDefaultCatalogAndSla() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_service.svc_sla_policy WHERE tenant_id = 0")).isEqualTo(2);
        assertThat(count("SELECT count(1) FROM fenglin_service.svc_catalog WHERE tenant_id = 0")).isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_service.svc_catalog_item WHERE tenant_id = 0")).isEqualTo(3);
    }

    @Test
    void enforcesTenantIsolation() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_service_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_service.svc_ticket "
                    + "(id, tenant_id, ticket_no, title, ticket_type, source, requester_id, created_by) "
                    + "VALUES (1, 9901, 'SVC202601000001', '租户9901工单', 'INCIDENT', 'SELF', 1, 1)");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_service.svc_ticket")).isEqualTo(1);

            statement.execute("SET app.tenant_id = '9902'");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_service.svc_ticket")).isZero();

            // 平台默认目录与 SLA 模板对任意租户可读
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_service.svc_catalog_item "
                    + "WHERE tenant_id = 0")).isEqualTo(3);
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_service.svc_sla_policy "
                    + "WHERE tenant_id = 0")).isEqualTo(2);
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
}
