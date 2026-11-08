package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowTaskMapper {

    @Insert("""
            INSERT INTO hub_wf_task (id, tenant_id, instance_id, flowable_task_id, node_key, node_name, assignee_id,
                                     candidate_ids, candidate_groups, task_type, status, due_at,
                                     created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{instanceId}, #{flowableTaskId}, #{nodeKey}, #{nodeName,jdbcType=VARCHAR},
                    #{assigneeId,jdbcType=BIGINT}, #{candidateIds,jdbcType=VARCHAR},
                    #{candidateGroups,jdbcType=VARCHAR}, #{taskType}, 'PENDING', #{dueAt,jdbcType=TIMESTAMP},
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("instanceId") long instanceId,
               @Param("flowableTaskId") String flowableTaskId,
               @Param("nodeKey") String nodeKey,
               @Param("nodeName") String nodeName,
               @Param("assigneeId") Long assigneeId,
               @Param("candidateIds") String candidateIds,
               @Param("candidateGroups") String candidateGroups,
               @Param("taskType") String taskType,
               @Param("dueAt") Instant dueAt,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, instance_id, flowable_task_id, node_key, node_name, assignee_id, candidate_ids,
                   candidate_groups, task_type, action, comment, status, due_at, handled_at
              FROM hub_wf_task
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id FROM hub_wf_task
             WHERE tenant_id = #{tenantId} AND flowable_task_id = #{flowableTaskId}
               AND status = 'PENDING' AND deleted = 0
             LIMIT 1
            """)
    Long findPendingByFlowableTaskId(@Param("tenantId") long tenantId,
                                     @Param("flowableTaskId") String flowableTaskId);

    @Select("""
            SELECT id, instance_id, flowable_task_id, node_key, node_name, assignee_id, candidate_ids,
                   candidate_groups, task_type, action, comment, status, due_at, handled_at
              FROM hub_wf_task
             WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} AND deleted = 0
             ORDER BY created_at ASC
            """)
    List<Map<String, Object>> findByInstance(@Param("tenantId") long tenantId,
                                             @Param("instanceId") long instanceId);

    @Update("""
            UPDATE hub_wf_task
               SET status = 'DONE', action = #{action}, comment = #{comment,jdbcType=VARCHAR}, handled_at = now(),
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int markDone(@Param("tenantId") long tenantId,
                 @Param("id") long id,
                 @Param("action") String action,
                 @Param("comment") String comment,
                 @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_wf_task
               SET action = #{action}, assignee_id = #{assigneeId,jdbcType=BIGINT},
                   candidate_ids = #{candidateIds,jdbcType=VARCHAR}, comment = #{comment,jdbcType=VARCHAR},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int updatePendingAction(@Param("tenantId") long tenantId,
                            @Param("id") long id,
                            @Param("action") String action,
                            @Param("assigneeId") Long assigneeId,
                            @Param("candidateIds") String candidateIds,
                            @Param("comment") String comment,
                            @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_wf_task
               SET status = 'CANCELLED', updated_by = #{operatorId,jdbcType=BIGINT},
                   updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId}
               AND status = 'PENDING' AND deleted = 0
            """)
    int cancelPendingByInstance(@Param("tenantId") long tenantId,
                                @Param("instanceId") long instanceId,
                                @Param("operatorId") Long operatorId);

    @Select("""
            <script>
            SELECT t.id, t.instance_id, t.node_key, t.node_name, t.due_at, t.created_at,
                   i.instance_no, i.title, i.initiator_id,
                   COALESCE(u.real_name, u.nickname, u.username, i.initiator_id::text) AS initiator_name
              FROM hub_wf_task t
              JOIN hub_wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
              LEFT JOIN hub_user u ON u.id = i.initiator_id AND u.tenant_id = i.tenant_id
             WHERE t.tenant_id = #{tenantId} AND t.status = 'PENDING' AND t.deleted = 0
               AND (t.assignee_id = #{userId}
                    OR (t.assignee_id IS NULL
                        AND (',' || COALESCE(t.candidate_ids, '') || ',') LIKE ('%,' || #{userId} || ',%')))
            <if test="domain != null and domain != ''">
               AND i.business_type LIKE (#{domain} || '.%')
            </if>
            <if test="urgent">
               AND t.due_at IS NOT NULL AND t.due_at &lt;= #{urgentBefore}
            </if>
             ORDER BY (t.due_at IS NULL), t.due_at ASC, t.created_at DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> pendingPage(@Param("tenantId") long tenantId,
                                          @Param("userId") long userId,
                                          @Param("domain") String domain,
                                          @Param("urgent") boolean urgent,
                                          @Param("urgentBefore") Instant urgentBefore,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*)
              FROM hub_wf_task t
              JOIN hub_wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
             WHERE t.tenant_id = #{tenantId} AND t.status = 'PENDING' AND t.deleted = 0
               AND (t.assignee_id = #{userId}
                    OR (t.assignee_id IS NULL
                        AND (',' || COALESCE(t.candidate_ids, '') || ',') LIKE ('%,' || #{userId} || ',%')))
            <if test="domain != null and domain != ''">
               AND i.business_type LIKE (#{domain} || '.%')
            </if>
            <if test="urgent">
               AND t.due_at IS NOT NULL AND t.due_at &lt;= #{urgentBefore}
            </if>
            </script>
            """)
    long pendingCount(@Param("tenantId") long tenantId,
                      @Param("userId") long userId,
                      @Param("domain") String domain,
                      @Param("urgent") boolean urgent,
                      @Param("urgentBefore") Instant urgentBefore);

    @Select("""
            SELECT count(*) FROM hub_wf_task
             WHERE tenant_id = #{tenantId} AND status = 'PENDING' AND deleted = 0
               AND due_at IS NOT NULL AND due_at < now()
            """)
    long countOverdue(@Param("tenantId") long tenantId);

    @Select("""
            <script>
            SELECT node_key, COALESCE(avg(extract(epoch FROM (handled_at - created_at)) / 60.0), 0) AS avg_minutes,
                   count(*) AS cnt
              FROM hub_wf_task
             WHERE tenant_id = #{tenantId} AND status = 'DONE' AND handled_at IS NOT NULL AND deleted = 0
            <if test="from != null">AND created_at &gt;= #{from}</if>
            <if test="to != null">AND created_at &lt;= #{to}</if>
             GROUP BY node_key
             ORDER BY avg_minutes DESC
            </script>
            """)
    List<Map<String, Object>> nodeStats(@Param("tenantId") long tenantId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Select("""
            <script>
            SELECT id, COALESCE(real_name, nickname, username) AS display_name
              FROM hub_user
             WHERE tenant_id = #{tenantId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> displayNames(@Param("tenantId") long tenantId,
                                           @Param("ids") List<Long> ids);
}
