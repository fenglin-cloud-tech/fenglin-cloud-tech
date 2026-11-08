package com.fenglin.search.service;

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
 * 索引任务轮询：跨租户发现 PENDING 任务并逐租户执行。
 */
@Component
public class SearchIndexTaskJob {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexTaskJob.class);

    private final SearchIndexTaskService indexTaskService;
    private final DataSourceProperties dataSourceProperties;
    private final boolean enabled;

    public SearchIndexTaskJob(SearchIndexTaskService indexTaskService,
                              DataSourceProperties dataSourceProperties,
                              @Value("${fenglin.search.index-task-job-enabled:true}") boolean enabled) {
        this.indexTaskService = indexTaskService;
        this.dataSourceProperties = dataSourceProperties;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${fenglin.search.index-task-poll-interval-ms:5000}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.error("索引任务轮询失败", ex);
        }
    }

    public int runOnce() {
        List<PendingTask> tasks = pendingTasks();
        for (PendingTask task : tasks) {
            indexTaskService.process(task.id(), task.tenantId());
        }
        return tasks.size();
    }

    private List<PendingTask> pendingTasks() {
        String sql = "SELECT id, tenant_id FROM fenglin_hub.hub_search_index_task"
                + " WHERE status = 'PENDING' AND deleted = 0 ORDER BY created_at LIMIT 10";
        List<PendingTask> tasks = new ArrayList<>();
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tasks.add(new PendingTask(rs.getLong("id"), rs.getLong("tenant_id")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取待执行索引任务失败", ex);
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
