package com.fenglin.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportDefMapper {

    @Select("""
            SELECT id, code, name, report_type, dataset_id, CAST(config AS text) AS config_text, status
              FROM hub_report_def
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, code, name, report_type, dataset_id, status
              FROM hub_report_def
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="reportType != null"> AND report_type = #{reportType} </if>
             <if test="status != null"> AND status = #{status} </if>
             ORDER BY created_at DESC, id DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("reportType") String reportType,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            <script>
            SELECT count(1)
              FROM hub_report_def
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="reportType != null"> AND report_type = #{reportType} </if>
             <if test="status != null"> AND status = #{status} </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("reportType") String reportType,
               @Param("status") String status);
}
