package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface AiModelMapper {

    @Select("""
            SELECT id, code, provider_type, model_type, base_url, routing_weight, api_key_fingerprint
              FROM hub_ai_model
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

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
}
