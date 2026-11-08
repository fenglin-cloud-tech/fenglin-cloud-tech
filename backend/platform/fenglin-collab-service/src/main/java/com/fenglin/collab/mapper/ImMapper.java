package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * IM 域 Mapper。所有 SQL 带 tenant_id 条件，与 RLS 形成双重隔离。
 */
@Mapper
public interface ImMapper {

    @Select("""
            SELECT c.id AS "conversationId", c.conv_type AS "convType", c.title AS "title",
                   c.member_count AS "memberCount", c.status AS "status", c.owner_id AS "ownerId",
                   m.summary AS "lastMessageSummary",
                   (EXTRACT(EPOCH FROM c.last_message_at) * 1000)::bigint AS "lastMessageAt",
                   cm.pinned AS "pinned", cm.mute AS "mute",
                   (EXTRACT(EPOCH FROM c.created_at) * 1000)::bigint AS "createdAt",
                   (SELECT count(1) FROM collab_message msg
                     WHERE msg.tenant_id = c.tenant_id AND msg.conversation_id = c.id
                       AND msg.deleted = 0 AND msg.revoked = 0 AND msg.sender_id <> #{userId}
                       AND (cm.last_read_message_id IS NULL
                            OR msg.seq > (SELECT r.seq FROM collab_message r WHERE r.id = cm.last_read_message_id))
                   ) AS "unreadCount"
            FROM collab_conversation c
            JOIN collab_conversation_member cm
              ON cm.conversation_id = c.id AND cm.tenant_id = c.tenant_id
             AND cm.member_id = #{userId} AND cm.deleted = 0
            LEFT JOIN collab_message m ON m.id = c.last_message_id AND m.tenant_id = c.tenant_id
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0 AND c.status <> 'DISSOLVED'
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR c.title ILIKE '%' || #{keyword} || '%')
            ORDER BY cm.pinned DESC, c.last_message_at DESC NULLS LAST
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listConversations(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                                @Param("keyword") String keyword, @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_conversation c
            JOIN collab_conversation_member cm
              ON cm.conversation_id = c.id AND cm.tenant_id = c.tenant_id
             AND cm.member_id = #{userId} AND cm.deleted = 0
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0 AND c.status <> 'DISSOLVED'
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR c.title ILIKE '%' || #{keyword} || '%')
            """)
    long countConversations(@Param("tenantId") long tenantId, @Param("userId") long userId,
                            @Param("keyword") String keyword);

    @Select("""
            SELECT id AS "conversationId", conv_type AS "convType", title AS "title", owner_id AS "ownerId",
                   member_count AS "memberCount", announcement AS "announcement", status AS "status",
                   source AS "source", (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_conversation
            WHERE tenant_id = #{tenantId} AND id = CAST(#{convId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findConversation(@Param("tenantId") long tenantId, @Param("convId") String convId);

    @Select("""
            SELECT id AS "conversationId" FROM collab_conversation
            WHERE tenant_id = #{tenantId} AND id = CAST(#{convId} AS uuid) AND deleted = 0
            FOR UPDATE
            """)
    String lockConversation(@Param("tenantId") long tenantId, @Param("convId") String convId);

    @Insert("""
            INSERT INTO collab_conversation
                (id, tenant_id, conv_type, title, owner_id, member_count, announcement, status, source, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{convType}, #{title,jdbcType=VARCHAR}, #{ownerId},
                    #{memberCount}, #{announcement,jdbcType=VARCHAR}, 'ACTIVE', #{source}, #{userId})
            """)
    void insertConversation(@Param("id") String id, @Param("tenantId") long tenantId,
                            @Param("convType") String convType, @Param("title") String title,
                            @Param("ownerId") Long ownerId, @Param("memberCount") int memberCount,
                            @Param("announcement") String announcement, @Param("source") String source,
                            @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_conversation_member
                (id, tenant_id, conversation_id, member_id, role, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{convId} AS uuid), #{memberId}, #{role}, #{userId})
            """)
    void insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("convId") String convId,
                      @Param("memberId") long memberId, @Param("role") String role, @Param("userId") long userId);

