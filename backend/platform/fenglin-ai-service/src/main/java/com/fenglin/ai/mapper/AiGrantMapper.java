package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiGrantMapper {

    @Select("""
            SELECT id, host_product, capability, enabled, quota_qps, quota_token_month, billing_mode
              FROM hub_ai_capability_grant
             WHERE tenant_id = #{tenantId} AND host_product = #{hostProduct}
               AND capability = #{capability} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId,
                             @Param("hostProduct") String hostProduct,
                             @Param("capability") String capability);

    @Insert("""
            INSERT INTO hub_ai_capability_grant (id, tenant_id, host_product, capability, enabled,
                                                 quota_qps, quota_token_month, billing_mode,
                                                 created_by, created_at, updated_by, updated_at,
                                                 deleted, version)
            VALUES (#{id}, #{tenantId}, #{hostProduct}, #{capability}, #{enabled}, #{quotaQps},
                    #{quotaTokenMonth}, #{billingMode}, #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            ON CONFLICT (tenant_id, host_product, capability) WHERE deleted = 0
            DO UPDATE SET enabled = EXCLUDED.enabled,
                          quota_qps = EXCLUDED.quota_qps,
                          quota_token_month = EXCLUDED.quota_token_month,
                          billing_mode = EXCLUDED.billing_mode,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_ai_capability_grant.version + 1
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("hostProduct") String hostProduct,
               @Param("capability") String capability,
               @Param("enabled") int enabled,
               @Param("quotaQps") int quotaQps,
               @Param("quotaTokenMonth") long quotaTokenMonth,
               @Param("billingMode") String billingMode,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, host_product, capability, enabled, quota_qps, quota_token_month, billing_mode
              FROM hub_ai_capability_grant
             WHERE tenant_id = #{tenantId} AND deleted = 0
             ORDER BY host_product, capability
            """)
    List<Map<String, Object>> listAll(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE hub_ai_capability_grant
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND host_product = #{hostProduct}
               AND capability = #{capability} AND deleted = 0
            """)
    int softDelete(@Param("tenantId") long tenantId,
                   @Param("hostProduct") String hostProduct,
                   @Param("capability") String capability,
                   @Param("operatorId") Long operatorId);
}
