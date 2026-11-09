package com.fenglin.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 低代码应用：应用/表单/流程/模型/页面/发布/脚本。
 */
@Mapper
public interface LowcodeMapper {

    @Insert("""
            INSERT INTO svc_app_definition
                (id, tenant_id, app_code, name, icon, description, owner_id, dept_id, env, visible_scope,
                 status, script_risk_level, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appCode}, #{name}, #{icon}, #{description}, #{ownerId}, #{deptId},
                    'DRAFT', #{visibleScope}::jsonb, 'DRAFT', #{scriptRiskLevel}, #{userId}, #{userId})
            """)
    void insertApp(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appCode") String appCode,
                   @Param("name") String name, @Param("icon") String icon, @Param("description") String description,
                   @Param("ownerId") Long ownerId, @Param("deptId") Long deptId,
                   @Param("visibleScope") String visibleScope, @Param("scriptRiskLevel") String scriptRiskLevel,
                   @Param("userId") long userId);

    @Select("""
            SELECT id, app_code, name, icon, description, owner_id, dept_id, env,
                   visible_scope::text AS visible_scope, status, script_risk_level, created_by, created_at, updated_at
            FROM svc_app_definition WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findApp(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, app_code, name, icon, description, owner_id, dept_id, env,
                   visible_scope::text AS visible_scope, status, script_risk_level, created_by, created_at, updated_at
            FROM svc_app_definition
            WHERE app_code = #{appCode} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findAppByCode(@Param("tenantId") long tenantId, @Param("appCode") String appCode);

    @Select("""
            <script>
            SELECT id, app_code, name, env, owner_id, status, script_risk_level, created_at, updated_at
            FROM svc_app_definition
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (name ILIKE '%' || #{keyword} || '%' OR app_code ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listApps(@Param("tenantId") long tenantId, @Param("status") String status,
                                       @Param("keyword") String keyword, @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_app_definition
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (name ILIKE '%' || #{keyword} || '%' OR app_code ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long countApps(@Param("tenantId") long tenantId, @Param("status") String status,
                   @Param("keyword") String keyword);

    @Update("""
            UPDATE svc_app_definition SET status = #{status}, env = #{env},
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateAppStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                        @Param("status") String status, @Param("env") String env, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_form_definition
                (id, tenant_id, app_id, form_key, name, version_no, schema_json, engine_ref, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{formKey}, #{name}, #{versionNo}, #{schemaJson}::jsonb,
                    #{engineRef}, #{status}, #{userId}, #{userId})
            """)
    void insertForm(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                    @Param("formKey") String formKey, @Param("name") String name, @Param("versionNo") int versionNo,
                    @Param("schemaJson") String schemaJson, @Param("engineRef") String engineRef,
                    @Param("status") String status, @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, form_key, name, version_no, schema_json::text AS schema_json, engine_ref,
                   status, created_at, updated_at
            FROM svc_form_definition WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findForm(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, form_key, name, version_no, status, created_at FROM svc_form_definition
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            ORDER BY form_key, version_no DESC
            """)
    List<Map<String, Object>> listForms(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Insert("""
            INSERT INTO svc_form_field
                (id, tenant_id, form_id, field_key, label, field_type, required, options, validators,
                 linkage, perm_json, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{formId}, #{fieldKey}, #{label}, #{fieldType}, #{required},
                    #{options}::jsonb, #{validators}::jsonb, #{linkage}::jsonb, #{permJson}::jsonb,
                    #{sortNo}, #{userId})
            """)
    void insertField(@Param("id") long id, @Param("tenantId") long tenantId, @Param("formId") long formId,
                     @Param("fieldKey") String fieldKey, @Param("label") String label,
                     @Param("fieldType") String fieldType, @Param("required") boolean required,
                     @Param("options") String options, @Param("validators") String validators,
                     @Param("linkage") String linkage, @Param("permJson") String permJson,
                     @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            SELECT id, field_key, label, field_type, required, options::text AS options,
                   validators::text AS validators, linkage::text AS linkage, perm_json::text AS perm_json, sort_no
            FROM svc_form_field WHERE tenant_id = #{tenantId} AND form_id = #{formId} AND deleted = 0
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listFields(@Param("tenantId") long tenantId, @Param("formId") long formId);

    @Insert("""
            INSERT INTO svc_flow_definition
                (id, tenant_id, app_id, flow_key, name, version_no, bpmn_json, form_id, engine_deploy_id,
                 status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{flowKey}, #{name}, #{versionNo}, #{bpmnJson}::jsonb,
                    #{formId}, #{engineDeployId}, #{status}, #{userId}, #{userId})
            """)
    void insertFlowDef(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                       @Param("flowKey") String flowKey, @Param("name") String name, @Param("versionNo") int versionNo,
                       @Param("bpmnJson") String bpmnJson, @Param("formId") Long formId,
                       @Param("engineDeployId") String engineDeployId, @Param("status") String status,
                       @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, flow_key, name, version_no, bpmn_json::text AS bpmn_json, form_id,
                   engine_deploy_id, status, created_at, updated_at
            FROM svc_flow_definition WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findFlowDef(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, app_id, flow_key, name, version_no, bpmn_json::text AS bpmn_json, form_id,
                   engine_deploy_id, status, created_at, updated_at
            FROM svc_flow_definition
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND flow_key = #{flowKey} AND deleted = 0
            ORDER BY version_no DESC LIMIT 1
            """)
    Map<String, Object> findFlowByKey(@Param("tenantId") long tenantId, @Param("appId") long appId,
                                      @Param("flowKey") String flowKey);

    @Select("""
            SELECT id, flow_key, name, version_no, status, engine_deploy_id, created_at
            FROM svc_flow_definition
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            ORDER BY flow_key, version_no DESC
            """)
    List<Map<String, Object>> listFlowDefs(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Update("""
            UPDATE svc_flow_definition SET status = #{status}, engine_deploy_id = #{engineDeployId},
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateFlowDefStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("status") String status, @Param("engineDeployId") String engineDeployId,
                            @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_flow_instance
                (id, tenant_id, flow_id, flow_key, business_key, title, app_id, initiator_id, status,
                 current_node, engine_instance_id, started_at, created_by)
            VALUES (#{id}, #{tenantId}, #{flowId}, #{flowKey}, #{businessKey}, #{title}, #{appId}, #{userId},
                    'RUNNING', #{currentNode}, #{engineInstanceId}, now(), #{userId})
            """)
    void insertFlowInstance(@Param("id") long id, @Param("tenantId") long tenantId, @Param("flowId") long flowId,
                            @Param("flowKey") String flowKey, @Param("businessKey") String businessKey,
                            @Param("title") String title, @Param("appId") Long appId,
                            @Param("currentNode") String currentNode, @Param("engineInstanceId") String engineInstanceId,
                            @Param("userId") long userId);

    @Select("""
            SELECT id, flow_id, flow_key, business_key, title, initiator_id, status, current_node,
                   engine_instance_id, started_at, ended_at, duration_sec, created_at
            FROM svc_flow_instance WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findFlowInstance(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, flow_key, business_key, title, initiator_id, status, current_node, started_at, created_at
            FROM svc_flow_instance
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listFlowInstances(@Param("tenantId") long tenantId, @Param("status") String status,
                                                @Param("limit") int limit, @Param("offset") int offset);

    @Insert("""
            INSERT INTO svc_flow_task
                (id, tenant_id, instance_id, task_key, task_name, task_type, assignee_id, candidate_json,
                 status, created_at_task, created_by)
            VALUES (#{id}, #{tenantId}, #{instanceId}, #{taskKey}, #{taskName}, #{taskType}, #{assigneeId},
                    #{candidateJson}::jsonb, 'PENDING', now(), #{userId})
            """)
    void insertTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("instanceId") long instanceId,
                    @Param("taskKey") String taskKey, @Param("taskName") String taskName,
                    @Param("taskType") String taskType, @Param("assigneeId") Long assigneeId,
                    @Param("candidateJson") String candidateJson, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT t.id, t.instance_id, i.flow_key, i.title, t.task_key, t.task_name, t.task_type,
                   t.assignee_id, t.candidate_json::text AS candidate_json, t.status, t.created_at_task
            FROM svc_flow_task t JOIN svc_flow_instance i ON i.id = t.instance_id
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
            <if test="assigneeId != null">AND t.assignee_id = #{assigneeId}</if>
            <if test="status != null">AND t.status = #{status}</if>
            ORDER BY t.created_at_task DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTasks(@Param("tenantId") long tenantId, @Param("assigneeId") Long assigneeId,
                                        @Param("status") String status, @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("""
            SELECT id, instance_id, task_key, task_name, assignee_id, status FROM svc_flow_task
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findTask(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE svc_flow_task SET status = #{status}, action = #{action}, comment = #{comment},
                   completed_at = now(), duration_sec = EXTRACT(EPOCH FROM (now() - created_at_task))::bigint,
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 AND status = 'PENDING'
            """)
    int completeTask(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("action") String action, @Param("comment") String comment, @Param("userId") long userId);

    @Update("""
            UPDATE svc_flow_instance SET status = #{status}, current_node = #{currentNode},
                   ended_at = CASE WHEN #{status} = 'COMPLETED' THEN now() ELSE ended_at END,
                   duration_sec = CASE WHEN #{status} = 'COMPLETED'
                                       THEN EXTRACT(EPOCH FROM (now() - started_at))::bigint ELSE duration_sec END,
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateInstance(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                       @Param("currentNode") String currentNode, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_data_model
                (id, tenant_id, app_id, model_key, name, version_no, engine_ref, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{modelKey}, #{name}, #{versionNo}, #{engineRef},
                    #{status}, #{userId}, #{userId})
            """)
    void insertModel(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                     @Param("modelKey") String modelKey, @Param("name") String name, @Param("versionNo") int versionNo,
                     @Param("engineRef") String engineRef, @Param("status") String status, @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, model_key, name, version_no, engine_ref, status, created_at, updated_at
            FROM svc_data_model WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findModel(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, model_key, name, version_no, status, created_at FROM svc_data_model
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            ORDER BY model_key, version_no DESC
            """)
    List<Map<String, Object>> listModels(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Insert("""
            INSERT INTO svc_data_model_field
                (id, tenant_id, model_id, field_key, name, data_type, length, nullable, unique_flag,
                 indexed, ref_model_id, ref_type, calc_expr, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{modelId}, #{fieldKey}, #{name}, #{dataType}, #{length}, #{nullable},
                    #{uniqueFlag}, #{indexed}, #{refModelId}, #{refType}, #{calcExpr}, #{sortNo}, #{userId})
            """)
    void insertModelField(@Param("id") long id, @Param("tenantId") long tenantId, @Param("modelId") long modelId,
                          @Param("fieldKey") String fieldKey, @Param("name") String name,
                          @Param("dataType") String dataType, @Param("length") Integer length,
                          @Param("nullable") boolean nullable, @Param("uniqueFlag") boolean uniqueFlag,
                          @Param("indexed") boolean indexed, @Param("refModelId") Long refModelId,
                          @Param("refType") String refType, @Param("calcExpr") String calcExpr,
                          @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            SELECT id, field_key, name, data_type, length, nullable, unique_flag, indexed,
                   ref_model_id, ref_type, calc_expr, sort_no
            FROM svc_data_model_field WHERE tenant_id = #{tenantId} AND model_id = #{modelId} AND deleted = 0
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listModelFields(@Param("tenantId") long tenantId, @Param("modelId") long modelId);

    @Insert("""
            INSERT INTO svc_page_schema
                (id, tenant_id, app_id, page_key, name, page_type, schema_json, data_binding, perm_json,
                 version_no, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{pageKey}, #{name}, #{pageType}, #{schemaJson}::jsonb,
                    #{dataBinding}::jsonb, #{permJson}::jsonb, #{versionNo}, #{status}, #{userId}, #{userId})
            """)
    void insertPage(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                    @Param("pageKey") String pageKey, @Param("name") String name, @Param("pageType") String pageType,
                    @Param("schemaJson") String schemaJson, @Param("dataBinding") String dataBinding,
                    @Param("permJson") String permJson, @Param("versionNo") int versionNo,
                    @Param("status") String status, @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, page_key, name, page_type, schema_json::text AS schema_json,
                   data_binding::text AS data_binding, perm_json::text AS perm_json, version_no, status,
                   created_at, updated_at
            FROM svc_page_schema WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findPage(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, app_id, page_key, name, page_type, schema_json::text AS schema_json,
                   data_binding::text AS data_binding, perm_json::text AS perm_json, version_no, status,
                   created_at, updated_at
            FROM svc_page_schema
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND page_key = #{pageKey} AND deleted = 0
            ORDER BY version_no DESC LIMIT 1
            """)
    Map<String, Object> findPageByKey(@Param("tenantId") long tenantId, @Param("appId") long appId,
                                      @Param("pageKey") String pageKey);

    @Update("""
            UPDATE svc_page_schema SET data_binding = #{dataBinding}::jsonb, updated_by = #{userId},
                   version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updatePageDatasource(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("dataBinding") String dataBinding, @Param("userId") long userId);

    @Select("""
            SELECT id, page_key, name, page_type, version_no, status, updated_at FROM svc_page_schema
            WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            ORDER BY page_key, version_no DESC
            """)
    List<Map<String, Object>> listPages(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Insert("""
            INSERT INTO svc_app_release
                (id, tenant_id, app_id, release_no, from_env, to_env, snapshot_json, approval_status,
                 status, release_note, script_scan_json, created_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{releaseNo}, #{fromEnv}, #{toEnv}, #{snapshotJson}::jsonb,
                    'PENDING', 'RELEASED', #{releaseNote}, #{scriptScanJson}::jsonb, #{userId})
            """)
    void insertRelease(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                       @Param("releaseNo") String releaseNo, @Param("fromEnv") String fromEnv,
                       @Param("toEnv") String toEnv, @Param("snapshotJson") String snapshotJson,
                       @Param("releaseNote") String releaseNote, @Param("scriptScanJson") String scriptScanJson,
                       @Param("userId") long userId);

    @Select("""
            SELECT r.id, r.app_id, a.name AS app_name, r.release_no, r.from_env, r.to_env, r.snapshot_json::text AS snapshot_json,
                   r.approval_status, r.approver_id, r.approved_at, r.rollback_from, r.status, r.release_note,
                   r.script_scan_json::text AS script_scan_json, r.created_at
            FROM svc_app_release r LEFT JOIN svc_app_definition a ON a.id = r.app_id
            WHERE r.id = #{id} AND r.tenant_id = #{tenantId} AND r.deleted = 0
            """)
    Map<String, Object> findRelease(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT r.id, r.app_id, a.name AS app_name, r.release_no, r.from_env, r.to_env, r.approval_status,
                   r.status, r.created_at FROM svc_app_release r LEFT JOIN svc_app_definition a ON a.id = r.app_id
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
            <if test="appId != null">AND r.app_id = #{appId}</if>
            ORDER BY r.created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listReleases(@Param("tenantId") long tenantId, @Param("appId") Long appId,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE svc_app_release SET approval_status = #{approvalStatus}, approver_id = #{userId},
                   approved_at = now(), status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int approveRelease(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("approvalStatus") String approvalStatus, @Param("status") String status,
                       @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_script_definition
                (id, tenant_id, app_id, script_key, name, language, content, version_no, risk_level,
                 scan_result, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{scriptKey}, #{name}, #{language}, #{content}, #{versionNo},
                    #{riskLevel}, #{scanResult}::jsonb, #{status}, #{userId}, #{userId})
            """)
    void insertScript(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
                      @Param("scriptKey") String scriptKey, @Param("name") String name, @Param("language") String language,
                      @Param("content") String content, @Param("versionNo") int versionNo,
                      @Param("riskLevel") String riskLevel, @Param("scanResult") String scanResult,
                      @Param("status") String status, @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, script_key, name, language, content, version_no, risk_level,
                   scan_result::text AS scan_result, status, created_at, updated_at
            FROM svc_script_definition WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findScript(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, script_key, name, language, version_no, risk_level, status, updated_at
            FROM svc_script_definition WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            ORDER BY script_key, version_no DESC
            """)
    List<Map<String, Object>> listScripts(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
