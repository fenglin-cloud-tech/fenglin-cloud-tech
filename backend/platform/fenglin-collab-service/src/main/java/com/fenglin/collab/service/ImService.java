package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.ImMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.ConversationCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ConversationDetailView;
import com.fenglin.collab.web.dto.CollabDtos.ConversationView;
import com.fenglin.collab.web.dto.CollabDtos.MemberAddRequest;
import com.fenglin.collab.web.dto.CollabDtos.MemberView;
import com.fenglin.collab.web.dto.CollabDtos.MessagePage;
import com.fenglin.collab.web.dto.CollabDtos.MessageSendRequest;
import com.fenglin.collab.web.dto.CollabDtos.MessageView;
import com.fenglin.collab.web.dto.CollabDtos.ReadResult;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IM 主链：会话、成员、消息发送（幂等/序号有序）、已读、撤回、表情、检索。
 *
 * <p>检索兜底：文档要求 ES 为主路径、PG 摘要检索为降级路径；本域首版直接使用 PG 摘要 ILIKE，
 * ES 索引在搜索域接入后补齐（标注 G-13 类缺口）。</p>
 */
@Service
public class ImService {

    private static final Set<String> CONV_TYPES = Set.of("SINGLE", "GROUP", "TOPIC");
    private static final Set<String> MSG_TYPES = Set.of(
            "TEXT", "RICH", "IMAGE", "FILE", "AUDIO", "VIDEO", "CARD", "SYSTEM");

    private final ImMapper imMapper;
    private final CollabCrypto crypto;
    private final CollabAudit audit;
    private final CollabSettingService settingService;
    private final int groupMemberLimit;

    public ImService(ImMapper imMapper, CollabCrypto crypto, CollabAudit audit,
                     CollabSettingService settingService,
                     @Value("${fenglin.collab.group-member-limit:2000}") int groupMemberLimit) {
        this.imMapper = imMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.settingService = settingService;
        this.groupMemberLimit = groupMemberLimit;
    }

