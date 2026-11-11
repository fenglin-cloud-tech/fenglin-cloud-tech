package com.fenglin.message.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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

/**
 * 失败消息重试：WS 离线导致的失败按指数退避重试，最多 max-retry 次。
 * 跨租户候选发现与回执/状态回写均走 SECURITY DEFINER 窄函数，
 * 连接取自服务连接池（connection-init-sql: SET ROLE fl_app），不再使用库属主裸连接。
 */
@Component
public class MessageRetryJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetryJob.class);

    private final DataSource dataSource;
    private final MessageDispatcher dispatcher;
    private final boolean enabled;
    private final int maxRetry;

    public MessageRetryJob(DataSource dataSource,
                           MessageDispatcher dispatcher,
                           @Value("${fenglin.message.retry-job-enabled:true}") boolean enabled,
                           @Value("${fenglin.message.max-retry:6}") int maxRetry) {
        this.dataSource = dataSource;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.maxRetry = maxRetry;
    }

    @Scheduled(fixedDelay = 30_000L)
    public void retryFailed() {
        if (!enabled) {
            return;
        }
        try {
            int retried = retry();
            if (retried > 0) {
                log.info("消息重试完成，处理 {} 条", retried);
            }
        } catch (Exception ex) {
            log.warn("消息重试任务执行失败", ex);
        }
    }

    private int retry() throws SQLException {
        int processed = 0;
        String sql = "SELECT message_id, tenant_id, receiver_id, channel, title, content"
                + " FROM fenglin_hub.job_claim_failed_ws_messages(?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, maxRetry);
            select.setInt(2, 100);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long messageId = rs.getLong("message_id");
                    long tenantId = rs.getLong("tenant_id");
                    long receiverId = rs.getLong("receiver_id");
                    MessageDispatcher.DispatchResult result = dispatcher.dispatch(tenantId, receiverId,
                            rs.getString("channel"), messageId, rs.getString("title"), rs.getString("content"));
                    complete(connection, messageId, result);
                    processed++;
                }
            }
        }
        return processed;
    }

    /**
     * 回执写入与 FAILED → SENT 状态回写由函数在同一调用内完成；
     * tenant_id 由函数按 message_id 回查，调用方不传。
     */
    private void complete(Connection connection, long messageId,
                          MessageDispatcher.DispatchResult result) throws SQLException {
        String sql = "SELECT retry_count, marked_sent"
                + " FROM fenglin_hub.job_complete_message_retry(?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            statement.setLong(2, IdWorker.getId());
            statement.setString(3, result.receiptStatus());
            statement.setString(4, result.error());
            statement.setBoolean(5, "SENT".equals(result.messageStatus()));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    log.warn("消息重试未写入回执（消息不存在或已非 FAILED/WS）：messageId={}", messageId);
                }
            }
        }
    }
}
