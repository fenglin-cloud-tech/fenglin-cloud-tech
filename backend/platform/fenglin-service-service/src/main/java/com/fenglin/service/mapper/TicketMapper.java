package com.fenglin.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 工单、流转日志、评论、SLA 计时与违约。
 */
@Mapper
public interface TicketMapper {

    @Insert("""
            INSERT INTO svc_ticket
                (id, tenant_id, ticket_no, title, description, ticket_type, catalog_id, catalog_item_id,
                 priority, impact, urgency, source, requester_id, requester_dept_id, contact_enc, contact_hash,
                 assignee_id, assignee_group_id, status, sla_policy_id, sla_response_due_at, sla_resolve_due_at,
                 tags, ext_json, ai_category, ai_confidence, created_by)
            VALUES
                (#{id}, #{tenantId}, #{ticketNo}, #{title}, #{description}, #{ticketType}, #{catalogId}, #{catalogItemId},
                 #{priority}, #{impact}, #{urgency}, #{source}, #{requesterId}, #{requesterDeptId}, #{contactEnc}, #{contactHash},
                 #{assigneeId}, #{assigneeGroupId}, #{status}, #{slaPolicyId}, #{responseDueAt}, #{resolveDueAt},
                 #{tags}::jsonb, #{extJson}::jsonb, #{aiCategory}, #{aiConfidence}, #{userId})
            """)
    void insert(Map<String, Object> row);