    @Transactional
    public ConversationDetailView createConversation(ConversationCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        String convType = request.convType() == null ? "GROUP" : request.convType();
        if (!CONV_TYPES.contains(convType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "会话类型必须为 SINGLE/GROUP/TOPIC");
        }
        LinkedHashSet<Long> members = new LinkedHashSet<>();
        members.add(userId);
        if (request.memberIds() != null) {
            request.memberIds().stream().filter(id -> id != null && id > 0).forEach(members::add);
        }
        if ("SINGLE".equals(convType) && members.size() != 2) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "单聊必须且只能包含两名成员");
        }
        if (members.size() > groupMemberLimit) {
            throw new BizException(CollabErrorCode.MEMBER_LIMIT,
                    "会话成员数超过上限 " + groupMemberLimit);
        }
        String conversationId = UUID.randomUUID().toString();
        String title = request.title();
        if (title == null || title.isBlank()) {
            title = "SINGLE".equals(convType) ? "单聊" : "未命名群聊";
        }
        imMapper.insertConversation(conversationId, tenantId, convType, title, userId, members.size(),
                request.announcement(), "INTERNAL", userId);
        for (Long memberId : members) {
            imMapper.insertMember(IdWorker.getId(), tenantId, conversationId, memberId,
                    memberId == userId ? "OWNER" : "MEMBER", userId);
        }
        audit.publish("COLLAB_CONVERSATION_CREATED", "CREATE", "collab_conversation", conversationId, "LOW");
        return detail(conversationId);
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationView> listConversations(String keyword, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<ConversationView> records = imMapper
                .listConversations(tenantId, userId, keyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toConversationView).toList();
        return PageResult.of(records, imMapper.countConversations(tenantId, userId, keyword), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ConversationDetailView detail(String conversationId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> row = imMapper.findConversation(tenantId, conversationId);
        if (row == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        requireMember(tenantId, conversationId, userId);
        List<MemberView> members = imMapper.listMembers(tenantId, conversationId).stream()
                .map(item -> new MemberView(Rows.lngVal(item, "memberId"), Rows.str(item, "role"),
                        Rows.intVal(item, "mute") == 1, Rows.intVal(item, "pinned") == 1,
                        Rows.str(item, "memberStatus"), Rows.instant(item, "joinAt")))
                .toList();
        long unread = imMapper.countUnread(tenantId, conversationId, userId);
        ConversationView view = new ConversationView(conversationId, Rows.str(row, "convType"),
                Rows.str(row, "title"), Rows.intVal(row, "memberCount"), Rows.str(row, "status"),
                Rows.lng(row, "ownerId"), null, null, unread, false, false,
                Rows.instant(row, "createdAt"));
        return new ConversationDetailView(view, members);
    }

    @Transactional
    public List<MemberView> addMembers(String conversationId, MemberAddRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        imMapper.lockConversation(tenantId, conversationId);
        Map<String, Object> conversation = imMapper.findConversation(tenantId, conversationId);
        if (conversation == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        requireAdmin(tenantId, conversationId, userId);
        List<Long> candidates = request == null || request.memberIds() == null ? List.of() : request.memberIds();
        for (Long memberId : candidates) {
            if (memberId == null || imMapper.findMember(tenantId, conversationId, memberId) != null) {
                continue;
            }
            if (imMapper.countMembers(tenantId, conversationId) >= groupMemberLimit) {
                throw new BizException(CollabErrorCode.MEMBER_LIMIT, "会话成员数超过上限 " + groupMemberLimit);
            }
            imMapper.insertMember(IdWorker.getId(), tenantId, conversationId, memberId, "MEMBER", userId);
        }
        int count = imMapper.countMembers(tenantId, conversationId);
        imMapper.updateMemberCount(tenantId, conversationId, count, userId);
        audit.publish("COLLAB_CONVERSATION_MEMBER_ADDED", "UPDATE", "collab_conversation", conversationId, "LOW");
        return imMapper.listMembers(tenantId, conversationId).stream()
                .map(item -> new MemberView(Rows.lngVal(item, "memberId"), Rows.str(item, "role"),
                        Rows.intVal(item, "mute") == 1, Rows.intVal(item, "pinned") == 1,
                        Rows.str(item, "memberStatus"), Rows.instant(item, "joinAt")))
                .toList();
    }

    @Transactional
    public void removeMember(String conversationId, long memberId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> conversation = imMapper.findConversation(tenantId, conversationId);
        if (conversation == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        requireAdmin(tenantId, conversationId, userId);
        if (memberId == Rows.lngVal(conversation, "ownerId")) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "群主不可被移除");
        }
        if (imMapper.removeMember(tenantId, conversationId, memberId, "KICKED", userId) == 0) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "成员不存在");
        }
        imMapper.updateMemberCount(tenantId, conversationId, imMapper.countMembers(tenantId, conversationId), userId);
        audit.publish("COLLAB_CONVERSATION_MEMBER_REMOVED", "UPDATE", "collab_conversation", conversationId, "MEDIUM");
    }

    /**
     * 缺口补齐 API-IM-012：成员主动退出会话。
     */
    @Transactional
    public void leave(String conversationId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> conversation = imMapper.findConversation(tenantId, conversationId);
        if (conversation == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (userId == Rows.lngVal(conversation, "ownerId")) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "群主需先转让群主或解散群聊");
        }
        if (imMapper.removeMember(tenantId, conversationId, userId, "QUIT", userId) == 0) {
            throw new BizException(CollabErrorCode.NOT_MEMBER);
        }
        imMapper.updateMemberCount(tenantId, conversationId, imMapper.countMembers(tenantId, conversationId), userId);
        audit.publish("COLLAB_CONVERSATION_LEFT", "UPDATE", "collab_conversation", conversationId, "LOW");
    }

    /**
     * 缺口补齐 API-IM-013：群主解散会话。
     */
    @Transactional
    public void dissolve(String conversationId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> conversation = imMapper.findConversation(tenantId, conversationId);
        if (conversation == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (userId != Rows.lngVal(conversation, "ownerId")) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅群主可解散会话");
        }
        imMapper.dissolveConversation(tenantId, conversationId, userId);
        audit.publish("COLLAB_CONVERSATION_DISSOLVED", "DELETE", "collab_conversation", conversationId, "MEDIUM");
    }

    @Transactional
    public MessageView sendMessage(String conversationId, MessageSendRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.clientMsgId() == null || request.clientMsgId().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "client_msg_id 不能为空");
        }
        Map<String, Object> conversation = imMapper.findConversation(tenantId, conversationId);
        if (conversation == null) {
            throw new BizException(CollabErrorCode.CONVERSATION_NOT_FOUND);
        }
        requireMember(tenantId, conversationId, userId);
        Map<String, Object> existing = imMapper.findByClientMsgId(tenantId, conversationId, request.clientMsgId());
        if (existing != null) {
            return toMessageView(existing);
        }
        String msgType = request.msgType() == null ? "TEXT" : request.msgType();
        if (!MSG_TYPES.contains(msgType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "不支持的消息类型: " + msgType);
        }
        if ("TEXT".equals(msgType) && (request.content() == null || request.content().isBlank())) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "文本消息内容不能为空");
        }
        String content = request.content();
        if ("TEXT".equals(msgType) || "RICH".equals(msgType)) {
            content = settingService.applySensitiveFilter(content);
        }
        String cipher = crypto.encrypt(tenantId, "collab_message", "content", content);
        String hash = crypto.hash(tenantId, content);
        String summary = crypto.summary(msgType, content);
        String mentionIds = request.mentionIds() == null || request.mentionIds().isEmpty()
                ? null : request.mentionIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b)
                .map(value -> "{" + value + "}").orElse(null);
        OffsetDateTime sentAt = OffsetDateTime.now(ZoneOffset.UTC);
        long messageId = IdWorker.getId();
        imMapper.lockConversation(tenantId, conversationId);
        long seq = imMapper.nextSeq(tenantId, conversationId);
        imMapper.insertMessage(messageId, tenantId, conversationId, userId, msgType, cipher, hash, summary,
                request.fileId(), request.replyToId(), request.clientMsgId(), seq, mentionIds, sentAt);
        imMapper.updateLastMessage(tenantId, conversationId, messageId, sentAt, userId);
        imMapper.updateReadCursor(tenantId, conversationId, userId, messageId);
        audit.publish("COLLAB_MESSAGE_SENT", "CREATE", "collab_message", messageId, "LOW");
        return toMessageView(imMapper.findMessage(tenantId, messageId));
    }

    @Transactional(readOnly = true)
    public MessagePage listMessages(String conversationId, Long cursor, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        requireMember(tenantId, conversationId, userId);
        int safeSize = size == null ? 50 : Math.min(Math.max(size, 1), 200);
        List<Map<String, Object>> rows = imMapper.listMessages(tenantId, conversationId, cursor, safeSize);
        List<MessageView> ascending = new ArrayList<>(rows.stream().map(this::toMessageView).toList());
        java.util.Collections.reverse(ascending);
        Long nextCursor = rows.isEmpty() ? null : Rows.lngVal(rows.get(rows.size() - 1), "seq");
        return new MessagePage(ascending, nextCursor);
    }

    @Transactional
    public void revokeMessage(long messageId, String reason) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> message = imMapper.findMessage(tenantId, messageId);
        if (message == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }
        String conversationId = Rows.str(message, "conversationId");
        Map<String, Object> member = imMapper.findMember(tenantId, conversationId, userId);
        if (member == null) {
            throw new BizException(CollabErrorCode.NOT_MEMBER);
        }
        if (Rows.lngVal(message, "senderId") != userId
                && !"OWNER".equals(Rows.str(member, "role")) && !"ADMIN".equals(Rows.str(member, "role"))) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅发送者或群管理员可撤回");
        }
        if (imMapper.revokeMessage(tenantId, messageId, Rows.lngVal(message, "senderId"), reason) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "消息已撤回或不允许撤回");
        }
        audit.publish("COLLAB_MESSAGE_REVOKED", "UPDATE", "collab_message", messageId, "MEDIUM");
    }

    @Transactional
    public ReadResult markRead(String conversationId, long messageId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        requireMember(tenantId, conversationId, userId);
        Map<String, Object> message = imMapper.findMessage(tenantId, messageId);
        if (message == null || !conversationId.equals(Rows.str(message, "conversationId"))) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }
        imMapper.insertRead(IdWorker.getId(), tenantId, conversationId, messageId, userId);
        imMapper.updateReadCursor(tenantId, conversationId, userId, messageId);
        return new ReadResult(conversationId, imMapper.countUnread(tenantId, conversationId, userId));
    }

    @Transactional
    public void react(long messageId, String emoji) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (emoji == null || emoji.isBlank() || emoji.length() > 32) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "表情不能为空且不超过 32 字符");
        }
        Map<String, Object> message = imMapper.findMessage(tenantId, messageId);
        if (message == null || Rows.intVal(message, "revoked") == 1) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "消息不存在或已撤回");
        }
        requireMember(tenantId, Rows.str(message, "conversationId"), userId);
        imMapper.insertReaction(IdWorker.getId(), tenantId, messageId, userId, emoji);
    }

    @Transactional(readOnly = true)
    public PageResult<MessageView> search(String conversationId, Long senderId, String msgType, String keyword,
                                          Long startMs, Long endMs, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<MessageView> records = imMapper
                .searchMessages(tenantId, userId, conversationId, senderId, msgType, keyword, startMs, endMs,
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toMessageView).toList();
        return PageResult.of(records, imMapper.countSearch(tenantId, userId, conversationId, senderId, msgType,
                keyword, startMs, endMs), safePage, safeSize);
    }

    private void requireMember(long tenantId, String conversationId, long userId) {
        if (imMapper.findMember(tenantId, conversationId, userId) == null) {
            throw new BizException(CollabErrorCode.NOT_MEMBER);
        }
    }

    private void requireAdmin(long tenantId, String conversationId, long userId) {
        Map<String, Object> member = imMapper.findMember(tenantId, conversationId, userId);
        if (member == null) {
            throw new BizException(CollabErrorCode.NOT_MEMBER);
        }
        String role = Rows.str(member, "role");
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅群主或管理员可管理成员");
        }
    }

    private ConversationView toConversationView(Map<String, Object> row) {
        return new ConversationView(Rows.str(row, "conversationId"), Rows.str(row, "convType"),
                Rows.str(row, "title"), Rows.intVal(row, "memberCount"), Rows.str(row, "status"),
                Rows.lng(row, "ownerId"), Rows.str(row, "lastMessageSummary"),
                Rows.instant(row, "lastMessageAt"), Rows.lngVal(row, "unreadCount"),
                Rows.intVal(row, "pinned") == 1, Rows.intVal(row, "mute") == 1,
                Rows.instant(row, "createdAt"));
    }

    private MessageView toMessageView(Map<String, Object> row) {
        return new MessageView(Rows.lngVal(row, "messageId"), Rows.str(row, "conversationId"),
                Rows.lngVal(row, "senderId"), Rows.str(row, "msgType"), Rows.str(row, "summary"),
                Rows.str(row, "fileId"), Rows.lng(row, "replyToId"), Rows.lngVal(row, "seq"),
                Rows.intVal(row, "revoked") == 1, Rows.instant(row, "sentAtTimestamp"));
    }
}
