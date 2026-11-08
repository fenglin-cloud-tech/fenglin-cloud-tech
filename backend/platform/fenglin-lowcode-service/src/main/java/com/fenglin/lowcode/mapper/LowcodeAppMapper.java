package com.fenglin.lowcode.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface LowcodeAppMapper {

    @Insert("""
            INSERT INTO hub_lowcode_app (id, tenant_id, code, name, app_type, schema, data_model, env,
                                         status, published_at, created_by, created_at, updated_by,
                                         updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{appType}, CAST(#{schema} AS jsonb),
                    CAST(#{dataModel} AS jsonb), #{env}, #{status}, NULL, #{operatorId}, now(),
                    #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("appType") String appType,
               @Param("schema") String schema,
               @Param("dataModel") String dataModel,
               @Param("env") String env,
               @Param("status") String status,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, code, name, app_type, CAST(schema AS text) AS schema_text,
                   CAST(data_model AS text) AS data_model_text, env, status, published_at,
                   (SELECT COALESCE(MAX(v.version_no), 0) FROM hub_lowcode_version v
                     WHERE v.tenant_id = a.tenant_id AND v.app_id = a.id AND v.deleted = 0) AS latest_version
              FROM hub_lowcode_app a
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("""
            SELECT id, code, name, app_type, CAST(schema AS text) AS schema_text,
                   CAST(data_model AS text) AS data_model_text, env, status, published_at,
                   (SELECT COALESCE(MAX(v.version_no), 0) FROM hub_lowcode_version v
                     WHERE v.tenant_id = a.tenant_id AND v.app_id = a.id AND v.deleted = 0) AS latest_version
              FROM hub_lowcode_app a
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, code, name, app_type, CAST(schema AS text) AS schema_text,
                   CAST(data_model AS text) AS data_model_text, env, status, published_at,
                   (SELECT COALESCE(MAX(v.version_no), 0) FROM hub_lowcode_version v
                     WHERE v.tenant_id = a.tenant_id AND v.app_id = a.id AND v.deleted = 0) AS latest_version
              FROM hub_lowcode_app a
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="env != null and env != ''"> AND env = #{env}</if>
            <if test="status != null and status != ''"> AND status = #{status}</if>
             ORDER BY created_at DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("env") String env,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            <script>
            SELECT count(*)
              FROM hub_lowcode_app
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="env != null and env != ''"> AND env = #{env}</if>
            <if test="status != null and status != ''"> AND status = #{status}</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("env") String env,
               @Param("status") String status);

    @Update("""
            UPDATE hub_lowcode_app
               SET env = #{env}, status = 'PUBLISHED', published_at = now(),
                   updated_by = #{operatorId}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int markPublished(@Param("tenantId") long tenantId,
                      @Param("id") long id,
                      @Param("env") String env,
                      @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_lowcode_app
               SET schema = CAST(#{schema} AS jsonb), data_model = CAST(#{dataModel} AS jsonb),
                   status = 'PUBLISHED', env = #{env}, published_at = now(),
                   updated_by = #{operatorId}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int restoreVersion(@Param("tenantId") long tenantId,
                       @Param("id") long id,
                       @Param("schema") String schema,
                       @Param("dataModel") String dataModel,
                       @Param("env") String env,
                       @Param("operatorId") Long operatorId);
}
