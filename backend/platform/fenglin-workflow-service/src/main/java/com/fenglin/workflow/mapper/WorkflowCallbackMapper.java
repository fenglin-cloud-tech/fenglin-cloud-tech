package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface WorkflowCallbackMapper {

    @Select("""
            SELECT id, process_key, callback_url, secret_enc, events, timeout_ms, max_retry
              FROM hub_wf_callback
             WHERE tenant_id = #{tenantId} AND process_key = #{processKey}
               AND enabled = 1 AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findEnabled(@Param("tenantId") long tenantId,
                                    @Param("processKey") String processKey);
}
