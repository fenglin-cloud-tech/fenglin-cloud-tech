package com.fenglin.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageTemplateMapper {

    @Select("""
            SELECT id, code, name, channel, title_tpl, content_tpl, variables, locale, status
              FROM hub_message_template
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("""
            SELECT id, code, name, channel, title_tpl, content_tpl, variables, locale, status
              FROM hub_message_template
             WHERE tenant_id = #{tenantId} AND deleted = 0
             ORDER BY code
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO hub_message_template (id, tenant_id, code, name, channel, title_tpl, content_tpl,
                                              variables, locale, status, created_by, created_at,
                                              updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{channel}, #{titleTpl}, #{contentTpl},
                    #{variables}, #{locale}, #{status}, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, code)
            DO UPDATE SET name = EXCLUDED.name,
                          channel = EXCLUDED.channel,
                          title_tpl = EXCLUDED.title_tpl,
                          content_tpl = EXCLUDED.content_tpl,
                          variables = EXCLUDED.variables,
                          locale = EXCLUDED.locale,
                          status = EXCLUDED.status,
                          deleted = 0,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_message_template.version + 1
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("channel") String channel,
               @Param("titleTpl") String titleTpl,
               @Param("contentTpl") String contentTpl,
               @Param("variables") String variables,
               @Param("locale") String locale,
               @Param("status") String status,
               @Param("operatorId") Long operatorId);
}
