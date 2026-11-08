package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
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

    @Select("""
            SELECT latest.*
              FROM (
                    SELECT DISTINCT ON (code)
                           id, code, prompt_version, name, scene, system_prompt, user_prompt,
                           CAST(variables AS text) AS variables_text, status, created_at
                      FROM hub_ai_prompt
                     WHERE tenant_id = #{tenantId} AND deleted = 0
                       AND (CAST(#{scene,jdbcType=VARCHAR} AS varchar) IS NULL
                            OR scene = CAST(#{scene,jdbcType=VARCHAR} AS varchar))
                     ORDER BY code, prompt_version DESC
                   ) latest
             ORDER BY latest.created_at DESC, latest.code
             LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listLatest(@Param("tenantId") long tenantId,
                                         @Param("scene") String scene,
                                         @Param("offset") long offset,
                                         @Param("size") long size);

    @Select("""
            SELECT count(DISTINCT code)
              FROM hub_ai_prompt
             WHERE tenant_id = #{tenantId} AND deleted = 0
               AND (CAST(#{scene,jdbcType=VARCHAR} AS varchar) IS NULL
                    OR scene = CAST(#{scene,jdbcType=VARCHAR} AS varchar))
            """)
    long countLatest(@Param("tenantId") long tenantId, @Param("scene") String scene);

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

    @Update("""
            UPDATE hub_ai_prompt
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
            """)
    int softDeleteByCode(@Param("tenantId") long tenantId,
                         @Param("code") String code,
                         @Param("operatorId") Long operatorId);
}
