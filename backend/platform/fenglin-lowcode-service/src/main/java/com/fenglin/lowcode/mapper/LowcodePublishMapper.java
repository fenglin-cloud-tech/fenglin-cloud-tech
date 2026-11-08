package com.fenglin.lowcode.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface LowcodePublishMapper {

    @Insert("""
            INSERT INTO hub_lowcode_publish (id, tenant_id, app_id, version_id, env, action, status,
                                             process_instance_id, remark, created_by, created_at,
                                             updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{versionId}, #{env}, #{action}, 'SUCCESS',
                    #{processInstanceId,jdbcType=VARCHAR}, #{remark,jdbcType=VARCHAR},
                    #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("versionId") long versionId,
               @Param("env") String env,
               @Param("action") String action,
               @Param("processInstanceId") String processInstanceId,
               @Param("remark") String remark,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, version_id, env, action, status, process_instance_id
              FROM hub_lowcode_publish
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
             ORDER BY created_at DESC
             LIMIT 1
            """)
    Map<String, Object> findLatest(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
