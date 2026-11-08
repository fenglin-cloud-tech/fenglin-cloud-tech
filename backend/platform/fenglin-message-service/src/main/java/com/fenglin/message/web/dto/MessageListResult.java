package com.fenglin.message.web.dto;

import java.time.Instant;
import java.util.List;

public record MessageListResult(List<MessageView> records, long unreadCount, int page, int size, long total) {

    public record MessageView(long messageId, String messageNo, String channel, String templateCode,
                              String title, String content, String bizType, String bizId,
                              String status, Instant sentAt, Instant readAt, Instant createdAt) {
    }
}
