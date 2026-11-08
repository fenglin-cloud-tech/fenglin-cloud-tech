package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PermissionMapper {

    @Select("""
            <script>
            SELECT code, name, type, domain, parent_code, sort
              FROM hub_permission
             WHERE deleted = 0
             <if test="domain != null and domain != ''">AND domain = #{domain}</if>
             ORDER BY domain, sort, code
            </script>
            """)
    List<Map<String, Object>> list(@Param("domain") String domain);

    @Select("""
            <script>
            SELECT count(*) FROM hub_permission
             WHERE deleted = 0 AND code IN
             <foreach collection="codes" item="code" open="(" separator="," close=")">#{code}</foreach>
            </script>
            """)
    long countByCodes(@Param("codes") List<String> codes);
}
