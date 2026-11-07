package com.fenglin.common.mybatis;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;

/**
 * 在每个数据库事务内通过 SET LOCAL 注入租户，不使用连接池初始化 SET。
 * 未处于事务中时直接失败，避免 SET LOCAL 失效导致跨租户读取。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class TenantRlsInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        TenantContext.Snapshot tenant = TenantContext.get();
        if (tenant == null || tenant.tenantId() == null) {
            return invocation.proceed();
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new BizException(CommonErrorCode.TENANT_TRANSACTION_REQUIRED);
        }
        Executor executor = (Executor) invocation.getTarget();
        if (executor.getTransaction() == null) {
            throw new BizException(CommonErrorCode.TENANT_TRANSACTION_REQUIRED);
        }
        Connection connection = executor.getTransaction().getConnection();
        TenantRlsSession.apply(connection, tenant);
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
