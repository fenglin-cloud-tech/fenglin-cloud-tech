package com.fenglin.audit.service;

import com.fenglin.common.mybatis.partition.PartitionMaintenanceSupport;
import com.fenglin.common.mybatis.partition.PartitionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 审计日志按月分区维护：启动补建当月/下月分区，之后每月滚动补建下月。
 * 建分区、搬默认分区数据、授权、RLS 与策略复制统一由 {@link PartitionMaintenanceSupport} 完成。
 */
@Component
public class AuditPartitionMaintainer {

    private static final Logger log = LoggerFactory.getLogger(AuditPartitionMaintainer.class);

    private static final PartitionSpec SPEC = new PartitionSpec(
            "fenglin_hub", "hub_audit_log", "hub_audit_log_default", "occurred_at",
            PartitionSpec.KeyType.TIMESTAMPTZ, 1, List.of("fl_app", "fl_platform_role"));

    private final PartitionMaintenanceSupport support;
    private final boolean enabled;

    public AuditPartitionMaintainer(DataSourceProperties dataSourceProperties,
                                    @Value("${fenglin.audit.partition-maintenance-enabled:true}") boolean enabled) {
        this.support = new PartitionMaintenanceSupport(dataSourceProperties);
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        maintain("启动补建");
    }

    @Scheduled(cron = "${fenglin.audit.partition-maintenance-cron:0 5 1 1 * *}")
    public void scheduledMaintain() {
        maintain("月度维护");
    }

    private void maintain(String trigger) {
        if (!enabled) {
            return;
        }
        try {
            int created = ensurePartitions(LocalDate.now(ZoneOffset.UTC));
            log.info("审计月分区{}完成，新建 {} 个分区", trigger, created);
        } catch (RuntimeException ex) {
            log.error("审计月分区{}失败，审计写入暂时落在默认分区", trigger, ex);
        }
    }

    /**
     * 补建指定日期所在月与下个月的分区，返回本次新建的分区数。
     */
    public int ensurePartitions(LocalDate today) {
        return support.ensureMonths(SPEC, today, 2);
    }

    /**
     * 确保指定月份分区存在；已存在、未取得锁或由其他实例完成时返回 false。
     */
    public boolean ensureMonth(YearMonth month) {
        return support.ensureMonth(SPEC, month);
    }
}
