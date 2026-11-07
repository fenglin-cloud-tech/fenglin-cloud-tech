package com.fenglin.common.mybatis.test;

import com.fenglin.common.core.BizException;
import com.fenglin.common.mybatis.test.support.ProbeResult;
import com.fenglin.common.mybatis.test.support.TenantProbeMapper;
import com.fenglin.common.mybatis.test.support.TenantProbeService;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RlsTestApplication.class)
class TenantRlsConnectionReuseTest {

    private static final EmbeddedPostgresSupport POSTGRES = startPostgres();

    @Autowired
    private TenantProbeService service;

    @Autowired
    private TenantProbeMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    @Test
    void tenantInterceptorIsRegisteredInMybatisChain() {
        assertThat(applicationContext.getBeanNamesForType(
                com.fenglin.common.mybatis.TenantRlsInterceptor.class)).isNotEmpty();
        assertThat(sqlSessionFactory.getConfiguration().getInterceptors())
                .anyMatch(interceptor -> interceptor instanceof com.fenglin.common.mybatis.TenantRlsInterceptor);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 1);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.close();
    }

    @Test
    void reusedConnectionIsolatesTenantsAcrossSequentialTransactions() throws Exception {
        ProbeResult tenantOne = service.queryFor(1L);
        ProbeResult tenantTwo = service.queryFor(2L);

        assertThat(tenantOne.ids()).containsExactly(1L, 3L);
        assertThat(tenantTwo.ids()).containsExactly(2L, 4L);
        assertThat(tenantOne.currentTenantSetting()).isEqualTo("1");
        assertThat(tenantTwo.currentTenantSetting()).isEqualTo("2");
        assertThat(tenantOne.backendPid())
                .as("连接池只允许一个物理连接，两次事务必须复用同一后端连接")
                .isEqualTo(tenantTwo.backendPid());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
            assertThat(resultSet.next()).isTrue();
            String residualTenant = resultSet.getString(1);
            assertThat(residualTenant)
                    .as("事务结束后 SET LOCAL 必须失效，连接不能残留租户")
                    .satisfiesAnyOf(value -> assertThat(value).isNull(), value -> assertThat(value).isEmpty());
        }
    }

    @Test
    void tenantQueryOutsideTransactionIsRejected() {
        TenantContext.set(1L, null, "ADMIN", "test-request", false);
        try {
            assertThatThrownBy(() -> mapper.findIds())
                    .as("MyBatis 会把业务异常包装为 PersistenceException，根因必须仍是 BizException")
                    .hasRootCauseInstanceOf(BizException.class)
                    .rootCause()
                    .extracting(error -> ((BizException) error).getErrorCode().code())
                    .isEqualTo("FL-COMMON-0422");
        } finally {
            TenantContext.clear();
        }
    }

    private static EmbeddedPostgresSupport startPostgres() {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        try (Connection connection = java.sql.DriverManager.getConnection(postgres.jdbcUrl(), "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE fl_app NOLOGIN");
            statement.execute("CREATE TABLE tenant_probe ("
                    + "id bigint PRIMARY KEY, tenant_id bigint NOT NULL, name varchar(64) NOT NULL)");
            statement.execute("INSERT INTO tenant_probe (id, tenant_id, name) VALUES "
                    + "(1, 1, 'a1'), (2, 2, 'b1'), (3, 1, 'a2'), (4, 2, 'b2')");
            statement.execute("ALTER TABLE tenant_probe ENABLE ROW LEVEL SECURITY");
            statement.execute("CREATE POLICY p_tenant_probe ON tenant_probe "
                    + "USING (tenant_id = current_setting('app.tenant_id', true)::bigint) "
                    + "WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::bigint)");
            statement.execute("GRANT USAGE ON SCHEMA public TO fl_app");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_probe TO fl_app");
            return postgres;
        } catch (Exception ex) {
            postgres.close();
            throw new IllegalStateException("初始化 RLS 测试库失败", ex);
        }
    }
}