    @Select("""
            SELECT t.id, t.ticket_no, t.title, t.description, t.ticket_type, t.catalog_id, t.catalog_item_id,
                   t.priority, t.impact, t.urgency, t.source, t.requester_id, t.requester_dept_id,
                   t.assignee_id, t.assignee_group_id, t.status, t.sla_policy_id,
                   t.sla_response_due_at, t.sla_resolve_due_at, t.first_response_at, t.resolved_at, t.closed_at,
                   t.reopen_count, t.escalation_level, t.kb_article_id, t.ai_category, t.ai_confidence,
                   t.tags::text AS tags, t.ext_json::text AS ext_json,
                   t.created_by, t.created_at, t.updated_at
            FROM svc_ticket t
            WHERE t.id = #{id} AND t.tenant_id = #{tenantId} AND t.deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT t.id, t.ticket_no, t.title, t.ticket_type, t.catalog_item_id, t.priority, t.source,
                   t.requester_id, t.assignee_id, t.assignee_group_id, t.status,
                   t.sla_resolve_due_at, t.resolved_at, t.closed_at, t.created_at, t.updated_at
            FROM svc_ticket t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
            <if test="status != null">AND t.status = #{status}</if>
            <if test="ticketType != null">AND t.ticket_type = #{ticketType}</if>
            <if test="priority != null">AND t.priority = #{priority}</if>
            <if test="assigneeId != null">AND t.assignee_id = #{assigneeId}</if>
            <if test="assigneeGroupId != null">AND t.assignee_group_id = #{assigneeGroupId}</if>
            <if test="requesterId != null">AND t.requester_id = #{requesterId}</if>
            <if test="catalogItemId != null">AND t.catalog_item_id = #{catalogItemId}</if>
            <if test="keyword != null">AND (t.title ILIKE '%' || #{keyword} || '%' OR t.ticket_no ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY t.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("status") String status,
                                   @Param("ticketType") String ticketType, @Param("priority") String priority,
                                   @Param("assigneeId") Long assigneeId, @Param("assigneeGroupId") Long assigneeGroupId,
                                   @Param("requesterId") Long requesterId, @Param("catalogItemId") Long catalogItemId,
                                   @Param("keyword") String keyword, @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_ticket t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
            <if test="status != null">AND t.status = #{status}</if>
            <if test="ticketType != null">AND t.ticket_type = #{ticketType}</if>
            <if test="priority != null">AND t.priority = #{priority}</if>
            <if test="assigneeId != null">AND t.assignee_id = #{assigneeId}</if>
            <if test="assigneeGroupId != null">AND t.assignee_group_id = #{assigneeGroupId}</if>
            <if test="requesterId != null">AND t.requester_id = #{requesterId}</if>
            <if test="catalogItemId != null">AND t.catalog_item_id = #{catalogItemId}</if>
            <if test="keyword != null">AND (t.title ILIKE '%' || #{keyword} || '%' OR t.ticket_no ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId, @Param("status") String status,
               @Param("ticketType") String ticketType, @Param("priority") String priority,
               @Param("assigneeId") Long assigneeId, @Param("assigneeGroupId") Long assigneeGroupId,
               @Param("requesterId") Long requesterId, @Param("catalogItemId") Long catalogItemId,
               @Param("keyword") String keyword);

    @Update("""
            UPDATE svc_ticket SET status = #{toStatus}, assignee_id = COALESCE(#{assigneeId}, assignee_id),
                   assignee_group_id = COALESCE(#{assigneeGroupId}, assignee_group_id),
                   first_response_at = COALESCE(first_response_at, #{firstResponseAt}),
                   resolved_at = #{resolvedAt}, closed_at = #{closedAt},
                   escalation_level = COALESCE(#{escalationLevel}, escalation_level),
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("toStatus") String toStatus,
                     @Param("assigneeId") Long assigneeId, @Param("assigneeGroupId") Long assigneeGroupId,
                     @Param("firstResponseAt") Timestamp firstResponseAt,
                     @Param("resolvedAt") Timestamp resolvedAt, @Param("closedAt") Timestamp closedAt,
                     @Param("escalationLevel") Integer escalationLevel, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_ticket_flow_log
                (id, tenant_id, ticket_id, action, from_status, to_status, operator_id,
                 from_assignee_id, to_assignee_id, reason, operated_at, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{action}, #{fromStatus}, #{toStatus}, #{operatorId},
                    #{fromAssigneeId}, #{toAssigneeId}, #{reason}, #{operatedAt}, #{operatorId})
            """)
    void insertFlowLog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                       @Param("action") String action, @Param("fromStatus") String fromStatus,
                       @Param("toStatus") String toStatus, @Param("operatorId") long operatorId,
                       @Param("fromAssigneeId") Long fromAssigneeId, @Param("toAssigneeId") Long toAssigneeId,
                       @Param("reason") String reason, @Param("operatedAt") Timestamp operatedAt);

    @Select("""
            SELECT id, action, from_status, to_status, operator_id, from_assignee_id, to_assignee_id,
                   reason, operated_at
            FROM svc_ticket_flow_log
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            ORDER BY operated_at, id
            """)
    List<Map<String, Object>> listFlowLogs(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId);

    @Insert("""
            INSERT INTO svc_ticket_comment
                (id, tenant_id, ticket_id, comment_type, content, author_id, is_internal, attachment_ids, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{commentType}, #{content}, #{authorId}, #{internal},
                    #{attachmentIds}::jsonb, #{authorId})
            """)
    void insertComment(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                       @Param("commentType") String commentType, @Param("content") String content,
                       @Param("authorId") long authorId, @Param("internal") boolean internal,
                       @Param("attachmentIds") String attachmentIds);

    @Select("""
            SELECT id, comment_type, content, author_id, is_internal, attachment_ids::text AS attachment_ids, created_at
            FROM svc_ticket_comment
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            ORDER BY created_at, id
            """)
    List<Map<String, Object>> listComments(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId);

    @Insert("""
            INSERT INTO svc_ticket_relation (id, tenant_id, ticket_id, rel_type, rel_id, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{relType}, #{relId}, #{userId})
            """)
    void insertRelation(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                        @Param("relType") String relType, @Param("relId") long relId, @Param("userId") long userId);

    @Select("""
            SELECT id, rel_type, rel_id, created_at FROM svc_ticket_relation
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listRelations(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId);

    @Select("""
            SELECT p.id, p.name, p.response_minutes, p.resolve_minutes, p.warning_threshold,
                   p.escalation_matrix::text AS escalation_matrix
            FROM svc_sla_policy p
            WHERE p.deleted = 0 AND p.status = 'ACTIVE' AND (p.tenant_id = #{tenantId} OR p.tenant_id = 0)
              AND p.id = #{policyId}
            """)
    Map<String, Object> findPolicy(@Param("tenantId") long tenantId, @Param("policyId") long policyId);

    @Select("""
            SELECT p.id, p.name, p.response_minutes, p.resolve_minutes, p.warning_threshold,
                   p.escalation_matrix::text AS escalation_matrix
            FROM svc_sla_policy p
            WHERE p.deleted = 0 AND p.status = 'ACTIVE' AND (p.tenant_id = #{tenantId} OR p.tenant_id = 0)
              AND (p.priority IS NULL OR p.priority = #{priority})
              AND (p.ticket_type IS NULL OR p.ticket_type = #{ticketType})
            ORDER BY CASE WHEN p.tenant_id = #{tenantId} THEN 0 ELSE 1 END,
                     CASE WHEN p.priority = #{priority} THEN 0 ELSE 1 END, p.id
            LIMIT 1
            """)
    Map<String, Object> findApplicablePolicy(@Param("tenantId") long tenantId, @Param("priority") String priority,
                                             @Param("ticketType") String ticketType);

    @Select("""
            SELECT id, sla_policy_id, response_elapsed_sec, resolve_elapsed_sec, response_state, resolve_state,
                   response_due_at, resolve_due_at, warned_response, warned_resolve, last_tick_at
            FROM svc_sla_timer
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            """)
    Map<String, Object> findSlaTimer(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId);

    @Insert("""
            INSERT INTO svc_sla_timer
                (id, tenant_id, ticket_id, sla_policy_id, response_state, resolve_state,
                 response_due_at, resolve_due_at, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{policyId}, 'RUNNING', 'RUNNING',
                    #{responseDueAt}, #{resolveDueAt}, #{userId})
            """)
    void insertSlaTimer(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                        @Param("policyId") long policyId, @Param("responseDueAt") Timestamp responseDueAt,
                        @Param("resolveDueAt") Timestamp resolveDueAt, @Param("userId") long userId);

    @Update("""
            UPDATE svc_sla_timer SET response_state = #{responseState}, resolve_state = #{resolveState},
                   response_elapsed_sec = #{responseElapsed}, resolve_elapsed_sec = #{resolveElapsed},
                   last_tick_at = now(), updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            """)
    int updateSlaTimer(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                       @Param("responseState") String responseState, @Param("resolveState") String resolveState,
                       @Param("responseElapsed") long responseElapsed, @Param("resolveElapsed") long resolveElapsed,
                       @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_sla_breach
                (id, tenant_id, ticket_id, sla_policy_id, breach_type, due_at, breached_at, overdue_sec,
                 escalated_level, escalated_to, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{policyId}, #{breachType}, #{dueAt}, now(), #{overdueSec},
                    #{escalatedLevel}, #{escalatedTo}, 'OPEN', #{userId})
            """)
    void insertBreach(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                      @Param("policyId") long policyId, @Param("breachType") String breachType,
                      @Param("dueAt") Timestamp dueAt, @Param("overdueSec") long overdueSec,
                      @Param("escalatedLevel") int escalatedLevel, @Param("escalatedTo") Long escalatedTo,
                      @Param("userId") long userId);

    @Select("""
            <script>
            SELECT b.id, b.ticket_id, t.ticket_no, b.breach_type, b.due_at, b.breached_at, b.overdue_sec,
                   b.escalated_level, b.escalated_to, b.status
            FROM svc_sla_breach b JOIN svc_ticket t ON t.id = b.ticket_id
            WHERE b.tenant_id = #{tenantId} AND b.deleted = 0
            <if test="status != null">AND b.status = #{status}</if>
            ORDER BY b.breached_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listBreaches(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT contact_enc FROM svc_ticket
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    String findContactEnc(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE svc_ticket SET reopen_count = reopen_count + 1, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    void increaseReopen(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, sla_policy_id, sla_response_due_at, sla_resolve_due_at
            FROM svc_ticket
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')
            ORDER BY sla_resolve_due_at NULLS LAST
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listRunningSla(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Select("""
            SELECT status, count(1) AS cnt FROM svc_ticket
            WHERE tenant_id = #{tenantId} AND deleted = 0 GROUP BY status ORDER BY status
            """)
    List<Map<String, Object>> countByStatus(@Param("tenantId") long tenantId);

    @Select("""
            SELECT priority, count(1) AS cnt FROM svc_ticket
            WHERE tenant_id = #{tenantId} AND deleted = 0 GROUP BY priority ORDER BY priority
            """)
    List<Map<String, Object>> countByPriority(@Param("tenantId") long tenantId);

    @Select("""
            SELECT ticket_type, count(1) AS cnt FROM svc_ticket
            WHERE tenant_id = #{tenantId} AND deleted = 0 GROUP BY ticket_type ORDER BY ticket_type
            """)
    List<Map<String, Object>> countByType(@Param("tenantId") long tenantId);

    @Select("""
            SELECT assignee_id, count(1) AS total,
                   count(1) FILTER (WHERE status IN ('RESOLVED','CLOSED')) AS resolved,
                   count(1) FILTER (WHERE sla_resolve_due_at IS NOT NULL AND resolved_at > sla_resolve_due_at) AS breached
            FROM svc_ticket
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND assignee_id IS NOT NULL
            GROUP BY assignee_id ORDER BY total DESC
            """)
    List<Map<String, Object>> workload(@Param("tenantId") long tenantId);

    @Select("""
            SELECT count(1) AS total,
                   count(1) FILTER (WHERE resolved_at IS NOT NULL AND sla_resolve_due_at IS NOT NULL
                                    AND resolved_at <= sla_resolve_due_at) AS met,
                   count(1) FILTER (WHERE resolved_at IS NOT NULL AND sla_resolve_due_at IS NOT NULL
                                    AND resolved_at > sla_resolve_due_at) AS breached
            FROM svc_ticket WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> slaReport(@Param("tenantId") long tenantId);
}
