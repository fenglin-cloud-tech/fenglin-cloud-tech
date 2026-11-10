package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 开放接口文档元数据：平台全局发布，不含租户维度。
 */
@Mapper
public interface ApiDocMapper {

    @Select("""
            <script>
            SELECT code, method, path, name, domain, summary, description, required_scope,
                   request_schema, response_schema, sample_request, sample_response,
                   sandbox_enabled, status
              FROM hub_api_doc
             WHERE deleted = 0 AND status = 'PUBLISHED'
            <if test="domain != null"> AND domain = #{domain} </if>
             ORDER BY domain, sort, code
            </script>
            """)
    List<Map<String, Object>> listPublished(@Param("domain") String domain);

    @Select("""
            SELECT code, method, path, name, domain, summary, description, required_scope,
                   request_schema, response_schema, sample_request, sample_response,
                   sandbox_enabled, status
              FROM hub_api_doc
             WHERE code = #{code} AND deleted = 0 AND status = 'PUBLISHED'
            """)
    Map<String, Object> findPublished(@Param("code") String code);
}
