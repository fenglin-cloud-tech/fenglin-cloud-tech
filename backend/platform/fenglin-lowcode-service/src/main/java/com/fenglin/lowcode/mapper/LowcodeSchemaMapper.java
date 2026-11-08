package com.fenglin.lowcode.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 补充 6 表中的表单/页面/模型 Schema 落库（文档未定义结构，按 create 请求的 schema.forms / schema.pages / dataModel.fields 索引）。
 */
@Mapper
public interface LowcodeSchemaMapper {

    @Insert("""
            INSERT INTO hub_lowcode_form (id, tenant_id, app_id, form_key, name, schema, sort,
                                          created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{formKey}, #{name}, CAST(#{schema} AS jsonb), #{sort},
                    #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insertForm(@Param("id") long id,
                   @Param("tenantId") long tenantId,
                   @Param("appId") long appId,
                   @Param("formKey") String formKey,
                   @Param("name") String name,
                   @Param("schema") String schema,
                   @Param("sort") int sort,
                   @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO hub_lowcode_page (id, tenant_id, app_id, page_key, name, schema, sort,
                                          created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{pageKey}, #{name}, CAST(#{schema} AS jsonb), #{sort},
                    #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insertPage(@Param("id") long id,
                   @Param("tenantId") long tenantId,
                   @Param("appId") long appId,
                   @Param("pageKey") String pageKey,
                   @Param("name") String name,
                   @Param("schema") String schema,
                   @Param("sort") int sort,
                   @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO hub_lowcode_model (id, tenant_id, app_id, field_name, field_type, required,
                                           max_length, default_value, sort, created_by, created_at,
                                           updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{fieldName}, #{fieldType}, #{required},
                    #{maxLength,jdbcType=INTEGER}, #{defaultValue,jdbcType=VARCHAR}, #{sort},
                    #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insertModel(@Param("id") long id,
                    @Param("tenantId") long tenantId,
                    @Param("appId") long appId,
                    @Param("fieldName") String fieldName,
                    @Param("fieldType") String fieldType,
                    @Param("required") int required,
                    @Param("maxLength") Integer maxLength,
                    @Param("defaultValue") String defaultValue,
                    @Param("sort") int sort,
                    @Param("operatorId") Long operatorId);

    @Select("""
            SELECT field_name, field_type, required, max_length, default_value
              FROM hub_lowcode_model
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
             ORDER BY sort
            """)
    List<Map<String, Object>> listModels(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Delete("DELETE FROM hub_lowcode_form WHERE tenant_id = #{tenantId} AND app_id = #{appId}")
    int deleteForms(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Delete("DELETE FROM hub_lowcode_page WHERE tenant_id = #{tenantId} AND app_id = #{appId}")
    int deletePages(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Delete("DELETE FROM hub_lowcode_model WHERE tenant_id = #{tenantId} AND app_id = #{appId}")
    int deleteModels(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
