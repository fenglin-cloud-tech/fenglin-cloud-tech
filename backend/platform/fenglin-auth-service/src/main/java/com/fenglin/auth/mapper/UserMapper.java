package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Update("""
            UPDATE hub_user SET last_login_at = now(), updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int touchLastLogin(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /** PLAT-22：改密/重置后递增用户令牌版本，令旧 access token 失效 */
    @Update("""
            UPDATE hub_user SET token_version = token_version + 1, updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int incrementTokenVersion(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("""
            SELECT token_version FROM hub_user
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Long findTokenVersion(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
