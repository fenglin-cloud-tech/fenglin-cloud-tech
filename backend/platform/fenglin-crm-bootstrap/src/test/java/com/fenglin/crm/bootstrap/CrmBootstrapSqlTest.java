package com.fenglin.crm.bootstrap;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM 前置数据 bootstrap 可重复执行验证：真实 PostgreSQL，连续执行两次不产生重复数据。
 */
class CrmBootstrapSqlTest {

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
                .table("crm_bootstrap_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/crm-bootstrap")
                .load()
                .migrate();
    }

    @Test
    void seedsPermissionsOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE code = 'crm:contract:manage'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'crm'"))
                .isEqualTo(21);
    }

    @Test
    void seedsDictionariesOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_type WHERE code LIKE 'crm\\_%' ESCAPE '\\'"))
                .isEqualTo(8);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code = 'crm_opportunity_stage'"))
                .isEqualTo(6);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code LIKE 'crm\\_%' ESCAPE '\\'"))
                .isEqualTo(35);
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
