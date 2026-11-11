package com.fenglin.ai.service;

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
import java.time.ZoneOffset;
import java.util.List;

/**
 * AI 计量按月分区维护：启动补建当月及未来两月，之后每月滚动维护。
 * 保持 DEFAULT 分区兜底；新分区自动开 RLS 并复制父表策略。
 */
@Component
public class AiMeteringPartitionMaintainer {

    private static final Logger log = LoggerFactory.getLogger(AiMeteringPartitionMaintainer.class);

    private static final PartitionSpec SPEC = new PartitionSpec(
            "fenglin_hub", "hub_ai_metering", "hub_ai_metering_default", "stat_date",
            PartitionSpec.KeyType.DATE, 4, List.of("fl_app", "fl_platform_role"));

    private final PartitionMaintenanceSupport support;
    private final boolean enabled;

    public AiMeteringPartitionMaintainer(DataSourceProperties dataSourceProperties,
                                         @Value("${fenglin.ai.partition-maintenance-enabled:true}")
                                         boolean enabled) {
        this.support = new PartitionMaintenanceSupport(dataSourceProperties);
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        maintain("启动补建");
    }

    @Scheduled(cron = "${fenglin.ai.partition-maintenance-cron:0 30 1 1 * *}")
    public void scheduledMaintain() {
        maintain("月度维护");
    }

    private void maintain(String trigger) {
        if (!enabled) {
            return;
        }
        try {
            int created = support.ensureMonths(SPEC, LocalDate.now(ZoneOffset.UTC), 3);
            log.info("AI 计量月分区{}完成，新建 {} 个分区", trigger, created);
        } catch (RuntimeException ex) {
            log.error("AI 计量月分区{}失败，写入暂时落在默认分区", trigger, ex);
        }
    }
}
