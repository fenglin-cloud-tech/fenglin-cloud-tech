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

    @Select("""
            <script>
            SELECT ds.id, ds.code, ds.name, ds.description, ds.status,
                   (SELECT count(1) FROM hub_report_dataset_field f
                     WHERE f.tenant_id = ds.tenant_id AND f.dataset_id = ds.id AND f.deleted = 0)
                     AS field_count
              FROM hub_report_dataset ds
             WHERE ds.tenant_id = #{tenantId} AND ds.deleted = 0
             <if test="status != null"> AND ds.status = #{status} </if>
             ORDER BY ds.created_at DESC, ds.id DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            <script>
            SELECT count(1)
              FROM hub_report_dataset ds
             WHERE ds.tenant_id = #{tenantId} AND ds.deleted = 0
             <if test="status != null"> AND ds.status = #{status} </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId, @Param("status") String status);
}
