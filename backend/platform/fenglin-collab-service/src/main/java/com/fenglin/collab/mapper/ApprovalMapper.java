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
 * 审批域 Mapper。实例/任务状态机：RUNNING → APPROVED/REJECTED/CANCELLED。
 */
@Mapper
public interface ApprovalMapper {

    @Insert("""
            INSERT INTO collab_approval_template
                (id, tenant_id, code, name, category, form_schema, process_key, enabled, dept_scope, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{code}, #{name}, #{category},
                    CAST(#{formSchema,jdbcType=VARCHAR} AS jsonb), #{processKey}, #{enabled},
                    CAST(#{deptScope,jdbcType=VARCHAR} AS bigint[]), #{userId})
            """)
    void insertTemplate(@Param("id") String id, @Param("tenantId") long tenantId, @Param("code") String code,
                        @Param("name") String name, @Param("category") String category,
                        @Param("formSchema") String formSchema, @Param("processKey") String processKey,
                        @Param("enabled") int enabled, @Param("deptScope") String deptScope,
                        @Param("userId") long userId);

    @Select("""
            SELECT CAST(id AS varchar) AS "templateId", code AS "code", name AS "name", category AS "category",
                   CAST(form_schema AS varchar) AS "formSchema", process_key AS "processKey", enabled AS "enabled"
            FROM collab_approval_template
            WHERE tenant_id = #{tenantId} AND id = CAST(#{templateId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findTemplate(@Param("tenantId") long tenantId, @Param("templateId") String templateId);

    @Select("""
            SELECT CAST(id AS varchar) AS "templateId", code AS "code", name AS "name", category AS "category",
                   process_key AS "processKey", enabled AS "enabled"
            FROM collab_approval_template
            WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
            """)
    Map<String, Object> findTemplateByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("""
            SELECT CAST(id AS varchar) AS "templateId", code AS "code", name AS "name", category AS "category",
                   process_key AS "processKey", enabled AS "enabled"
            FROM collab_approval_template
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND enabled = 1
            ORDER BY category, code
            """)
    List<Map<String, Object>> listTemplates(@Param("tenantId") long tenantId);

    @Select("""
            SELECT 'APV' || to_char(now(), 'YYYYMM') || lpad(nextval('collab_apv_no_seq')::text, 6, '0') AS "instanceNo"
            """)
    String nextInstanceNo();

    @Insert("""
            INSERT INTO collab_approval_instance
                (id, tenant_id, template_id, instance_no, initiator_id, form_data, form_data_cipher,
                 wf_instance_id, status, current_node, idempotency_key, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, CAST(#{templateId} AS uuid), #{instanceNo}, #{initiatorId},
                    CAST(#{formData} AS jsonb), #{formDataCipher,jdbcType=VARCHAR}, #{wfInstanceId,jdbcType=VARCHAR},
                    'RUNNING', #{currentNode}, #{idempotencyKey,jdbcType=VARCHAR}, #{initiatorId})
            """)
    void insertInstance(@Param("id") String id, @Param("tenantId") long tenantId, @Param("templateId") String templateId,
                        @Param("instanceNo") String instanceNo, @Param("initiatorId") long initiatorId,
                        @Param("formData") String formData, @Param("formDataCipher") String formDataCipher,
                        @Param("wfInstanceId") String wfInstanceId, @Param("currentNode") String currentNode,
                        @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT CAST(id AS varchar) AS "instanceId", CAST(template_id AS varchar) AS "templateId",
                   instance_no AS "instanceNo", initiator_id AS "initiatorId", CAST(form_data AS varchar) AS "formData",
                   wf_instance_id AS "wfInstanceId", status AS "status", current_node AS "currentNode",
                   (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_approval_instance
            WHERE tenant_id = #{tenantId} AND id = CAST(#{instanceId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findInstance(@Param("tenantId") long tenantId, @Param("instanceId") String instanceId);

    @Select("""
            SELECT CAST(id AS varchar) AS "instanceId", CAST(template_id AS varchar) AS "templateId",
                   instance_no AS "instanceNo", initiator_id AS "initiatorId", CAST(form_data AS varchar) AS "formData",
                   wf_instance_id AS "wfInstanceId", status AS "status", current_node AS "currentNode",
                   (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_approval_instance
            WHERE tenant_id = #{tenantId} AND idempotency_key = #{idempotencyKey} AND deleted = 0
            """)
    Map<String, Object> findByIdempotencyKey(@Param("tenantId") long tenantId,
                                             @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE collab_approval_instance
            SET status = #{status}, current_node = #{currentNode,jdbcType=VARCHAR},
                finished_at = #{finishedAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{instanceId} AS uuid) AND deleted = 0
            """)
    void updateInstanceStatus(@Param("tenantId") long tenantId, @Param("instanceId") String instanceId,
                              @Param("status") String status, @Param("currentNode") String currentNode,
                              @Param("finishedAt") OffsetDateTime finishedAt, @Param("userId") long userId);

    @Update("""
            UPDATE collab_approval_instance SET wf_instance_id = #{wfInstanceId}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{instanceId} AS uuid)
            """)
    void bindWorkflowInstance(@Param("tenantId") long tenantId, @Param("instanceId") String instanceId,
                              @Param("wfInstanceId") String wfInstanceId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_approval_task
                (id, tenant_id, instance_id, node_key, node_name, assignee_id, assignee_type, task_type, status, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{instanceId} AS uuid), #{nodeKey}, #{nodeName}, #{assigneeId},
                    #{assigneeType}, 'SINGLE', 'PENDING', #{initiatorId})
            """)
    void insertTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("instanceId") String instanceId,
                    @Param("nodeKey") String nodeKey, @Param("nodeName") String nodeName,
                    @Param("assigneeId") Long assigneeId, @Param("assigneeType") String assigneeType,
                    @Param("initiatorId") long initiatorId);

    @Select("""
            SELECT id AS "taskId", CAST(instance_id AS varchar) AS "instanceId", node_key AS "nodeKey",
                   node_name AS "nodeName", assignee_id AS "assigneeId", status AS "status", action AS "action",
                   (EXTRACT(EPOCH FROM handled_at) * 1000)::bigint AS "handledAt"
            FROM collab_approval_task
            WHERE tenant_id = #{tenantId} AND id = #{taskId} AND deleted = 0
            """)
    Map<String, Object> findTask(@Param("tenantId") long tenantId, @Param("taskId") long taskId);

    @Select("""
            SELECT id AS "taskId", CAST(instance_id AS varchar) AS "instanceId", node_key AS "nodeKey",
                   node_name AS "nodeName", assignee_id AS "assigneeId", status AS "status", action AS "action",
                   (EXTRACT(EPOCH FROM handled_at) * 1000)::bigint AS "handledAt"
            FROM collab_approval_task
            WHERE tenant_id = #{tenantId} AND instance_id = CAST(#{instanceId} AS uuid) AND deleted = 0
            ORDER BY created_at, id
            """)
    List<Map<String, Object>> listTasksByInstance(@Param("tenantId") long tenantId,
                                                  @Param("instanceId") String instanceId);

    @Select("""
            SELECT t.id AS "taskId", CAST(t.instance_id AS varchar) AS "instanceId", t.node_key AS "nodeKey",
                   t.node_name AS "nodeName", t.assignee_id AS "assigneeId", t.status AS "status", t.action AS "action",
                   (EXTRACT(EPOCH FROM t.handled_at) * 1000)::bigint AS "handledAt",
                   i.instance_no AS "instanceNo", i.status AS "instanceStatus"
            FROM collab_approval_task t
            JOIN collab_approval_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id
            WHERE t.tenant_id = #{tenantId} AND t.assignee_id = #{userId} AND t.status = 'PENDING'
              AND t.deleted = 0 AND i.deleted = 0 AND i.status = 'RUNNING'
            ORDER BY i.created_at
            """)
    List<Map<String, Object>> listMyTasks(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Update("""
            UPDATE collab_approval_task
            SET action = #{action}, status = #{status}, comment_cipher = #{commentCipher,jdbcType=VARCHAR},
                handled_at = now(), updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{taskId} AND deleted = 0 AND status = 'PENDING'
            """)
    int updateTaskHandled(@Param("tenantId") long tenantId, @Param("taskId") long taskId,
                          @Param("action") String action, @Param("status") String status,
                          @Param("commentCipher") String commentCipher, @Param("userId") long userId);

    @Update("""
            UPDATE collab_approval_task SET assignee_id = #{assigneeId}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{taskId} AND status = 'PENDING' AND deleted = 0
            """)
    int updateTaskAssignee(@Param("tenantId") long tenantId, @Param("taskId") long taskId,
                           @Param("assigneeId") long assigneeId, @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM collab_approval_task
            WHERE tenant_id = #{tenantId} AND instance_id = CAST(#{instanceId} AS uuid)
              AND status = 'PENDING' AND deleted = 0
            """)
    long countPendingTasks(@Param("tenantId") long tenantId, @Param("instanceId") String instanceId);

    @Update("""
            UPDATE collab_approval_task SET status = 'SKIPPED', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND instance_id = CAST(#{instanceId} AS uuid)
              AND status = 'PENDING' AND deleted = 0
            """)
    void skipPendingTasks(@Param("tenantId") long tenantId, @Param("instanceId") String instanceId,
                          @Param("userId") long userId);

    @Select("""
            SELECT i.id AS "instanceId", i.instance_no AS "instanceNo", i.status AS "status",
                   i.initiator_id AS "initiatorId", t.node_key AS "nodeKey", t.node_name AS "nodeName",
                   (EXTRACT(EPOCH FROM i.created_at) * 1000)::bigint AS "createdAt"
            FROM collab_approval_instance i
            LEFT JOIN collab_approval_task t ON t.instance_id = i.id AND t.tenant_id = i.tenant_id
                 AND t.status = 'PENDING' AND t.deleted = 0
            WHERE i.tenant_id = #{tenantId} AND i.initiator_id = #{userId} AND i.deleted = 0
            ORDER BY i.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listMyInstances(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                              @Param("offset") int offset, @Param("size") int size);
}
