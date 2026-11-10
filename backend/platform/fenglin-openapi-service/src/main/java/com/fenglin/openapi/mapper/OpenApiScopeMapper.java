package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 开放接口权限目录：直接读取 hub_permission 中产品侧 API 权限点，避免前端硬编码或造数据。
 */
@Mapper
public interface OpenApiScopeMapper {

    @Select("""
            <script>
            SELECT code, name, domain
              FROM hub_permission
             WHERE deleted = 0 AND type = 'API'
               AND code NOT LIKE 'platform:%'
               AND code NOT LIKE 'tenant:%'
               AND code NOT LIKE 'hub:%'
            <if test="domain != null"> AND domain = #{domain} </if>
             ORDER BY domain, code
            </script>
            """)
    List<Map<String, Object>> listOpenableScopes(@Param("domain") String domain);
}
