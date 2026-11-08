package com.fenglin.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface ReportDefMapper {

    @Select("""
            SELECT id, code, name, report_type, dataset_id, CAST(config AS text) AS config_text, status
              FROM hub_report_def
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);
}
