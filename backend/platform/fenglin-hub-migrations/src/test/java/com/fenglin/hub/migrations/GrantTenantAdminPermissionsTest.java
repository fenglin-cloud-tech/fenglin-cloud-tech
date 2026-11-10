package com.fenglin.hub.migrations;

import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 8 域 TENANT_ADMIN 幂等授权脚本验证（内嵌真实 PostgreSQL + 完整 hub 迁移）。
 */
class GrantTenantAdminPermissionsTest {

    private static final List<String> DOMAINS =
            List.of("crm", "collab", "talent", "finance", "devops", "service", "legal", "ai");
    private static final String ADMIN_TENANT_A = "9001";
    private static final String ADMIN_TENANT_B = "9002";
    private static final long ADMIN_ROLE_A = 900_001L;
    private static final long ADMIN_ROLE_B = 900_002L;
    private static final long MEMBER_ROLE = 900_003L;

    private static EmbeddedPostgresSupport postgres;

    @BeforeAll
    static void migrate() {
        postgres = EmbeddedPostgresSupport.start();
        Flyway.configure()
                .dataSource(postgres.jdbcUrl(), "postgres", "")
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        postgres.close();
    }

    @Test
    void grantsAreIdempotentAndRecoverAfterRoleBackfill() throws Exception {
        seedRolesAndPermissions();
        execute("DELETE FROM fenglin_hub.hub_role_permission");

        runMaintenanceScript();
        List<String> firstRun = snapshot();
        assertThat(firstRun)
                .as("两个租户的 TENANT_ADMIN 各获得全部 8 域权限")
                .hasSize(expectedGrantCount());
        assertThat(grantedCodesOf(ADMIN_TENANT_A, ADMIN_ROLE_A))
                .containsAll(DOMAINS.stream().map(domain -> domain + ":test:perm").toList());
        assertThat(grantedCodesOf(ADMIN_TENANT_B, ADMIN_ROLE_B))
                .containsAll(DOMAINS.stream().map(domain -> domain + ":test:perm").toList());
        assertThat(grantedCodesOf(ADMIN_TENANT_A, MEMBER_ROLE))
                .as("非管理员角色不受影响")
                .isEmpty();
        assertThat(allGrantedCodes())
                .as("平台级权限和非 8 域权限不授予租户管理员")
                .doesNotContain("platform:ai:test:manage", "workflow:test:run");

        runMaintenanceScript();
        assertThat(snapshot())
                .as("连跑两次结果完全一致（行、主键、授权内容都不变）")
                .isEqualTo(firstRun);

        // 模拟回灌再次整体覆盖 hub_role_permission，脚本可自动恢复
        execute("DELETE FROM fenglin_hub.hub_role_permission");
        runMaintenanceScript();
        assertThat(grantedCodesOf(ADMIN_TENANT_A, ADMIN_ROLE_A))
                .hasSize(expectedGrantCount() / 2)
                .containsAll(DOMAINS.stream().map(domain -> domain + ":test:perm").toList());
    }

    private void seedRolesAndPermissions() throws Exception {
        execute("""
                INSERT INTO fenglin_hub.hub_role (id, tenant_id, code, name, role_type, data_scope)
                VALUES (900001, 9001, 'TENANT_ADMIN', '租户管理员', 'TENANT', 'ALL'),
                       (900002, 9002, 'TENANT_ADMIN', '租户管理员', 'TENANT', 'ALL'),
                       (900003, 9001, 'MEMBER', '普通成员', 'BIZ', 'SELF')
                ON CONFLICT (tenant_id, code) DO NOTHING
                """);
        execute("""
                INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
                VALUES (910001, 'crm:test:perm', '测试权限', 'API', 'crm', 1, 0),
                       (910002, 'collab:test:perm', '测试权限', 'API', 'collab', 2, 0),
                       (910003, 'talent:test:perm', '测试权限', 'API', 'talent', 3, 0),
                       (910004, 'finance:test:perm', '测试权限', 'API', 'finance', 4, 0),
                       (910005, 'devops:test:perm', '测试权限', 'API', 'devops', 5, 0),
                       (910006, 'service:test:perm', '测试权限', 'API', 'service', 6, 0),
                       (910007, 'legal:test:perm', '测试权限', 'API', 'legal', 7, 0),
                       (910008, 'ai:test:perm', '测试权限', 'API', 'ai', 8, 0),
                       (910009, 'platform:ai:test:manage', '平台AI测试权限', 'API', 'ai', 9, 0),
                       (910010, 'workflow:test:run', '流程测试权限', 'API', 'workflow', 10, 0)
                ON CONFLICT (code) DO NOTHING
                """);
    }

    private void runMaintenanceScript() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/maintenance/grant_tenant_admin_permissions.sql")) {
            assertThat(in).as("授权脚本必须随 hub-migrations 一起打包").isNotNull();
            execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private int expectedGrantCount() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT (SELECT count(*) FROM fenglin_hub.hub_permission
                             WHERE deleted = 0
                               AND domain IN ('crm','collab','talent','finance','devops','service','legal','ai')
                               AND code NOT LIKE 'platform:%')
                          * (SELECT count(*) FROM fenglin_hub.hub_role
                             WHERE code = 'TENANT_ADMIN' AND deleted = 0)
                     """)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<String> grantedCodesOf(String tenantId, long roleId) throws Exception {
        try (Connection connection = connect();
             var statement = connection.prepareStatement("""
                     SELECT permission_code FROM fenglin_hub.hub_role_permission
                     WHERE tenant_id = ? AND role_id = ? AND deleted = 0
                     ORDER BY permission_code
                     """)) {
            statement.setLong(1, Long.parseLong(tenantId));
            statement.setLong(2, roleId);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> codes = new ArrayList<>();
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
                return codes;
            }
        }
    }

    private List<String> allGrantedCodes() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT permission_code FROM fenglin_hub.hub_role_permission WHERE deleted = 0")) {
            List<String> codes = new ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getString(1));
            }
            return codes;
        }
    }

    private List<String> snapshot() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT id, tenant_id, role_id, permission_code FROM fenglin_hub.hub_role_permission
                     ORDER BY tenant_id, role_id, permission_code
                     """)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getLong(1) + "|" + rs.getLong(2) + "|" + rs.getLong(3) + "|" + rs.getString(4));
            }
            return rows;
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.jdbcUrl(), "postgres", "");
    }
}
