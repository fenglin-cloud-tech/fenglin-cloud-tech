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
 * 流程挖掘与 RPA。
 */
@Mapper
public interface OpsMapper {

    // ---------- 流程挖掘 ----------
    @Insert("""
            INSERT INTO svc_mining_event_log
                (id, tenant_id, case_id, activity, activity_code, event_time, resource, lifecycle,
                 source_system, attributes, ingested_at, created_by)
            VALUES (#{id}, #{tenantId}, #{caseId}, #{activity}, #{activityCode}, #{eventTime}, #{resource},
                    #{lifecycle}, #{sourceSystem}, #{attributes}::jsonb, now(), #{userId})
            ON CONFLICT (id) DO NOTHING
            """)
    void insertEvent(@Param("id") long id, @Param("tenantId") long tenantId, @Param("caseId") String caseId,
                     @Param("activity") String activity, @Param("activityCode") String activityCode,
                     @Param("eventTime") Timestamp eventTime, @Param("resource") String resource,
                     @Param("lifecycle") String lifecycle, @Param("sourceSystem") String sourceSystem,
                     @Param("attributes") String attributes, @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM svc_mining_event_log
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND event_time >= #{from} AND event_time < #{to}
            """)
    long countEvents(@Param("tenantId") long tenantId, @Param("from") Timestamp from, @Param("to") Timestamp to);

    @Select("""
            SELECT case_id, activity, activity_code, event_time, resource, lifecycle
            FROM svc_mining_event_log
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND event_time >= #{from} AND event_time < #{to}
              AND (#{sourceSystem}::varchar IS NULL OR source_system = #{sourceSystem})
            ORDER BY case_id, event_time
            """)
    List<Map<String, Object>> listEvents(@Param("tenantId") long tenantId, @Param("from") Timestamp from,
                                         @Param("to") Timestamp to, @Param("sourceSystem") String sourceSystem);

    @Insert("""
            INSERT INTO svc_mining_result
                (id, tenant_id, result_type, name, source_system, period_start, period_end, model_json,
                 metrics_json, candidate_json, status, computed_at, created_by)
            VALUES (#{id}, #{tenantId}, #{resultType}, #{name}, #{sourceSystem}, #{periodStart}, #{periodEnd},
                    #{modelJson}::jsonb, #{metricsJson}::jsonb, #{candidateJson}::jsonb, 'READY', now(), #{userId})
            """)
    void insertResult(@Param("id") long id, @Param("tenantId") long tenantId, @Param("resultType") String resultType,
                      @Param("name") String name, @Param("sourceSystem") String sourceSystem,
                      @Param("periodStart") Timestamp periodStart, @Param("periodEnd") Timestamp periodEnd,
                      @Param("modelJson") String modelJson, @Param("metricsJson") String metricsJson,
                      @Param("candidateJson") String candidateJson, @Param("userId") long userId);

    @Select("""
            SELECT id, result_type, name, source_system, period_start, period_end, model_json::text AS model_json,
                   metrics_json::text AS metrics_json, candidate_json::text AS candidate_json, status, computed_at
            FROM svc_mining_result WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findResult(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, result_type, name, source_system, period_start, period_end, status, computed_at
            FROM svc_mining_result
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="resultType != null">AND result_type = #{resultType}</if>
            ORDER BY computed_at DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listResults(@Param("tenantId") long tenantId, @Param("resultType") String resultType,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE svc_mining_result SET status = 'ARCHIVED', updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int archiveResult(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, result_type, name, source_system, period_start, period_end, model_json::text AS model_json,
                   metrics_json::text AS metrics_json, candidate_json::text AS candidate_json, status, computed_at
            FROM svc_mining_result
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND candidate_json @> ('[{"id":' || #{candidateId} || '}]')::jsonb
            ORDER BY computed_at DESC LIMIT 1
            """)
    Map<String, Object> findResultByCandidate(@Param("tenantId") long tenantId,
                                              @Param("candidateId") long candidateId);

    // ---------- RPA ----------
    @Insert("""
            INSERT INTO svc_robot
                (id, tenant_id, robot_code, name, robot_type, host, capabilities, max_concurrency, status,
                 agent_version, created_by)
            VALUES (#{id}, #{tenantId}, #{robotCode}, #{name}, #{robotType}, #{host}, #{capabilities}::jsonb,
                    #{maxConcurrency}, #{status}, #{agentVersion}, #{userId})
            """)
    void insertRobot(@Param("id") long id, @Param("tenantId") long tenantId, @Param("robotCode") String robotCode,
                     @Param("name") String name, @Param("robotType") String robotType, @Param("host") String host,
                     @Param("capabilities") String capabilities, @Param("maxConcurrency") int maxConcurrency,
                     @Param("status") String status, @Param("agentVersion") String agentVersion,
                     @Param("userId") long userId);

    @Select("""
            SELECT id, robot_code, name, robot_type, host, capabilities::text AS capabilities, max_concurrency,
                   status, last_heartbeat_at, agent_version, created_at
            FROM svc_robot WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY robot_code LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listRobots(@Param("tenantId") long tenantId, @Param("limit") int limit,
                                         @Param("offset") int offset);

    @Select("""
            SELECT id, robot_code, name, robot_type, host, max_concurrency, status, last_heartbeat_at, agent_version
            FROM svc_robot WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findRobot(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE svc_robot SET status = #{status}, last_heartbeat_at = now(), updated_by = #{userId},
                   version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateRobotStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_robot_credential
                (id, tenant_id, name, cred_type, target_system, secret_enc, kms_key_id, kms_version,
                 rotate_due_at, status, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{credType}, #{targetSystem}, #{secretEnc}, #{kmsKeyId},
                    #{kmsVersion}, #{rotateDueAt}, 'ACTIVE', #{userId})
            """)
    void insertCredential(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                          @Param("credType") String credType, @Param("targetSystem") String targetSystem,
                          @Param("secretEnc") String secretEnc, @Param("kmsKeyId") String kmsKeyId,
                          @Param("kmsVersion") String kmsVersion, @Param("rotateDueAt") Timestamp rotateDueAt,
                          @Param("userId") long userId);

    @Select("""
            SELECT id, name, cred_type, target_system, kms_key_id, kms_version, rotate_due_at, status,
                   last_used_at, created_at
            FROM svc_robot_credential WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id
            """)
    List<Map<String, Object>> listCredentials(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id, name, cred_type, target_system, secret_enc, kms_key_id
            FROM svc_robot_credential
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 AND status = 'ACTIVE'
            """)
    Map<String, Object> findCredentialSecret(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO svc_automation_flow
                (id, tenant_id, app_id, flow_code, name, version_no, nodes_json, params_schema, target_whitelist,
                 timeout_sec, retry_policy, source_candidate_id, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{appId}, #{flowCode}, #{name}, #{versionNo}, #{nodesJson}::jsonb,
                    #{paramsSchema}::jsonb, #{targetWhitelist}::jsonb, #{timeoutSec}, #{retryPolicy}::jsonb,
                    #{sourceCandidateId}, #{status}, #{userId}, #{userId})
            """)
    void insertAutomation(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") Long appId,
                          @Param("flowCode") String flowCode, @Param("name") String name,
                          @Param("versionNo") int versionNo, @Param("nodesJson") String nodesJson,
                          @Param("paramsSchema") String paramsSchema, @Param("targetWhitelist") String targetWhitelist,
                          @Param("timeoutSec") int timeoutSec, @Param("retryPolicy") String retryPolicy,
                          @Param("sourceCandidateId") Long sourceCandidateId, @Param("status") String status,
                          @Param("userId") long userId);

    @Select("""
            SELECT id, app_id, flow_code, name, version_no, nodes_json::text AS nodes_json,
                   params_schema::text AS params_schema, target_whitelist::text AS target_whitelist,
                   timeout_sec, retry_policy::text AS retry_policy, source_candidate_id, status
            FROM svc_automation_flow WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findAutomation(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, flow_code, name, version_no, status, timeout_sec, source_candidate_id, created_at
            FROM svc_automation_flow
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listAutomations(@Param("tenantId") long tenantId, @Param("status") String status,
                                              @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE svc_automation_flow SET status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateAutomationStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("status") String status, @Param("userId") long userId);

    @Update("""
            UPDATE svc_automation_flow SET target_whitelist = #{whitelist}::jsonb, updated_by = #{userId},
                   version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateWhitelist(@Param("tenantId") long tenantId, @Param("id") long id,
                        @Param("whitelist") String whitelist, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_robot_schedule
                (id, tenant_id, name, automation_flow_id, trigger_type, cron_expr, event_topic,
                 robot_selector, concurrency_limit, timeout_sec, enabled, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{automationFlowId}, #{triggerType}, #{cronExpr}, #{eventTopic},
                    #{robotSelector}::jsonb, #{concurrencyLimit}, #{timeoutSec}, #{enabled}, #{userId})
            """)
    void insertSchedule(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                        @Param("automationFlowId") long automationFlowId, @Param("triggerType") String triggerType,
                        @Param("cronExpr") String cronExpr, @Param("eventTopic") String eventTopic,
                        @Param("robotSelector") String robotSelector, @Param("concurrencyLimit") int concurrencyLimit,
                        @Param("timeoutSec") int timeoutSec, @Param("enabled") boolean enabled,
                        @Param("userId") long userId);

    @Select("""
            SELECT id, name, automation_flow_id, trigger_type, cron_expr, event_topic, robot_selector::text AS robot_selector,
                   concurrency_limit, timeout_sec, enabled, last_fire_at, created_at
            FROM svc_robot_schedule WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id
            """)
    List<Map<String, Object>> listSchedules(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO svc_robot_task
                (id, tenant_id, task_no, automation_flow_id, robot_id, trigger_type, priority, input_json,
                 status, max_retry, business_key, idempotency_key, created_by)
            VALUES (#{id}, #{tenantId}, #{taskNo}, #{automationFlowId}, #{robotId}, #{triggerType}, #{priority},
                    #{inputJson}::jsonb, #{status}, #{maxRetry}, #{businessKey}, #{idempotencyKey}, #{userId})
            """)
    void insertTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("taskNo") String taskNo,
                    @Param("automationFlowId") long automationFlowId, @Param("robotId") Long robotId,
                    @Param("triggerType") String triggerType, @Param("priority") int priority,
                    @Param("inputJson") String inputJson, @Param("status") String status,
                    @Param("maxRetry") int maxRetry, @Param("businessKey") String businessKey,
                    @Param("idempotencyKey") String idempotencyKey, @Param("userId") long userId);

    @Select("""
            SELECT id, task_no, automation_flow_id, robot_id, trigger_type, priority, input_json::text AS input_json,
                   output_json::text AS output_json, status, retry_count, max_retry, next_retry_at,
                   started_at, ended_at, duration_sec, error_code, error_msg, business_key, idempotency_key, created_at
            FROM svc_robot_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findTask(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id FROM svc_robot_task WHERE tenant_id = #{tenantId} AND idempotency_key = #{key}
              AND deleted = 0
            """)
    Long findTaskByIdempotency(@Param("tenantId") long tenantId, @Param("key") String key);

    @Select("""
            <script>
            SELECT id, task_no, automation_flow_id, robot_id, trigger_type, priority, status, retry_count,
                   business_key, started_at, ended_at, created_at
            FROM svc_robot_task
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="robotId != null">AND robot_id = #{robotId}</if>
            ORDER BY priority, created_at LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTasks(@Param("tenantId") long tenantId, @Param("status") String status,
                                        @Param("robotId") Long robotId, @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Update("""
            UPDATE svc_robot_task SET status = #{status}, robot_id = COALESCE(#{robotId}, robot_id),
                   started_at = CASE WHEN #{status} = 'RUNNING' THEN now() ELSE started_at END,
                   ended_at = CASE WHEN #{status} IN ('SUCCEEDED','FAILED','CANCELLED') THEN now() ELSE ended_at END,
                   duration_sec = CASE WHEN #{status} IN ('SUCCEEDED','FAILED','CANCELLED')
                                       THEN EXTRACT(EPOCH FROM (now() - started_at))::bigint ELSE duration_sec END,
                   error_code = #{errorCode}, error_msg = #{errorMsg},
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateTaskStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                         @Param("robotId") Long robotId, @Param("errorCode") String errorCode,
                         @Param("errorMsg") String errorMsg, @Param("userId") long userId);

    @Update("""
            UPDATE svc_robot_task SET status = #{status}, output_json = #{outputJson}::jsonb,
                   ended_at = CASE WHEN #{status} IN ('SUCCEEDED','FAILED','MANUAL_REQUIRED') THEN now() ELSE ended_at END,
                   duration_sec = CASE WHEN #{status} IN ('SUCCEEDED','FAILED','MANUAL_REQUIRED')
                                       THEN EXTRACT(EPOCH FROM (now() - started_at))::bigint ELSE duration_sec END,
                   error_code = #{errorCode}, error_msg = #{errorMsg},
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateTaskResult(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                         @Param("outputJson") String outputJson, @Param("errorCode") String errorCode,
                         @Param("errorMsg") String errorMsg, @Param("userId") long userId);

    @Update("""
            UPDATE svc_robot_task SET retry_count = retry_count + 1, status = #{status},
                   next_retry_at = #{nextRetryAt}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int retryTask(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                  @Param("nextRetryAt") Timestamp nextRetryAt, @Param("userId") long userId);

    @Select("""
            SELECT id, task_no FROM svc_robot_task
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 'QUEUED'
            ORDER BY priority, created_at LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    Map<String, Object> lockNextQueuedTask(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO svc_robot_task_log
                (id, tenant_id, task_id, attempt, log_level, step, message, screenshot_file_id, logged_at, created_by)
            VALUES (#{id}, #{tenantId}, #{taskId}, #{attempt}, #{logLevel}, #{step}, #{message},
                    #{screenshotFileId}, now(), #{userId})
            """)
    void insertTaskLog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("taskId") long taskId,
                       @Param("attempt") int attempt, @Param("logLevel") String logLevel,
                       @Param("step") String step, @Param("message") String message,
                       @Param("screenshotFileId") String screenshotFileId, @Param("userId") long userId);

    @Select("""
            SELECT id, attempt, log_level, step, message, screenshot_file_id, logged_at
            FROM svc_robot_task_log WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND deleted = 0
            ORDER BY logged_at, id
            """)
    List<Map<String, Object>> listTaskLogs(@Param("tenantId") long tenantId, @Param("taskId") long taskId);

    @Select("""
            SELECT count(1) AS total,
                   count(1) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
                   count(1) FILTER (WHERE status = 'FAILED') AS failed,
                   count(1) FILTER (WHERE status = 'MANUAL_REQUIRED') AS manual_required,
                   COALESCE(avg(duration_sec), 0) AS avg_duration_sec
            FROM svc_robot_task WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> rpaSummary(@Param("tenantId") long tenantId);

    @Select("""
            SELECT r.id, r.robot_code, r.name, r.status,
                   count(t.id) AS task_count,
                   count(t.id) FILTER (WHERE t.status = 'SUCCEEDED') AS succeeded,
                   COALESCE(sum(t.duration_sec), 0) AS busy_seconds
            FROM svc_robot r LEFT JOIN svc_robot_task t ON t.robot_id = r.id AND t.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
            GROUP BY r.id, r.robot_code, r.name, r.status ORDER BY r.robot_code
            """)
    List<Map<String, Object>> robotUtilization(@Param("tenantId") long tenantId);
}
