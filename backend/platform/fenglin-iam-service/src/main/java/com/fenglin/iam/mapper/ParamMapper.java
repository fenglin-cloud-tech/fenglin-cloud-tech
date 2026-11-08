package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ParamMapper {

    @Select("""
            <script>
            SELECT tenant_id, param_key, param_value, scope, product_code, value_type, remark
              FROM hub_param
             WHERE deleted = 0
               AND (tenant_id = 0 OR tenant_id = #{tenantId})
             <if test="scope != null and scope != ''">AND scope = #{scope}</if>
             <if test="productCode != null and productCode != ''">AND product_code = #{productCode}</if>
             ORDER BY param_key
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("scope") String scope,
                                   @Param("productCode") String productCode);

    @Select("""
            SELECT tenant_id, param_key, param_value, scope, product_code, value_type, remark
              FROM hub_param
             WHERE param_key = #{paramKey} AND deleted = 0
               AND (tenant_id = 0 OR tenant_id = #{tenantId})
             ORDER BY tenant_id DESC
            """)
    List<Map<String, Object>> findByKey(@Param("tenantId") long tenantId,
                                        @Param("paramKey") String paramKey);

    @Insert("""
            INSERT INTO hub_param (id, tenant_id, param_key, param_value, scope, product_code, value_type,
                                   remark, created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{paramKey}, #{paramValue}, #{scope},
                    #{productCode,jdbcType=VARCHAR}, #{valueType}, #{remark,jdbcType=VARCHAR},
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, param_key)
            DO UPDATE SET param_value = EXCLUDED.param_value,
                          scope = EXCLUDED.scope,
                          product_code = EXCLUDED.product_code,
                          value_type = EXCLUDED.value_type,
                          remark = EXCLUDED.remark,
                          deleted = 0,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_param.version + 1
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("paramKey") String paramKey,
               @Param("paramValue") String paramValue,
               @Param("scope") String scope,
               @Param("productCode") String productCode,
               @Param("valueType") String valueType,
               @Param("remark") String remark,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_param
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND param_key = #{paramKey} AND deleted = 0
            """)
    int delete(@Param("tenantId") long tenantId,
               @Param("paramKey") String paramKey,
               @Param("operatorId") Long operatorId);
}
