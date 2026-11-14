package com.fenglin.intelligence.migration;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 云智迁移校验：真实内嵌 PostgreSQL 上执行全部脚本，验证表/索引/RLS/触发器/种子与租户隔离。
 * 边界断言：提示词/模型/授权/计量四张表不在本库（由平台库 hub_ai_* 承担）。
 */
class IntelligenceMigrationTest {

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_intelligence")
                .defaultSchema("fenglin_intelligence")
                .locations("classpath:db/intelligence-migration")
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
        // 文档 30 张 - 平台 AI 中台承担的 4 张 + 租户盐/幂等/订阅兜底 3 张 = 29
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_intelligence' "
                + "AND tablename LIKE 'ai\\_%' ESCAPE '\\'")).isEqualTo(29);
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_intelligence' "
                + "AND tablename IN ('ai_knowledge_base', 'ai_knowledge_acl', 'ai_data_source', "
                + "'ai_data_source_credential', 'ai_document', 'ai_document_chunk', 'ai_vector_index_meta', "
                + "'ai_index_task', 'ai_kg_entity', 'ai_kg_relation', 'ai_search_log', 'ai_conversation', "
                + "'ai_message', 'ai_message_citation', 'ai_agent_definition', 'ai_agent_execution', "
                + "'ai_qa_feedback', 'ai_metric_definition', 'ai_dataset', 'ai_dataset_field', 'ai_dashboard', "
                + "'ai_chart', 'ai_insight_task', 'ai_insight_result', 'ai_attribution_analysis', "
                + "'ai_data_permission', 'ai_tenant_key', 'ai_idempotency_record', 'ai_insight_subscription')"))
                .isEqualTo(29);
        // 授权/计量/模型/提示词唯一源在平台库，业务库不得重复建模
        assertThat(count("SELECT count(1) FROM pg_tables WHERE schemaname = 'fenglin_intelligence' "
                + "AND tablename IN ('ai_capability_grant', 'ai_usage_metering', 'ai_model_provider', "
                + "'ai_prompt_template')")).isZero();
    }

    @Test
    void enablesRlsOnEveryTable() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_tables t JOIN pg_class c ON c.relname = t.tablename "
                + "WHERE t.schemaname = 'fenglin_intelligence' AND t.tablename LIKE 'ai\\_%' ESCAPE '\\' "
                + "AND c.relrowsecurity = true")).isEqualTo(29);
        assertThat(count("SELECT count(1) FROM pg_policies WHERE schemaname = 'fenglin_intelligence' "
                + "AND policyname LIKE 'p\\_ai\\_%\\_tenant' ESCAPE '\\'")).isEqualTo(29);
    }

    @Test
    void createsUpdatedAtAndImmutableTriggers() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_trigger WHERE tgname LIKE 'trg\\_ai\\_%\\_updated' ESCAPE '\\' "
                + "AND NOT tgisinternal")).isEqualTo(29);
        assertThat(count("SELECT count(1) FROM pg_trigger "
                + "WHERE tgname LIKE 'trg\\_ai\\_%\\_immutable\\_del' ESCAPE '\\' AND NOT tgisinternal"))
                .isEqualTo(7);
        assertThat(count("SELECT count(1) FROM pg_trigger "
                + "WHERE tgname LIKE 'trg\\_ai\\_%\\_immutable\\_soft' ESCAPE '\\' AND NOT tgisinternal"))
                .isEqualTo(7);
    }

    @Test
    void createsCriticalIndexes() throws Exception {
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_intelligence' "
                + "AND indexname LIKE 'uk\\_ai\\_uuid\\_%' ESCAPE '\\'")).isEqualTo(29);
        assertThat(count("SELECT count(1) FROM pg_indexes WHERE schemaname = 'fenglin_intelligence' "
                + "AND indexname IN ('uk_kb_code', 'uk_acl', 'uk_source_code', 'uk_cred_source', 'idx_doc_kb', "
                + "'idx_doc_checksum', 'idx_doc_title_trgm', 'idx_chunk_doc', 'idx_chunk_kb', 'uk_vec_collection', "
                + "'idx_task_status', 'idx_task_doc', 'idx_kg_ent_name', 'idx_kg_rel_src', 'idx_kg_rel_tgt', "
                + "'idx_search_user_time', 'idx_conv_user', 'idx_msg_conv', 'idx_cite_msg', 'uk_agent_code_ver', "
                + "'idx_agexec_agent', 'uk_metric_code', 'uk_dataset_code', 'uk_dash_code', 'idx_insight_status', "
                + "'idx_insight_result_task', 'idx_attr_metric', 'idx_perm_subject', 'uk_insight_sub', "
                + "'uk_tenant_key', 'uk_idem')")).isEqualTo(31);
    }

    @Test
    void seedsBuiltinMetricsReadableAcrossTenants() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_intelligence_app");
            statement.execute("SET app.tenant_id = '9901'");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_intelligence.ai_metric_definition "
                    + "WHERE tenant_id = 0")).isEqualTo(5);
            Throwable denied = catchThrowable(() -> statement.execute(
                    "INSERT INTO fenglin_intelligence.ai_metric_definition "
                            + "(id, tenant_id, code, name, formula, created_by) "
                            + "VALUES (900999, 0, 'x.y', '伪造平台指标', '1', 1)"));
            assertThat(denied).isInstanceOf(PSQLException.class);
            assertThat(((PSQLException) denied).getSQLState()).isEqualTo("42501");
            assertThat(denied.getMessage()).contains("ai_metric_definition");
        }
    }

    @Test
    void blocksDeleteAndSoftDeleteOnImmutableTables() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_intelligence_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_intelligence.ai_message "
                    + "(id, tenant_id, conversation_id, role, content, created_by, updated_by) "
                    + "VALUES (1, 9901, 100, 'ASSISTANT', '回答', 1, 1)");

            assertThatThrownBy(() -> statement.execute("DELETE FROM fenglin_intelligence.ai_message WHERE id = 1"))
                    .hasMessageContaining("immutable table");
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE fenglin_intelligence.ai_message SET deleted = 1 WHERE id = 1"))
                    .hasMessageContaining("immutable table");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_intelligence.ai_message")).isEqualTo(1);
        }
    }

    @Test
    void enforcesTenantIsolation() throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE fl_intelligence_app");
            statement.execute("SET app.tenant_id = '9901'");
            statement.execute("INSERT INTO fenglin_intelligence.ai_knowledge_base "
                    + "(id, tenant_id, code, name, vector_collection, es_index, created_by, updated_by) "
                    + "VALUES (11, 9901, 'kb_default', '租户9901知识库', 'col_9901', 'idx_9901', 1, 1)");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_intelligence.ai_knowledge_base"))
                    .isEqualTo(1);

            statement.execute("SET app.tenant_id = '9902'");
            assertThat(countOn(connection, "SELECT count(1) FROM fenglin_intelligence.ai_knowledge_base")).isZero();
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