    @Select("""
            SELECT member_id AS "memberId", role AS "role", mute AS "mute", pinned AS "pinned",
                   member_status AS "memberStatus", (EXTRACT(EPOCH FROM join_at) * 1000)::bigint AS "joinAt"
            FROM collab_conversation_member
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid) AND deleted = 0
            ORDER BY join_at
            """)
    List<Map<String, Object>> listMembers(@Param("tenantId") long tenantId, @Param("convId") String convId);

    @Select("""
            SELECT member_id AS "memberId", role AS "role", member_status AS "memberStatus",
                   last_read_message_id AS "lastReadId"
            FROM collab_conversation_member
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid)
              AND member_id = #{memberId} AND deleted = 0
            """)
    Map<String, Object> findMember(@Param("tenantId") long tenantId, @Param("convId") String convId,
                                   @Param("memberId") long memberId);

    @Select("""
            SELECT count(1) FROM collab_conversation_member
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid) AND deleted = 0
            """)
    int countMembers(@Param("tenantId") long tenantId, @Param("convId") String convId);

    @Update("""
            UPDATE collab_conversation SET member_count = #{memberCount}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{convId} AS uuid)
            """)
    void updateMemberCount(@Param("tenantId") long tenantId, @Param("convId") String convId,
                           @Param("memberCount") int memberCount, @Param("userId") long userId);

    @Update("""
            UPDATE collab_conversation_member SET member_status = #{status}, deleted = 1, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid)
              AND member_id = #{memberId} AND deleted = 0
            """)
    int removeMember(@Param("tenantId") long tenantId, @Param("convId") String convId,
                     @Param("memberId") long memberId, @Param("status") String status,
                     @Param("userId") long userId);

    @Update("""
            UPDATE collab_conversation SET status = 'DISSOLVED', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{convId} AS uuid) AND deleted = 0
            """)
    void dissolveConversation(@Param("tenantId") long tenantId, @Param("convId") String convId,
                              @Param("userId") long userId);

    @Select("""
            SELECT COALESCE(MAX(seq), 0) + 1 AS seq FROM collab_message
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid)
            """)
    long nextSeq(@Param("tenantId") long tenantId, @Param("convId") String convId);

    @Insert("""
            INSERT INTO collab_message
                (id, tenant_id, conversation_id, sender_id, msg_type, content_cipher, content_hash, summary,
                 file_id, reply_to_id, client_msg_id, seq, mention_ids, sent_at, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{conversationId} AS uuid), #{senderId}, #{msgType},
                    #{contentCipher,jdbcType=VARCHAR}, #{contentHash,jdbcType=VARCHAR},
                    #{summary,jdbcType=VARCHAR}, CAST(#{fileId,jdbcType=VARCHAR} AS uuid),
                    #{replyToId,jdbcType=BIGINT}, #{clientMsgId}, #{seq},
                    CAST(#{mentionIds,jdbcType=VARCHAR} AS bigint[]), #{sentAt}, #{senderId})
            """)
    void insertMessage(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("conversationId") String conversationId, @Param("senderId") long senderId,
                       @Param("msgType") String msgType, @Param("contentCipher") String contentCipher,
                       @Param("contentHash") String contentHash, @Param("summary") String summary,
                       @Param("fileId") String fileId, @Param("replyToId") Long replyToId,
                       @Param("clientMsgId") String clientMsgId, @Param("seq") long seq,
                       @Param("mentionIds") String mentionIds, @Param("sentAt") OffsetDateTime sentAt);

    @Update("""
            UPDATE collab_conversation
            SET last_message_id = #{messageId}, last_message_at = #{sentAt}, updated_by = #{senderId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{convId} AS uuid)
            """)
    void updateLastMessage(@Param("tenantId") long tenantId, @Param("convId") String convId,
                           @Param("messageId") long messageId, @Param("sentAt") OffsetDateTime sentAt,
                           @Param("senderId") long senderId);

