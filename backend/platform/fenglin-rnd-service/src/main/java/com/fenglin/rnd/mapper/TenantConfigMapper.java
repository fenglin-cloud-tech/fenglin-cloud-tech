package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 租户集成与度量配置 Mapper（管理端集成配置）。
 */
@Mapper
public interface TenantConfigMapper {

    @Select("""
            SELECT id AS "id", config_json::text AS "configJson", timezone AS "timezone",
                   data_retention_months AS "dataRetentionMonths", dora_target_level AS "doraTargetLevel",
                   webhook_base_url AS "webhookBaseUrl", updated_at::text AS "updatedAt"
            FROM devops_tenant_config
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO devops_tenant_config
                (id, tenant_id, config_json, timezone, data_retention_months, dora_target_level,
                 webhook_base_url, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{configJson} AS jsonb), #{timezone}, #{dataRetentionMonths},
                    #{doraTargetLevel}, #{webhookBaseUrl,jdbcType=VARCHAR}, #{userId})
            ON CONFLICT (tenant_id) WHERE deleted = 0
            DO UPDATE SET config_json = EXCLUDED.config_json, timezone = EXCLUDED.timezone,
                          data_retention_months = EXCLUDED.data_retention_months,
                          dora_target_level = EXCLUDED.dora_target_level,
                          webhook_base_url = EXCLUDED.webhook_base_url,
                          updated_by = #{userId}, version = devops_tenant_config.version + 1
            """)
    int upsert(@Param("id") long id, @Param("tenantId") long tenantId,
               @Param("configJson") String configJson, @Param("timezone") String timezone,
               @Param("dataRetentionMonths") int dataRetentionMonths,
               @Param("doraTargetLevel") String doraTargetLevel,
               @Param("webhookBaseUrl") String webhookBaseUrl, @Param("userId") long userId);
}
