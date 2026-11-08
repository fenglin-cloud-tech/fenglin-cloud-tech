package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Update("""
            UPDATE hub_user SET last_login_at = now(), updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int touchLastLogin(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
