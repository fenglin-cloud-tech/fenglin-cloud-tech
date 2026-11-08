package com.fenglin.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileUserMapper {

    @Select("""
            SELECT COALESCE(real_name, nickname, username, '用户' || id)
              FROM hub_user
             WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
            """)
    String findDisplayName(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
