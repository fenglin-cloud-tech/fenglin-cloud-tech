package com.fenglin.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计导出任务轮询（HUB-API-802）：跨租户发现 PENDING 任务后逐租户生成 CSV。
 * 任务领取使用 status=PENDING 条件更新，天然避免多实例重复导出。
 */
@Component
public class AuditExportJob {

    private static final Logger log = LoggerFactory.getLogger(AuditExportJob.class);

    private final AuditExportService auditExportService;
    private final DataSourceProperties dataSourceProperties;
    private final boolean enabled;

    public AuditExportJob(AuditExportService auditExportService,
                          DataSourceProperties dataSourceProperties,
                          @Value("${fenglin.audit.export-job-enabled:true}") boolean enabled) {
        this.auditExportService = auditExportService;
        this.dataSourceProperties = dataSourceProperties;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${fenglin.audit.export-poll-interval-ms:5000}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.error("审计导出任务轮询失败", ex);
        }
    }

    public int runOnce() {
        List<PendingTask> tasks = pendingTasks();
        for (PendingTask task : tasks) {
            auditExportService.process(task.id(), task.tenantId());
        }
        return tasks.size();
    }

    private List<PendingTask> pendingTasks() {
        String sql = "SELECT id, tenant_id FROM fenglin_hub.hub_audit_export_task"
                + " WHERE status = 'PENDING' AND deleted = 0 ORDER BY created_at LIMIT 10";
        List<PendingTask> tasks = new ArrayList<>();
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tasks.add(new PendingTask(rs.getLong("id"), rs.getLong("tenant_id")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取待导出任务失败", ex);
        }
        return tasks;
    }

    private Connection ownerConnection() throws SQLException {
        String url = dataSourceProperties.getUrl();
        String username = dataSourceProperties.getUsername();
        String password = dataSourceProperties.getPassword();
        return DriverManager.getConnection(url, username == null ? "" : username,
                password == null ? "" : password);
    }

    private record PendingTask(long id, long tenantId) {
    }
}
