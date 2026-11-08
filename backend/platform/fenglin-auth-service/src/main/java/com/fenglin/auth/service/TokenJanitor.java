package com.fenglin.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.entity.TenantEntity;
import com.fenglin.auth.mapper.RefreshTokenMapper;
import com.fenglin.auth.mapper.TenantMapper;
import com.fenglin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * refresh token 过期清理：按租户分批物理删除超过保留期的记录。
 * RLS 生效，需逐租户在事务内注入租户上下文后删除。
 */
@Component
public class TokenJanitor {

    private static final Logger log = LoggerFactory.getLogger(TokenJanitor.class);

    private final TenantMapper tenantMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final TransactionTemplate transactionTemplate;
    private final AuthProperties properties;

    public TokenJanitor(TenantMapper tenantMapper, RefreshTokenMapper refreshTokenMapper,
                        TransactionTemplate transactionTemplate, AuthProperties properties) {
        this.tenantMapper = tenantMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    @Scheduled(cron = "${fenglin.auth.token-janitor-cron:0 30 3 * * *}")
    public void scheduledRun() {
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.error("refresh token 清理任务失败", ex);
        }
    }

    /**
     * 执行一次清理，返回删除条数（供运维手动触发与测试验证）。
     */
    public int runOnce() {
        // 平台级遍历租户前先清除线程残留上下文，避免 RLS 拦截器要求租户上下文
        TenantContext.clear();
        Instant cutoff = Instant.now().minus(properties.getExpiredTokenRetention());
        List<TenantEntity> tenants = tenantMapper.selectList(Wrappers.<TenantEntity>lambdaQuery()
                .select(TenantEntity::getId)
                .eq(TenantEntity::getDeleted, 0));
        int total = 0;
        for (TenantEntity tenant : tenants) {
            total += purgeTenant(tenant.getId(), cutoff);
        }
        log.info("refresh token 清理完成：删除 {} 条（expires_at < {}，保留期 {}）",
                total, cutoff, properties.getExpiredTokenRetention());
        return total;
    }

    private int purgeTenant(Long tenantId, Instant cutoff) {
        TenantContext.set(tenantId, null, "SYSTEM", null, false);
        try {
            int tenantTotal = 0;
            while (true) {
                Integer deleted = transactionTemplate.execute(status ->
                        refreshTokenMapper.deleteExpired(tenantId, cutoff, properties.getTokenJanitorBatchSize()));
                int batch = deleted == null ? 0 : deleted;
                tenantTotal += batch;
                if (batch < properties.getTokenJanitorBatchSize()) {
                    break;
                }
            }
            if (tenantTotal > 0) {
                log.info("租户 {} 删除过期 refresh token {} 条", tenantId, tenantTotal);
            }
            return tenantTotal;
        } finally {
            TenantContext.clear();
        }
    }
}
