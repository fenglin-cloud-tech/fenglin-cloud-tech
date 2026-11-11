package com.fenglin.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
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
    private final DataSource dataSource;
    private final boolean enabled;

    public SearchIndexTaskJob(SearchIndexTaskService indexTaskService,
                              DataSource dataSource,
                              @Value("${fenglin.search.index-task-job-enabled:true}") boolean enabled) {
        this.indexTaskService = indexTaskService;
        this.dataSource = dataSource;
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
        String sql = "SELECT task_id, tenant_id FROM fenglin_hub.job_list_pending_search_index_tasks(?)";
        List<PendingTask> tasks = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, 10);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    tasks.add(new PendingTask(rs.getLong("task_id"), rs.getLong("tenant_id")));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取待执行索引任务失败", ex);
        }
        return tasks;
    }

    private record PendingTask(long id, long tenantId) {
    }
}
