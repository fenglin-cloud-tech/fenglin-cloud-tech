package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端治理查询：会话审计。
 */
@Mapper
public interface AdminMapper {

    @Select("""
            SELECT CAST(c.id AS varchar) AS "convId", c.title AS "title", c.conv_type AS "convType",
                   c.member_count AS "memberCount", c.status AS "status",
                   (EXTRACT(EPOCH FROM c.created_at) * 1000)::bigint AS "createdAt",
                   (SELECT count(1) FROM collab_message m
                    WHERE m.tenant_id = c.tenant_id AND m.conversation_id = c.id AND m.deleted = 0) AS "msgCount"
            FROM collab_conversation c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL
                   OR c.title ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{startAt,jdbcType=TIMESTAMP_WITH_TIMEZONE} AS timestamptz) IS NULL
                   OR c.created_at >= #{startAt})
              AND (CAST(#{endAt,jdbcType=TIMESTAMP_WITH_TIMEZONE} AS timestamptz) IS NULL
                   OR c.created_at < #{endAt})
            ORDER BY c.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listConversationsForAudit(@Param("tenantId") long tenantId,
                                                        @Param("keyword") String keyword,
                                                        @Param("startAt") OffsetDateTime startAt,
                                                        @Param("endAt") OffsetDateTime endAt,
                                                        @Param("offset") int offset,
                                                        @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_conversation c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL
                   OR c.title ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{startAt,jdbcType=TIMESTAMP_WITH_TIMEZONE} AS timestamptz) IS NULL
                   OR c.created_at >= #{startAt})
              AND (CAST(#{endAt,jdbcType=TIMESTAMP_WITH_TIMEZONE} AS timestamptz) IS NULL
                   OR c.created_at < #{endAt})
            """)
    long countConversationsForAudit(@Param("tenantId") long tenantId,
                                    @Param("keyword") String keyword,
                                    @Param("startAt") OffsetDateTime startAt,
                                    @Param("endAt") OffsetDateTime endAt);
}