    @Select("""
            SELECT id AS "messageId", conversation_id AS "conversationId", sender_id AS "senderId",
                   msg_type AS "msgType", summary AS "summary", CAST(file_id AS varchar) AS "fileId",
                   reply_to_id AS "replyToId", seq AS "seq", revoked AS "revoked",
                   (EXTRACT(EPOCH FROM sent_at) * 1000)::bigint AS "sentAtTimestamp"
            FROM collab_message
            WHERE tenant_id = #{tenantId} AND id = #{messageId} AND deleted = 0
            """)
    Map<String, Object> findMessage(@Param("tenantId") long tenantId, @Param("messageId") long messageId);

    @Select("""
            SELECT id AS "messageId", conversation_id AS "conversationId", sender_id AS "senderId",
                   msg_type AS "msgType", summary AS "summary", CAST(file_id AS varchar) AS "fileId",
                   reply_to_id AS "replyToId", seq AS "seq", revoked AS "revoked",
                   (EXTRACT(EPOCH FROM sent_at) * 1000)::bigint AS "sentAtTimestamp"
            FROM collab_message
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid)
              AND client_msg_id = #{clientMsgId} AND deleted = 0
            """)
    Map<String, Object> findByClientMsgId(@Param("tenantId") long tenantId, @Param("convId") String convId,
                                          @Param("clientMsgId") String clientMsgId);

    @Select("""
            SELECT id AS "messageId", conversation_id AS "conversationId", sender_id AS "senderId",
                   msg_type AS "msgType", summary AS "summary", CAST(file_id AS varchar) AS "fileId",
                   reply_to_id AS "replyToId", seq AS "seq", revoked AS "revoked",
                   (EXTRACT(EPOCH FROM sent_at) * 1000)::bigint AS "sentAtTimestamp"
            FROM collab_message
            WHERE tenant_id = #{tenantId} AND conversation_id = CAST(#{convId} AS uuid) AND deleted = 0
              AND (CAST(#{cursor,jdbcType=BIGINT} AS bigint) IS NULL OR seq < #{cursor})
            ORDER BY seq DESC
            LIMIT #{size}
            """)
    List<Map<String, Object>> listMessages(@Param("tenantId") long tenantId, @Param("convId") String convId,
                                           @Param("cursor") Long cursor, @Param("size") int size);

    @Update("""
            UPDATE collab_message SET revoked = 1, revoked_at = now(), revoked_reason = #{reason,jdbcType=VARCHAR},
                   updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{messageId} AND sender_id = #{userId} AND deleted = 0 AND revoked = 0
            """)
    int revokeMessage(@Param("tenantId") long tenantId, @Param("messageId") long messageId,
                      @Param("userId") long userId, @Param("reason") String reason);

    @Insert("""
            INSERT INTO collab_message_read (id, tenant_id, conversation_id, message_id, reader_id, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{convId} AS uuid), #{messageId}, #{userId}, #{userId})
            ON CONFLICT (tenant_id, message_id, reader_id) DO NOTHING
            """)
    void insertRead(@Param("id") long id, @Param("tenantId") long tenantId, @Param("convId") String convId,
                    @Param("messageId") long messageId, @Param("userId") long userId);

    @Update("""
            UPDATE collab_conversation_member cm
            SET last_read_message_id = m.id, updated_by = #{userId}
            FROM collab_message m
            WHERE cm.tenant_id = #{tenantId} AND cm.conversation_id = CAST(#{convId} AS uuid)
              AND cm.member_id = #{userId} AND cm.deleted = 0
              AND m.tenant_id = #{tenantId} AND m.id = #{messageId}
              AND (cm.last_read_message_id IS NULL
                   OR m.seq > (SELECT r.seq FROM collab_message r WHERE r.id = cm.last_read_message_id))
            """)
    void updateReadCursor(@Param("tenantId") long tenantId, @Param("convId") String convId,
                          @Param("userId") long userId, @Param("messageId") long messageId);

