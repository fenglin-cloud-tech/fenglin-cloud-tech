package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface AiPromptMapper {

    @Select("""
            SELECT id, code, prompt_version, name, scene, system_prompt, user_prompt,
                   CAST(variables AS text) AS variables_text, status
              FROM hub_ai_prompt
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
             ORDER BY prompt_version DESC
             LIMIT 1
            """)
    Map<String, Object> findLatest(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO hub_ai_prompt (id, tenant_id, code, prompt_version, content, variables, status,
                                       name, scene, system_prompt, user_prompt, created_by, created_at,
                                       updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{promptVersion}, #{content},
                    CAST(#{variables} AS jsonb), 'PUBLISHED', #{name}, #{scene}, #{systemPrompt},
                    #{userPrompt}, #{operatorId}, now(), #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("promptVersion") int promptVersion,
               @Param("content") String content,
               @Param("variables") String variables,
               @Param("name") String name,
               @Param("scene") String scene,
               @Param("systemPrompt") String systemPrompt,
               @Param("userPrompt") String userPrompt,
               @Param("operatorId") Long operatorId);
}
