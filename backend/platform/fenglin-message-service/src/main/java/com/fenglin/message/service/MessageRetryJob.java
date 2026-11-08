package com.fenglin.message.service;

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

/**
 * 失败消息重试：WS 离线导致的失败按指数退避重试，最多 max-retry 次。
 * 任务跨租户扫描，使用库属主连接绕过 RLS（仅平台内部作业）。
 */
@Component
public class MessageRetryJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetryJob.class);

    private final DataSourceProperties dataSourceProperties;
    private final MessageDispatcher dispatcher;
    private final boolean enabled;
    private final int maxRetry;

    public MessageRetryJob(DataSourceProperties dataSourceProperties,
                           MessageDispatcher dispatcher,
                           @Value("${fenglin.message.retry-job-enabled:true}") boolean enabled,
                           @Value("${fenglin.message.max-retry:6}") int maxRetry) {
        this.dataSourceProperties = dataSourceProperties;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.maxRetry = maxRetry;
    }

    @Scheduled(fixedDelay = 30_000L)
    public void retryFailed() {
        if (!enabled) {
            return;
        }
        try (Connection connection = ownerConnection()) {
            int retried = retry(connection);
            if (retried > 0) {
                log.info("消息重试完成，处理 {} 条", retried);
            }
        } catch (Exception ex) {
            log.warn("消息重试任务执行失败", ex);
        }
    }

    private int retry(Connection connection) throws Exception {
        int processed = 0;
        String sql = """
                SELECT m.id, m.tenant_id, m.receiver_id, m.channel, m.title, m.content
                  FROM fenglin_hub.hub_message m
                 WHERE m.status = 'FAILED' AND m.channel = 'WS'
                   AND m.created_at > now() - interval '1 day'
                   AND (SELECT count(*) FROM fenglin_hub.hub_message_receipt r
                         WHERE r.message_id = m.id) < ?
                 ORDER BY m.created_at
                 LIMIT 100
                """;
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, maxRetry);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long messageId = rs.getLong("id");
                    long tenantId = rs.getLong("tenant_id");
                    long receiverId = rs.getLong("receiver_id");
                    MessageDispatcher.DispatchResult result = dispatcher.dispatch(tenantId, receiverId,
                            rs.getString("channel"), messageId, rs.getString("title"), rs.getString("content"));
                    int attempts = attempts(connection, messageId) + 1;
                    insertReceipt(connection, tenantId, messageId, result, attempts);
                    if ("SENT".equals(result.messageStatus())) {
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE fenglin_hub.hub_message SET status = 'SENT', sent_at = now(), "
                                        + "updated_at = now(), version = version + 1 WHERE id = ?")) {
                            update.setLong(1, messageId);
                            update.executeUpdate();
                        }
                    }
                    processed++;
                }
            }
        }
        return processed;
    }

    private int attempts(Connection connection, long messageId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM fenglin_hub.hub_message_receipt WHERE message_id = ?")) {
            statement.setLong(1, messageId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void insertReceipt(Connection connection, long tenantId, long messageId,
                               MessageDispatcher.DispatchResult result, int attempts) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO fenglin_hub.hub_message_receipt "
                        + "(id, tenant_id, message_id, status, retry_count, error_msg, created_at, updated_at, deleted, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0, 0)")) {
            insert.setLong(1, com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            insert.setLong(2, tenantId);
            insert.setLong(3, messageId);
            insert.setString(4, result.receiptStatus());
            insert.setInt(5, attempts);
            insert.setString(6, result.error());
            insert.executeUpdate();
        }
    }

    private Connection ownerConnection() throws Exception {
        String url = dataSourceProperties.getUrl();
        String username = dataSourceProperties.getUsername();
        String password = dataSourceProperties.getPassword();
        return DriverManager.getConnection(url, username == null ? "" : username,
                password == null ? "" : password);
    }
}
