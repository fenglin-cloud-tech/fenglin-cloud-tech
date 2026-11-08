package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiModelMapper {

    @Select("""
            SELECT id, code, provider_type, model_type, base_url, routing_weight, api_key_fingerprint,
                   enabled
              FROM hub_ai_model
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("""
            SELECT id, code, provider_type, model_type, base_url, routing_weight, api_key_fingerprint,
                   enabled
              FROM hub_ai_model
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, code, provider_type, model_type, base_url, routing_weight, api_key_fingerprint,
                   enabled
              FROM hub_ai_model
             WHERE tenant_id = #{tenantId} AND deleted = 0
               AND (CAST(#{modelType,jdbcType=VARCHAR} AS varchar) IS NULL
                    OR model_type = CAST(#{modelType,jdbcType=VARCHAR} AS varchar))
               AND (CAST(#{providerType,jdbcType=VARCHAR} AS varchar) IS NULL
                    OR provider_type = CAST(#{providerType,jdbcType=VARCHAR} AS varchar))
             ORDER BY created_at DESC, id DESC
             LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("modelType") String modelType,
                                   @Param("providerType") String providerType,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            SELECT count(1)
              FROM hub_ai_model
             WHERE tenant_id = #{tenantId} AND deleted = 0
               AND (CAST(#{modelType,jdbcType=VARCHAR} AS varchar) IS NULL
                    OR model_type = CAST(#{modelType,jdbcType=VARCHAR} AS varchar))
               AND (CAST(#{providerType,jdbcType=VARCHAR} AS varchar) IS NULL
                    OR provider_type = CAST(#{providerType,jdbcType=VARCHAR} AS varchar))
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("modelType") String modelType,
               @Param("providerType") String providerType);

    @Select("""
            SELECT id, code, provider_type, model_type, base_url, routing_weight, api_key_fingerprint
              FROM hub_ai_model
             WHERE (tenant_id = #{tenantId} OR tenant_id = 0)
               AND model_type = 'LLM' AND enabled = 1 AND deleted = 0
             ORDER BY tenant_id DESC, routing_weight DESC, created_at DESC
             LIMIT 1
            """)
    Map<String, Object> findEnabledLlm(@Param("tenantId") long tenantId);

    @Select("""
            SELECT api_key_enc FROM hub_ai_model
             WHERE id = #{id} AND deleted = 0
            """)
    String findApiKeyEnc(@Param("id") long id);

    @Insert("""
            INSERT INTO hub_ai_model (id, tenant_id, code, provider, model_name, base_url, api_key_enc,
                                      capabilities, context_window, enabled, provider_type, model_type,
                                      routing_weight, api_key_fingerprint, created_by, created_at,
                                      updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{providerType}, #{code}, #{endpoint},
                    #{apiKeyEnc}, NULL, NULL, 1, #{providerType}, #{modelType}, #{routingWeight},
                    #{fingerprint}, #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("providerType") String providerType,
               @Param("modelType") String modelType,
               @Param("endpoint") String endpoint,
               @Param("apiKeyEnc") String apiKeyEnc,
               @Param("routingWeight") int routingWeight,
               @Param("fingerprint") String fingerprint,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_ai_model
               SET provider_type = #{providerType},
                   model_type = #{modelType},
                   base_url = #{endpoint},
                   api_key_enc = COALESCE(CAST(#{apiKeyEnc,jdbcType=VARCHAR} AS varchar), api_key_enc),
                   api_key_fingerprint = COALESCE(CAST(#{fingerprint,jdbcType=VARCHAR} AS varchar),
                                                  api_key_fingerprint),
                   routing_weight = #{routingWeight},
                   enabled = #{enabled},
                   updated_by = #{operatorId,jdbcType=BIGINT},
                   updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int update(@Param("tenantId") long tenantId,
               @Param("id") long id,
               @Param("providerType") String providerType,
               @Param("modelType") String modelType,
               @Param("endpoint") String endpoint,
               @Param("apiKeyEnc") String apiKeyEnc,
               @Param("fingerprint") String fingerprint,
               @Param("routingWeight") int routingWeight,
               @Param("enabled") int enabled,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_ai_model
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int softDelete(@Param("tenantId") long tenantId,
                   @Param("id") long id,
                   @Param("operatorId") Long operatorId);
}
