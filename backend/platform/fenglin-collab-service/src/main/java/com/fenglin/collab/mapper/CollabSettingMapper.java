package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 租户级配置（collab_setting）读写：敏感词策略、文档密级策略等。
 */
@Mapper
public interface CollabSettingMapper {

    @Select("""
            SELECT id AS "settingId", CAST(content AS varchar) AS "content"
            FROM collab_setting
            WHERE tenant_id = #{tenantId} AND setting_key = #{settingKey} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("settingKey") String settingKey);

    @Insert("""
            INSERT INTO collab_setting (id, tenant_id, setting_key, content, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{settingKey}, CAST(#{content} AS jsonb), #{userId}, #{userId})
            ON CONFLICT (tenant_id, setting_key) DO UPDATE
            SET content = CAST(EXCLUDED.content AS jsonb), updated_by = EXCLUDED.updated_by,
                updated_at = now(), deleted = 0
            """)
    void upsert(@Param("id") long id, @Param("tenantId") long tenantId,
                @Param("settingKey") String settingKey, @Param("content") String content,
                @Param("userId") long userId);
}
