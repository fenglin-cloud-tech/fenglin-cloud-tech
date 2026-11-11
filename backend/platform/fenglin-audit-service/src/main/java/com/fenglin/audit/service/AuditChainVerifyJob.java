package com.fenglin.audit.service;

import com.fenglin.audit.web.dto.ChainVerifyResult;
import com.fenglin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计链完整性定时校验（模块详细设计：审计篡改校验由定时任务每小时执行）。
 * 逐租户校验当月的哈希链，发现断链记录 ERROR 日志；校验只读，不写入审计链，避免二次污染。
 */
@Component
public class AuditChainVerifyJob {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifyJob.class);

    private static final DateTimeFormatter PARTITION_KEY = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx");

    private final AuditChainService auditChainService;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;

    public AuditChainVerifyJob(AuditChainService auditChainService,
                               DataSource dataSource,
                               PlatformTransactionManager transactionManager,
                               @Value("${fenglin.audit.chain-verify-enabled:true}") boolean enabled) {
        this.auditChainService = auditChainService;
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Scheduled(cron = "${fenglin.audit.chain-verify-cron:0 10 * * * *}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            VerifySummary summary = runOnce(Instant.now());
            if (summary.broken() > 0) {
                log.error("审计链定时校验发现断链：{}/{} 个租户校验失败", summary.broken(), summary.checked());
            } else {
                log.info("审计链定时校验通过：{} 个租户", summary.checked());
            }
        } catch (RuntimeException ex) {
            log.error("审计链定时校验执行失败", ex);
        }
    }

    /**
     * 校验当前月份内存在链记录的租户，返回校验汇总（供测试与运维手动触发）。
     */
    public VerifySummary runOnce(Instant now) {
        Instant monthStart = now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        String partitionKey = PARTITION_KEY.format(now);
        List<Long> tenantIds = tenantsWithChain(partitionKey);
        List<ChainVerifyResult> results = new ArrayList<>(tenantIds.size());
        int broken = 0;
        for (Long tenantId : tenantIds) {
            ChainVerifyResult result = verifyTenant(tenantId, monthStart, now);
            results.add(result);
            if (!result.valid()) {
                broken++;
                log.error("审计链校验未通过：tenant={} status={} seq={} logId={} message={}",
                        tenantId, result.status(), result.brokenSeq(), result.brokenLogId(), result.message());
            }
        }
        return new VerifySummary(tenantIds.size(), broken, List.copyOf(results));
    }

    private ChainVerifyResult verifyTenant(long tenantId, Instant from, Instant to) {
        TenantContext.set(tenantId, null, "SYSTEM", null, false);
        try {
            ChainVerifyResult result = transactionTemplate.execute(status ->
                    auditChainService.verify(tenantId, from, to));
            return result == null
                    ? new ChainVerifyResult(tenantId, 0, 0, 0, false, ChainVerifyResult.UNVERIFIABLE,
                            null, null, null, null, null, "校验未返回结果")
                    : result;
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 平台级遍历租户需跨租户读取，走 job_list_audit_chain_tenants 窄函数（仅返回 tenant_id）。
     */
    private List<Long> tenantsWithChain(String partitionKey) {
        String sql = "SELECT tenant_id FROM fenglin_hub.job_list_audit_chain_tenants(?)";
        List<Long> tenantIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, partitionKey);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    tenantIds.add(rs.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取待校验租户列表失败", ex);
        }
        return tenantIds;
    }

    public record VerifySummary(int checked, int broken, List<ChainVerifyResult> results) {
    }
}
