package com.fenglin.lowcode.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LowcodeVersionMapper {

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
              FROM hub_lowcode_version
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            """)
    int maxVersionNo(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Insert("""
            INSERT INTO hub_lowcode_version (id, tenant_id, app_id, version_no, schema, data_model,
                                             status, process_instance_id, published_by, published_at,
                                             created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{versionNo}, CAST(#{schema} AS jsonb),
                    CAST(#{dataModel} AS jsonb), 'PUBLISHED', #{processInstanceId,jdbcType=VARCHAR},
                    #{operatorId}, now(), #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("versionNo") int versionNo,
               @Param("schema") String schema,
               @Param("dataModel") String dataModel,
               @Param("processInstanceId") String processInstanceId,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, app_id, version_no, status, process_instance_id, published_at,
                   CAST(schema AS text) AS schema_text, CAST(data_model AS text) AS data_model_text
              FROM hub_lowcode_version
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND version_no = #{versionNo}
               AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByVersion(@Param("tenantId") long tenantId,
                                      @Param("appId") long appId,
                                      @Param("versionNo") int versionNo);

    @Select("""
            SELECT id, version_no, status, process_instance_id, published_at,
                   CAST(schema AS text) AS schema_text
              FROM hub_lowcode_version
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
             ORDER BY version_no DESC
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
