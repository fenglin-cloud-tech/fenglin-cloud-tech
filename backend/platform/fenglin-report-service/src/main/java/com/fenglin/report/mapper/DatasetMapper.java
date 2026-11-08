package com.fenglin.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DatasetMapper {

    @Select("""
            SELECT id, code, name, description, sql_template, status
              FROM hub_report_dataset
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT field_name, column_name, field_role, aggregate, data_type, sort
              FROM hub_report_dataset_field
             WHERE tenant_id = #{tenantId} AND dataset_id = #{datasetId} AND deleted = 0
             ORDER BY sort, field_name
            """)
    List<Map<String, Object>> findFields(@Param("tenantId") long tenantId,
                                         @Param("datasetId") long datasetId);
}