    @Select("""
            SELECT count(1) FROM collab_message msg
            JOIN collab_conversation_member cm
              ON cm.conversation_id = msg.conversation_id AND cm.tenant_id = msg.tenant_id
             AND cm.member_id = #{userId} AND cm.deleted = 0
            WHERE msg.tenant_id = #{tenantId} AND msg.conversation_id = CAST(#{convId} AS uuid)
              AND msg.deleted = 0 AND msg.revoked = 0 AND msg.sender_id <> #{userId}
              AND (cm.last_read_message_id IS NULL
                   OR msg.seq > (SELECT r.seq FROM collab_message r WHERE r.id = cm.last_read_message_id))
            """)
    long countUnread(@Param("tenantId") long tenantId, @Param("convId") String convId,
                     @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_message_reaction (id, tenant_id, message_id, user_id, emoji, created_by)
            VALUES (#{id}, #{tenantId}, #{messageId}, #{userId}, #{emoji}, #{userId})
            ON CONFLICT (tenant_id, message_id, user_id, emoji) DO NOTHING
            """)
    void insertReaction(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("messageId") long messageId, @Param("userId") long userId,
                        @Param("emoji") String emoji);

    @Select("""
            SELECT m.id AS "messageId", m.conversation_id AS "conversationId", m.sender_id AS "senderId",
                   m.msg_type AS "msgType", m.summary AS "summary", CAST(m.file_id AS varchar) AS "fileId",
                   m.reply_to_id AS "replyToId", m.seq AS "seq", m.revoked AS "revoked",
                   (EXTRACT(EPOCH FROM m.sent_at) * 1000)::bigint AS "sentAtTimestamp"
            FROM collab_message m
            JOIN collab_conversation_member cm
              ON cm.conversation_id = m.conversation_id AND cm.tenant_id = m.tenant_id
             AND cm.member_id = #{userId} AND cm.deleted = 0
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.revoked = 0
              AND (CAST(#{convId,jdbcType=VARCHAR} AS uuid) IS NULL
                   OR m.conversation_id = CAST(#{convId} AS uuid))
              AND (CAST(#{senderId,jdbcType=BIGINT} AS bigint) IS NULL OR m.sender_id = #{senderId})
              AND (CAST(#{msgType,jdbcType=VARCHAR} AS varchar) IS NULL OR m.msg_type = #{msgType})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR m.summary ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{startMs,jdbcType=BIGINT} AS bigint) IS NULL
                   OR m.sent_at >= to_timestamp(#{startMs} / 1000.0))
              AND (CAST(#{endMs,jdbcType=BIGINT} AS bigint) IS NULL
                   OR m.sent_at <= to_timestamp(#{endMs} / 1000.0))
            ORDER BY m.sent_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> searchMessages(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                             @Param("convId") String convId, @Param("senderId") Long senderId,
                                             @Param("msgType") String msgType, @Param("keyword") String keyword,
                                             @Param("startMs") Long startMs, @Param("endMs") Long endMs,
                                             @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_message m
            JOIN collab_conversation_member cm
              ON cm.conversation_id = m.conversation_id AND cm.tenant_id = m.tenant_id
             AND cm.member_id = #{userId} AND cm.deleted = 0
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.revoked = 0
              AND (CAST(#{convId,jdbcType=VARCHAR} AS uuid) IS NULL
                   OR m.conversation_id = CAST(#{convId} AS uuid))
              AND (CAST(#{senderId,jdbcType=BIGINT} AS bigint) IS NULL OR m.sender_id = #{senderId})
              AND (CAST(#{msgType,jdbcType=VARCHAR} AS varchar) IS NULL OR m.msg_type = #{msgType})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR m.summary ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{startMs,jdbcType=BIGINT} AS bigint) IS NULL
                   OR m.sent_at >= to_timestamp(#{startMs} / 1000.0))
              AND (CAST(#{endMs,jdbcType=BIGINT} AS bigint) IS NULL
                   OR m.sent_at <= to_timestamp(#{endMs} / 1000.0))
            """)
    long countSearch(@Param("tenantId") long tenantId, @Param("userId") long userId,
                     @Param("convId") String convId, @Param("senderId") Long senderId,
                     @Param("msgType") String msgType, @Param("keyword") String keyword,
                     @Param("startMs") Long startMs, @Param("endMs") Long endMs);
}
