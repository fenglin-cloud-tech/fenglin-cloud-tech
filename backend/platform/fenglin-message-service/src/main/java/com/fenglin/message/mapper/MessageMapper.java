package com.fenglin.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper {

    @Insert("""
            INSERT INTO hub_message (id, tenant_id, message_no, channel, template_code, receiver_id,
                                     title, content, biz_type, biz_id, status, sent_at,
                                     created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{messageNo}, #{channel}, #{templateCode,jdbcType=VARCHAR},
                    #{receiverId}, #{title,jdbcType=VARCHAR}, #{content,jdbcType=VARCHAR},
                    #{bizType,jdbcType=VARCHAR}, #{bizId,jdbcType=VARCHAR}, #{status},
                    #{sentAt,jdbcType=TIMESTAMP}, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("messageNo") String messageNo,
               @Param("channel") String channel,
               @Param("templateCode") String templateCode,
               @Param("receiverId") long receiverId,
               @Param("title") String title,
               @Param("content") String content,
               @Param("bizType") String bizType,
               @Param("bizId") String bizId,
               @Param("status") String status,
               @Param("sentAt") Instant sentAt,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, status FROM hub_message
             WHERE tenant_id = #{tenantId} AND template_code = #{templateCode}
               AND receiver_id = #{receiverId} AND biz_id = #{bizId}
               AND created_at >= #{since} AND deleted = 0
            """)
    List<Map<String, Object>> findDedup(@Param("tenantId") long tenantId,
                                        @Param("templateCode") String templateCode,
                                        @Param("receiverId") long receiverId,
                                        @Param("bizId") String bizId,
                                        @Param("since") Instant since);

    @Select("""
            <script>
            SELECT id, message_no, channel, template_code, title, content, biz_type, biz_id,
                   status, sent_at, read_at, created_at
              FROM hub_message
             WHERE tenant_id = #{tenantId} AND receiver_id = #{receiverId} AND deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="channel != null and channel != ''">AND channel = #{channel}</if>
             ORDER BY created_at DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("receiverId") long receiverId,
                                   @Param("status") String status,
                                   @Param("channel") String channel,
                                   @Param("size") int size,
                                   @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM hub_message
             WHERE tenant_id = #{tenantId} AND receiver_id = #{receiverId} AND deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="channel != null and channel != ''">AND channel = #{channel}</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("receiverId") long receiverId,
               @Param("status") String status,
               @Param("channel") String channel);

    @Select("""
            SELECT count(*) FROM hub_message
             WHERE tenant_id = #{tenantId} AND receiver_id = #{receiverId}
               AND deleted = 0 AND status <> 'READ'
            """)
    long countUnread(@Param("tenantId") long tenantId, @Param("receiverId") long receiverId);

    @Update("""
            UPDATE hub_message
               SET status = 'READ', read_at = COALESCE(read_at, now()), updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND receiver_id = #{receiverId} AND id = #{id}
               AND deleted = 0
            """)
    int markRead(@Param("tenantId") long tenantId,
                 @Param("receiverId") long receiverId,
                 @Param("id") long id);

    @Select("""
            SELECT id, status, read_at FROM hub_message
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE hub_message
               SET status = #{status}, sent_at = #{sentAt}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int updateStatusSent(@Param("tenantId") long tenantId,
                         @Param("id") long id,
                         @Param("status") String status,
                         @Param("sentAt") Instant sentAt);

    @Update("""
            UPDATE hub_message
               SET status = #{status}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int updateStatus(@Param("tenantId") long tenantId,
                     @Param("id") long id,
                     @Param("status") String status);
}
