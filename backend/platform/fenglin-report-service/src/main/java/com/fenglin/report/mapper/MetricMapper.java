package com.fenglin.report.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MetricMapper {

    @Insert("""
            INSERT INTO hub_metric (id, tenant_id, code, name, metric_type, formula, unit, grain,
                                    dimensions, lineage, owner_id, status, created_by, created_at,
                                    updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{metricType}, #{formula,jdbcType=VARCHAR},
                    #{unit,jdbcType=VARCHAR}, #{grain}, #{dimensions,jdbcType=VARCHAR},
                    CAST(#{lineage,jdbcType=VARCHAR} AS jsonb), #{ownerId,jdbcType=BIGINT}, #{status},
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("metricType") String metricType,
               @Param("formula") String formula,
               @Param("unit") String unit,
               @Param("grain") String grain,
               @Param("dimensions") String dimensions,
               @Param("lineage") String lineage,
               @Param("ownerId") Long ownerId,
               @Param("status") String status,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, code
              FROM hub_metric
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("""
            <script>
            SELECT id, code, name, metric_type, formula, unit, grain, dimensions,
                   CAST(lineage AS text) AS lineage_text, status
              FROM hub_metric
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="category != null and category != ''"> AND metric_type = #{category}</if>
            <if test="status != null and status != ''"> AND status = #{status}</if>
             ORDER BY created_at DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            <script>
            SELECT count(*)
              FROM hub_metric
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="category != null and category != ''"> AND metric_type = #{category}</if>
            <if test="status != null and status != ''"> AND status = #{status}</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("category") String category,
               @Param("status") String status);
}
