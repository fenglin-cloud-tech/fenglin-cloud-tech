package com.fenglin.common.mybatis;

import com.fenglin.common.tenant.TenantContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 事务级 SET LOCAL 缓存：同一事务只下发一次；事务结束后清理 ThreadLocal，
 * 确保连接归还池后再次借出时一定会重新注入新的租户。
 */
final class TenantRlsSession {

    private static final String SET_TENANT_SQL =
            "SELECT set_config('app.tenant_id', ?, true), set_config('app.cross_tenant', ?, true)";

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private TenantRlsSession() {
    }

    static void apply(Connection connection, TenantContext.Snapshot tenant) throws SQLException {
        State state = STATE.get();
        if (state == null || state.connection != connection) {
            state = new State(connection);
            STATE.set(state);
            registerCleanup();
        }
        String tenantKey = tenant.tenantId() + "|" + tenant.crossTenant();
        if (!tenantKey.equals(state.appliedKey)) {
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
                statement.setString(1, String.valueOf(tenant.tenantId()));
                statement.setString(2, tenant.crossTenant() ? "on" : "off");
                statement.execute();
            }
            state.appliedKey = tenantKey;
        }
    }

    private static void registerCleanup() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                STATE.remove();
            }
        });
    }

    private static final class State {
        private final Connection connection;
        private String appliedKey;

        private State(Connection connection) {
            this.connection = connection;
        }
    }
}
