package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface WorkflowCallbackLogMapper {

    @Insert("""
            INSERT INTO hub_wf_callback_log (id, tenant_id, instance_id, node_key, action, callback_url, status,
                                             attempts, created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{instanceId}, #{nodeKey}, #{action}, #{callbackUrl}, 'PENDING',
                    0, #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, instance_id, node_key, action) WHERE deleted = 0 DO NOTHING
            """)
    int insertIfAbsent(@Param("id") long id,
                       @Param("tenantId") long tenantId,
                       @Param("instanceId") long instanceId,
                       @Param("nodeKey") String nodeKey,
                       @Param("action") String action,
                       @Param("callbackUrl") String callbackUrl,
                       @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id FROM hub_wf_callback_log
             WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId}
               AND node_key = #{nodeKey} AND action = #{action} AND deleted = 0
            """)
    Long findId(@Param("tenantId") long tenantId,
                @Param("instanceId") long instanceId,
                @Param("nodeKey") String nodeKey,
                @Param("action") String action);

    @Update("""
            UPDATE hub_wf_callback_log
               SET status = #{status}, attempts = #{attempts}, http_status = #{httpStatus,jdbcType=INTEGER},
                   error_msg = #{errorMsg,jdbcType=VARCHAR}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int markResult(@Param("tenantId") long tenantId,
                   @Param("id") long id,
                   @Param("status") String status,
                   @Param("attempts") int attempts,
                   @Param("httpStatus") Integer httpStatus,
                   @Param("errorMsg") String errorMsg);

    @Select("""
            SELECT id, tenant_id, instance_id, node_key, action, callback_url, status, attempts, http_status,
                   error_msg, created_at
              FROM hub_wf_callback_log
             WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} AND deleted = 0
             ORDER BY created_at ASC
            """)
    java.util.List<Map<String, Object>> findByInstance(@Param("tenantId") long tenantId,
                                                       @Param("instanceId") long instanceId);
}
