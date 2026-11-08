package com.fenglin.message.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.message.mapper.MessageMapper;
import com.fenglin.message.mapper.MessagePreferenceMapper;
import com.fenglin.message.mapper.MessageReceiptMapper;
import com.fenglin.message.mapper.MessageTemplateMapper;
import com.fenglin.message.web.dto.MessageListResult;
import com.fenglin.message.web.dto.PreferenceRequest;
import com.fenglin.message.web.dto.PreferenceView;
import com.fenglin.message.web.dto.ReadResult;
import com.fenglin.message.web.dto.SendMessageRequest;
import com.fenglin.message.web.dto.SendMessageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MessageService {

    private static final Set<String> SUPPORTED_CHANNELS = Set.of("STATION", "WS");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern QUIET = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final MessageTemplateMapper templateMapper;
    private final MessageMapper messageMapper;
    private final MessageReceiptMapper receiptMapper;
    private final MessagePreferenceMapper preferenceMapper;
    private final MessageDispatcher dispatcher;
    private final AuditSink auditSink;
    private final Duration dedupWindow;

    public MessageService(MessageTemplateMapper templateMapper,
                          MessageMapper messageMapper,
                          MessageReceiptMapper receiptMapper,
                          MessagePreferenceMapper preferenceMapper,
                          MessageDispatcher dispatcher,
                          AuditSink auditSink,
                          @Value("${fenglin.message.dedup-window:PT24H}") Duration dedupWindow) {
        this.templateMapper = templateMapper;
        this.messageMapper = messageMapper;
        this.receiptMapper = receiptMapper;
        this.preferenceMapper = preferenceMapper;
        this.dispatcher = dispatcher;
        this.auditSink = auditSink;
        this.dedupWindow = dedupWindow;
    }

    @Transactional
    public SendMessageResult send(SendMessageRequest request) {
        long tenantId = TenantContext.requireTenantId();
        List<String> channels = validateChannels(request.channels());
        Map<String, Object> template = templateMapper.find(tenantId, request.templateCode());
        if (template == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "消息模板不存在: " + request.templateCode());
        }
        String title = render((String) template.get("title_tpl"), request.params(), "titleTpl");
        String content = render((String) template.get("content_tpl"), request.params(), "contentTpl");
        boolean forced = isForced(request.templateCode(), request.bizType());
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();

        List<Long> messageIds = new ArrayList<>();
        int suppressed = 0;
        int failed = 0;
        for (Long receiverId : new LinkedHashSet<>(request.receivers())) {
            if (request.bizId() != null && !request.bizId().isBlank()) {
                List<Map<String, Object>> existing = messageMapper.findDedup(tenantId,
                        request.templateCode(), receiverId, request.bizId(),
                        Instant.now().minus(dedupWindow));
                if (!existing.isEmpty()) {
                    existing.forEach(row -> messageIds.add(((Number) row.get("id")).longValue()));
                    continue;
                }
            }
            for (String channel : channels) {
                if (!forced && !allowedByPreference(tenantId, receiverId, request.bizType(), channel)) {
                    suppressed++;
                    continue;
                }
                long messageId = IdWorker.getId();
                String messageNo = "M" + messageId;
                messageMapper.insert(messageId, tenantId, messageNo, channel, request.templateCode(),
                        receiverId, title, content, request.bizType(), request.bizId(), "PENDING", null,
                        operatorId);
                MessageDispatcher.DispatchResult result = dispatcher.dispatch(tenantId, receiverId,
                        channel, messageId, title, content);
                if ("SENT".equals(result.messageStatus())) {
                    messageMapper.updateStatusSent(tenantId, messageId, "SENT", Instant.now());
                } else {
                    messageMapper.updateStatus(tenantId, messageId, "FAILED");
                    failed++;
                }
                receiptMapper.insert(IdWorker.getId(), tenantId, messageId, null,
                        result.receiptStatus(), 0, result.error());
                messageIds.add(messageId);
            }
        }
        audit("MESSAGE_SENT", request.templateCode());
        String status = failed == 0 ? "SENT" : (messageIds.isEmpty() ? "FAILED" : "PARTIAL");
        return new SendMessageResult(messageIds, status, suppressed, failed);
    }

    @Transactional(readOnly = true)
    public MessageListResult list(String status, String channel, Integer page, Integer size) {
        long tenantId = TenantContext.requireTenantId();
        long userId = currentUserId();
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        List<MessageListResult.MessageView> records = messageMapper
                .list(tenantId, userId, normalize(status), normalize(channel), pageSize, (pageNo - 1) * pageSize)
                .stream()
                .map(row -> new MessageListResult.MessageView(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("message_no"),
                        (String) row.get("channel"),
                        (String) row.get("template_code"),
                        (String) row.get("title"),
                        (String) row.get("content"),
                        (String) row.get("biz_type"),
                        (String) row.get("biz_id"),
                        (String) row.get("status"),
                        toInstant(row.get("sent_at")),
                        toInstant(row.get("read_at")),
                        toInstant(row.get("created_at"))))
                .toList();
        long total = messageMapper.count(tenantId, userId, normalize(status), normalize(channel));
        long unread = messageMapper.countUnread(tenantId, userId);
        return new MessageListResult(records, unread, pageNo, pageSize, total);
    }

    @Transactional
    public ReadResult markRead(long messageId) {
        long tenantId = TenantContext.requireTenantId();
        long userId = currentUserId();
        Map<String, Object> existing = messageMapper.findById(tenantId, messageId);
        if (existing == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "消息不存在: " + messageId);
        }
        messageMapper.markRead(tenantId, userId, messageId);
        Map<String, Object> row = messageMapper.findById(tenantId, messageId);
        return new ReadResult(messageId, (String) row.get("status"), toInstant(row.get("read_at")));
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> preferences() {
        long tenantId = TenantContext.requireTenantId();
        long userId = currentUserId();
        Map<String, List<Map<String, Object>>> grouped = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : preferenceMapper.list(tenantId, userId)) {
            grouped.computeIfAbsent((String) row.get("event_type"), key -> new ArrayList<>()).add(row);
        }
        List<PreferenceView> views = new ArrayList<>();
        grouped.forEach((eventType, rows) -> {
            List<String> channels = rows.stream()
                    .filter(row -> ((Number) row.get("enabled")).intValue() == 1)
                    .map(row -> (String) row.get("channel"))
                    .toList();
            String quiet = rows.stream()
                    .map(row -> (String) row.get("quiet_hours"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            String from = null;
            String to = null;
            if (quiet != null && quiet.contains("-")) {
                String[] parts = quiet.split("-", 2);
                from = parts[0];
                to = parts[1];
            }
            views.add(new PreferenceView(eventType, channels, from, to));
        });
        return views;
    }

    @Transactional
    public List<PreferenceView> updatePreferences(PreferenceRequest request) {
        long tenantId = TenantContext.requireTenantId();
        long userId = currentUserId();
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        for (PreferenceRequest.Item item : request.items()) {
            String quietFrom = item.quietHours() == null ? null : item.quietHours().from();
            String quietTo = item.quietHours() == null ? null : item.quietHours().to();
            if (quietFrom != null && !QUIET.matcher(quietFrom).matches()
                    || quietTo != null && !QUIET.matcher(quietTo).matches()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "免打扰时间格式必须为 HH:mm");
            }
            String quietHours = quietFrom == null || quietTo == null ? null : quietFrom + "-" + quietTo;
            for (String channel : item.channels()) {
                if (preferenceMapper.update(tenantId, userId, item.eventType(), channel, 1,
                        quietHours, operatorId) == 0) {
                    preferenceMapper.insert(IdWorker.getId(), tenantId, userId, item.eventType(), channel,
                            1, quietHours, operatorId);
                }
            }
            preferenceMapper.disableOthers(tenantId, userId, item.eventType(), item.channels());
        }
        audit("MESSAGE_PREFERENCE_UPDATED", "user:" + userId);
        return preferences();
    }

    private List<String> validateChannels(List<String> channels) {
        for (String channel : channels) {
            if (!SUPPORTED_CHANNELS.contains(channel)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST,
                        "通道未实现或未配置: " + channel + "（首版支持 STATION/WS）");
            }
        }
        return channels;
    }

    private boolean allowedByPreference(long tenantId, long receiverId, String eventType, String channel) {
        if (eventType == null || eventType.isBlank()) {
            return true;
        }
        List<Map<String, Object>> rows = preferenceMapper.listByEvent(tenantId, receiverId, eventType);
        if (rows.isEmpty()) {
            return true;
        }
        Map<String, Object> row = rows.stream()
                .filter(item -> channel.equals(item.get("channel")))
                .findFirst()
                .orElse(null);
        if (row == null || ((Number) row.get("enabled")).intValue() == 0) {
            return false;
        }
        String quiet = (String) row.get("quiet_hours");
        if (quiet == null || !quiet.contains("-")) {
            return true;
        }
        String[] parts = quiet.split("-", 2);
        return !inQuietHours(parts[0], parts[1]);
    }

    private boolean inQuietHours(String from, String to) {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(from);
        LocalTime end = LocalTime.parse(to);
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private boolean isForced(String templateCode, String bizType) {
        return templateCode.startsWith("security_")
                || (bizType != null && bizType.startsWith("security."));
    }

    private String render(String template, Map<String, String> params, String field) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params == null ? null : params.get(key);
            if (value == null) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "0005001 模板变量缺失: " + key + "（" + field + "）");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private long currentUserId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.userId() == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "缺少用户上下文");
        }
        return snapshot.userId();
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String resourceId) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "message",
                eventType,
                "MEDIUM",
                snapshot == null ? null : snapshot.tenantId(),
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                "MESSAGE",
                resourceId,
                Instant.now()));
    }
}
