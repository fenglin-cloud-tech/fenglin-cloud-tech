package com.fenglin.intelligence.bootstrap;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云智前置数据 bootstrap 幂等验证：真实 PostgreSQL，重复执行不产生重复数据。
 */
class IntelligenceBootstrapSqlTest {

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        applyBootstrap();
        applyBootstrap();
    }

    private static void applyBootstrap() {
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .table("intelligence_bootstrap_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/intelligence-bootstrap")
                .load()
                .migrate();
    }

    @Test
    void seedsPermissionsOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission "
                + "WHERE code = 'ai:search:secure'")).isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'ai'"))
                .isEqualTo(39);
        assertThat(count("SELECT count(DISTINCT code) FROM fenglin_hub.hub_permission WHERE domain = 'ai'"))
                .isEqualTo(39);
        // 平台 AI 中台已登记的权限码复用，不重复插入
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE code = 'ai:chat:rag'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission "
                + "WHERE code = 'tenant:ai:prompt:manage'")).isEqualTo(1);
    }

    @Test
    void seedsDictionariesOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_type "
                + "WHERE code LIKE 'ai\\_%' ESCAPE '\\'")).isEqualTo(21);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code LIKE 'ai\\_%' ESCAPE '\\'")).isEqualTo(88);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code = 'ai_sec_level'")).isEqualTo(4);
    }

    @Test
    void doesNotDuplicatePlatformAiConfig() throws Exception {
        // 提示词/模型/授权/计量的数据归平台 AI 中台，bootstrap 不得写入
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_ai_prompt WHERE deleted = 0")).isZero();
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_ai_model WHERE deleted = 0")).isZero();
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_ai_capability_grant WHERE deleted = 0")).isZero();
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_ai_metering WHERE deleted = 0")).isZero();
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
