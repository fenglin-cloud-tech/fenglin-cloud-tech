package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowFormDataMapper {

    @Insert("""
            INSERT INTO hub_wf_form_data (id, tenant_id, instance_id, form_key, form_data, form_version,
                                          created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{instanceId}, #{formKey,jdbcType=VARCHAR}, #{formData}::jsonb, 1,
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("instanceId") long instanceId,
               @Param("formKey") String formKey,
               @Param("formData") String formData,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT form_key, form_data::text AS form_data, form_version
              FROM hub_wf_form_data
             WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} AND deleted = 0
             ORDER BY form_version DESC
            """)
    List<Map<String, Object>> findByInstance(@Param("tenantId") long tenantId,
                                             @Param("instanceId") long instanceId);
}
