package com.fenglin.hub.migrations;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HubMigrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "hub_tenant", "hub_package", "hub_package_item", "hub_subscription",
            "hub_metering", "hub_usage_daily", "hub_bill", "hub_bill_item", "hub_license",
            "hub_message_preference", "hub_wf_callback", "hub_webhook_subscription",
            "hub_webhook_delivery", "hub_ai_model", "hub_ai_prompt",
            "hub_gw_route", "hub_gw_gray_rule",
            "hub_user", "hub_account", "hub_org", "hub_user_org", "hub_position", "hub_user_position",
            "hub_role", "hub_permission", "hub_role_permission", "hub_user_role",
            "hub_data_scope", "hub_field_permission", "hub_menu", "hub_dict_type", "hub_dict_item", "hub_param",
            "hub_login_session", "hub_sso_config", "hub_auth_token", "hub_auth_revocation",
            "hub_audit_export_task", "hub_audit_alert_rule",
            "hub_search_index_task", "hub_search_query_log");

    private static EmbeddedPostgresSupport postgres;
    private static MigrateResult migrateResult;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.jdbcUrl(), "postgres", "")
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load();
        migrateResult = flyway.migrate();
    }

    @AfterAll
    static void stop() {
        postgres.close();
    }

    @Test
    void appliesAllMigrationsOnEmptyDatabase() {
        assertThat(migrateResult.success).isTrue();
        assertThat(migrateResult.migrationsExecuted).isEqualTo(27);
    }

    @Test
    void createsFirstBatchAndSupplementTables() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = 'fenglin_hub' AND table_type = 'BASE TABLE'")) {
            List<String> tables = new ArrayList<>();
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
            assertThat(tables).containsAll(EXPECTED_TABLES);
        }
    }

    @Test
    void tenantSaltColumnsArePresent() throws Exception {
        assertThat(columnsOf("hub_tenant")).contains("hash_salt_enc", "hash_version");
    }

    @Test
    void authTokenExpiryIndexIsPresent() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT indexdef FROM pg_indexes "
                             + "WHERE schemaname = 'fenglin_hub' AND indexname = 'idx_hub_auth_token_expire'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).contains("(tenant_id, expires_at)");
        }
    }

    @Test
    void tenantScopedTablesHaveRowLevelSecurity() throws Exception {
        assertThat(rowLevelSecurityOf("hub_subscription")).isTrue();
        assertThat(rowLevelSecurityOf("hub_metering")).isTrue();
        assertThat(rowLevelSecurityOf("hub_message_preference")).isTrue();
        assertThat(rowLevelSecurityOf("hub_webhook_delivery")).isTrue();
        assertThat(rowLevelSecurityOf("hub_ai_model")).isTrue();
        assertThat(rowLevelSecurityOf("hub_user")).isTrue();
        assertThat(rowLevelSecurityOf("hub_account")).isTrue();
        assertThat(rowLevelSecurityOf("hub_login_session")).isTrue();
        assertThat(rowLevelSecurityOf("hub_auth_token")).isTrue();
        assertThat(rowLevelSecurityOf("hub_permission"))
                .as("功能权限为平台全局表，不启用 RLS")
                .isFalse();
        assertThat(rowLevelSecurityOf("hub_tenant"))
                .as("租户主表是平台跨租户表，按设计不启用 RLS")
                .isFalse();
    }

    @Test
    void everyMigrationIsRecordedAsSuccess() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     // Flyway 会额外记录一行 type='SCHEMA' 的建 schema 条目，这里只统计真正的 SQL 迁移
                     "SELECT count(*) FROM fenglin_hub.flyway_schema_history "
                             + "WHERE success = true AND type = 'SQL'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(27);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.jdbcUrl(), "postgres", "");
    }

    private List<String> columnsOf(String table) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'fenglin_hub' AND table_name = '" + table + "'")) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString(1));
            }
            return columns;
        }
    }

    private boolean rowLevelSecurityOf(String table) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT c.relrowsecurity FROM pg_class c "
                             + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                             + "WHERE n.nspname = 'fenglin_hub' AND c.relname = '" + table + "'")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBoolean(1);
        }
    }
}
