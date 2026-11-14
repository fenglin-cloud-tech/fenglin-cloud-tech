package com.fenglin.rnd.bootstrap;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云研前置数据 bootstrap 可重复执行验证：真实 PostgreSQL，连续执行两次不产生重复数据。
 */
class RndBootstrapSqlTest {

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
        seededPermissions = count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'devops'");
        seededDictTypes = count("SELECT count(1) FROM fenglin_hub.hub_dict_type WHERE code LIKE 'devops\\_%' ESCAPE '\\'");
        seededDictItems = count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 AND dict_code LIKE 'devops\\_%' ESCAPE '\\'");
        applyBootstrap();
    }

    private static void applyBootstrap() {
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .table("rnd_bootstrap_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/rnd-bootstrap")
                .load()
                .migrate();
    }

    @Test
    void seedsPermissionsOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission "
                + "WHERE code = 'biz:devops:release:deploy'")).isEqualTo(1);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_permission WHERE domain = 'devops'"))
                .isEqualTo(seededPermissions);
        assertThat(count("SELECT count(DISTINCT code) FROM fenglin_hub.hub_permission WHERE domain = 'devops'"))
                .isEqualTo(seededPermissions);
    }

    @Test
    void seedsDictionariesOnce() throws Exception {
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_type WHERE code LIKE 'devops\\_%' ESCAPE '\\'"))
                .isEqualTo(seededDictTypes);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code LIKE 'devops\\_%' ESCAPE '\\'")).isEqualTo(seededDictItems);
        assertThat(count("SELECT count(1) FROM fenglin_hub.hub_dict_item WHERE tenant_id = 0 "
                + "AND dict_code = 'devops_req_status_default'")).isEqualTo(7);
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
