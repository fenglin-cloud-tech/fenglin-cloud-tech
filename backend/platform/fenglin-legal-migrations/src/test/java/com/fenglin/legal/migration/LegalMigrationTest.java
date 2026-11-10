package com.fenglin.legal.migration;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 云法迁移校验：真实内嵌 PostgreSQL 上执行全部脚本，验证表/索引/RLS/防删触发器与租户隔离。
 */
class LegalMigrationTest {

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_legal")
                .defaultSchema("fenglin_legal")
                .locations("classpath:db/legal-migration")
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
        // 文档业务表 + 租户盐表 + 幂等记录表 = 33
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_legal' "
                + "AND tablename LIKE 'legal\\_%' ESCAPE '\\'")).isEqualTo(33);
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_legal' "
                + "AND tablename IN ('legal_contract', 'legal_contract_version', 'legal_contract_clause', "
                + "'legal_signatory', 'legal_approval_record', 'legal_seal_record', 'legal_sign_record', "
                + "'legal_evidence', 'legal_dlp_event', 'legal_dlp_event_handle', 'legal_watermark_record', "
                + "'legal_dlp_audit_log', 'legal_tenant_key', 'legal_idempotency_record')")).isEqualTo(14);
    }

    @Test
    void enablesRlsOnEveryTable() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_tables t JOIN pg_class c ON c.relname = t.tablename "
                + "WHERE t.schemaname = 'fenglin_legal' AND t.tablename LIKE 'legal\\_%' ESCAPE '\\' "
                + "AND c.relrowsecurity = true")).isEqualTo(33);
        assertThat(count("SELECT count(1) FROM pg_policies WHERE schemaname = 'fenglin_legal' "
                + "AND policyname LIKE 'p\\_legal\\_%\\_tenant' ESCAPE '\\'")).isEqualTo(33);
    }

    @Test
    void createsUpdatedAtAndImmutableTriggers() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_trigger WHERE tgname LIKE 'trg\\_legal\\_%\\_updated' ESCAPE '\\' "
                + "AND NOT tgisinternal")).isEqualTo(33);
        assertThat(count("SELECT count(1) FROM pg_trigger "
                + "WHERE tgname LIKE 'trg\\_legal\\_%\\_immutable\\_del' ESCAPE '\\' AND NOT tgisinternal"))
                .isEqualTo(11);
        assertThat(count("SELECT count(1) FROM pg_trigger "
                + "WHERE tgname LIKE 'trg\\_legal\\_%\\_immutable\\_soft' ESCAPE '\\' AND NOT tgisinternal"))
                .isEqualTo(11);
    }

    @Test
    void createsCriticalIndexes() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_legal' "
                + "AND indexname LIKE 'uk\\_legal\\_uuid\\_%' ESCAPE '\\'")).isEqualTo(29);
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_legal' "
                + "AND indexname IN ('uk_contract_no', 'uk_tpl_no', 'uk_tpl_ver', 'uk_tpl_var', 'uk_ct_ver', "
                + "'uk_party_uscc', 'uk_seal_apply_no', 'uk_sign_task_no', 'uk_evidence_no', 'uk_reminder', "
                + "'uk_audit_chain', 'uk_channel', 'uk_event_no', 'uk_wm_embed', 'uk_tenant_key', 'uk_idem', "
                + "'idx_contract_status', 'idx_contract_end', 'idx_clause_ver', 'idx_sign_contract', "
                + "'idx_sign_provider_task', 'idx_evidence_contract', 'idx_seal_record_ct', 'idx_perf_due', "
                + "'idx_rule_tenant', 'idx_policy_tenant', 'idx_event_query', 'idx_event_user', "
                + "'idx_terminal_hb', 'idx_attach_obj', 'idx_contract_name_trgm')")).isEqualTo(31);
    }

    @Test
    void blocksDeleteAndSoftDeleteOnImmutableTables() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_legal_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_legal.legal_evidence "
                    + "(id, tenant_id, evidence_no, contract_id, evidence_type, content_hash, block_hash, "
                    + " created_by, updated_by) "
                    + "VALUES (1, 9901, 'EVD2026000001', 100, 'CONTRACT_DOC', 'h1', 'b1', 1, 1)");

            assertThatThrownBy(() -> statement.execute("DELETE FROM fenglin_legal.legal_evidence WHERE id = 1"))
                    .hasMessageContaining("immutable table");
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE fenglin_legal.legal_evidence SET deleted = 1 WHERE id = 1"))
                    .hasMessageContaining("immutable table");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_legal.legal_evidence")).isEqualTo(1);
        }
    }

    @Test
    void enforcesTenantIsolation() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_legal_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_legal.legal_contract "
                    + "(id, tenant_id, contract_no, name, category, created_by, updated_by) "
                    + "VALUES (11, 9901, 'CT2026000001', '租户9901合同', 'SALES', 1, 1)");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_legal.legal_contract")).isEqualTo(1);

            statement.execute("SET app.tenant_id = '9902'");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_legal.legal_contract")).isZero();
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
