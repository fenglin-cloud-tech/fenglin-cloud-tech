package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 租户集成与度量配置 Mapper（管理端集成配置）。
 * RND-09：配置按环境（dev/test/staging/prod）各存一条，唯一键为 (tenant_id, env)。
 */
@Mapper
public interface TenantConfigMapper {

    @Select("""
            SELECT id AS "id", env AS "env", config_json::text AS "configJson", timezone AS "timezone",
                   data_retention_months AS "dataRetentionMonths", dora_target_level AS "doraTargetLevel",
                   webhook_base_url AS "webhookBaseUrl", updated_at::text AS "updatedAt"
            FROM devops_tenant_config
            WHERE tenant_id = #{tenantId} AND env = #{env} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("env") String env);

    @Insert("""
            INSERT INTO devops_tenant_config
                (id, tenant_id, env, config_json, timezone, data_retention_months, dora_target_level,
                 webhook_base_url, created_by)
            VALUES (#{id}, #{tenantId}, #{env}, CAST(#{configJson} AS jsonb), #{timezone}, #{dataRetentionMonths},
                    #{doraTargetLevel}, #{webhookBaseUrl,jdbcType=VARCHAR}, #{userId})
            ON CONFLICT (tenant_id, env) WHERE deleted = 0
            DO UPDATE SET config_json = EXCLUDED.config_json, timezone = EXCLUDED.timezone,
                          data_retention_months = EXCLUDED.data_retention_months,
                          dora_target_level = EXCLUDED.dora_target_level,
                          webhook_base_url = EXCLUDED.webhook_base_url,
                          updated_by = #{userId}, version = devops_tenant_config.version + 1
            """)
    int upsert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("env") String env,
               @Param("configJson") String configJson, @Param("timezone") String timezone,
               @Param("dataRetentionMonths") int dataRetentionMonths,
               @Param("doraTargetLevel") String doraTargetLevel,
               @Param("webhookBaseUrl") String webhookBaseUrl, @Param("userId") long userId);
}
