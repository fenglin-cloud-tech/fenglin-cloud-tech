package com.fenglin.message.service;

import com.fenglin.common.mybatis.partition.DdlConnectionConfig;
import com.fenglin.common.mybatis.partition.DdlIdentitySelfCheck;
import com.fenglin.common.mybatis.partition.PartitionMaintenanceSupport;
import com.fenglin.common.mybatis.partition.PartitionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 消息表按月分区维护：启动补建当月/下月分区，之后每月滚动补建。
 * 建分区、搬默认分区数据、授权、RLS 与策略复制统一由 {@link PartitionMaintenanceSupport} 完成。
 * DDL 使用独立数据源（fl_login_ddl + SET ROLE fl_ddl_owner），启动自检失败即退出。
 */
@Component
public class MessagePartitionMaintainer {

    private static final Logger log = LoggerFactory.getLogger(MessagePartitionMaintainer.class);

    private static final PartitionSpec SPEC = new PartitionSpec(
            "fenglin_hub", "hub_message", "hub_message_default", "created_at",
            PartitionSpec.KeyType.TIMESTAMPTZ, 2, List.of("fl_app", "fl_platform_role"));

    private final PartitionMaintenanceSupport support;
    private final ApplicationContext applicationContext;
    private final boolean enabled;
    private final boolean selfCheckEnabled;

    public MessagePartitionMaintainer(Environment environment,
                                      ApplicationContext applicationContext,
                                      @Value("${fenglin.message.partition-maintenance-enabled:true}") boolean enabled,
                                      @Value("${fenglin.db.ddl.self-check-enabled:true}") boolean selfCheckEnabled) {
        this.applicationContext = applicationContext;
        this.enabled = enabled;
        this.selfCheckEnabled = selfCheckEnabled;
        this.support = enabled ? new PartitionMaintenanceSupport(DdlConnectionConfig.from(environment)) : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) {
            return;
        }
        if (selfCheckEnabled) {
            DdlIdentitySelfCheck.verifyOrExit(support, SPEC.schema(), applicationContext, "fenglin-message-service");
        }
        maintain("启动补建");
    }

    @Scheduled(cron = "${fenglin.message.partition-maintenance-cron:0 10 1 1 * *}")
    public void scheduledMaintain() {
        maintain("月度维护");
    }

    private void maintain(String trigger) {
        if (!enabled) {
            return;
        }
        try {
            int created = ensurePartitions(LocalDate.now(ZoneOffset.UTC));
            log.info("消息月分区{}完成，新建 {} 个分区", trigger, created);
        } catch (RuntimeException ex) {
            log.error("消息月分区{}失败，消息暂时落在默认分区", trigger, ex);
        }
    }

    public int ensurePartitions(LocalDate today) {
        return support.ensureMonths(SPEC, today, 2);
    }

    public boolean ensureMonth(YearMonth month) {
        return support.ensureMonth(SPEC, month);
    }
}
