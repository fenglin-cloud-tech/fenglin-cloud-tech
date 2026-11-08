package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MenuMapper {

    @Select("""
            <script>
            SELECT code, name, client_type, path, component, parent_code, icon, sort, visible, permission_code
              FROM hub_menu
             WHERE deleted = 0 AND visible = 1
             <if test="clientType != null and clientType != ''">AND client_type = #{clientType}</if>
             ORDER BY sort, code
            </script>
            """)
    List<Map<String, Object>> list(@Param("clientType") String clientType);
}
