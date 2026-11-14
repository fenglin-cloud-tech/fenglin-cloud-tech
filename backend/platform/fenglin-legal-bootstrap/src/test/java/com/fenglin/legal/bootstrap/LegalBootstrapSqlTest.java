package com.fenglin.legal.bootstrap;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云法前置数据 bootstrap 可重复执行验证：真实 PostgreSQL，连续执行两次不产生重复数据。
 */
class LegalBootstrapSqlTest {

    private static EmbeddedPostgresSupport postgres;
    private static long seededPermissions;
    private static long seededDictTypes;
    private static long seededDictItems;

    @BeforeAll
    static void migrate() throws Exception {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        applyBootstrap();
        seededPermissions = count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'legal'");
        seededDictTypes = count("SELECT count(1) FROM fenglin_hub.hub_dict_type WHERE code LIKE 'legal\\_%' ESCAPE '\\'");
        seededDictItems = count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 AND dict_code LIKE 'legal\\_%' ESCAPE '\\'");
        applyBootstrap();
    }

    private static void applyBootstrap() {
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .table("legal_bootstrap_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/legal-bootstrap")
                .load()
                .migrate();
    }

    @Test
    void seedsPermissionsOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission "
                + "WHERE code = 'legal:contract:approve'")).isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'legal'"))
                .isEqualTo(seededPermissions);
        assertThat(count("SELECT count(DISTINCT code) FROM fenglin_hub.hub_permission WHERE domain = 'legal'"))
                .isEqualTo(seededPermissions);
    }

    @Test
    void seedsDictionariesOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_type "
                + "WHERE code LIKE 'legal\\_%' ESCAPE '\\'")).isEqualTo(seededDictTypes);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code LIKE 'legal\\_%' ESCAPE '\\'")).isEqualTo(seededDictItems);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code = 'legal_contract_status'")).isEqualTo(10);
    }

    private static long count(String sql) throws Exception {
        try (Connection connection = postgres.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
