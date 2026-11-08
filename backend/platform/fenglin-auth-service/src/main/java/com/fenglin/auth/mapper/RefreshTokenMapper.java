package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    @Update("""
            UPDATE hub_auth_token
               SET revoked = 1, rotated_at = now(), updated_at = now()
             WHERE tenant_id = #{tenantId} AND token_hash = #{tokenHash} AND revoked = 0
            """)
    int revokeByHash(@Param("tenantId") Long tenantId, @Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE hub_auth_token
               SET revoked = 1, updated_at = now()
             WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND revoked = 0
            """)
    int revokeBySession(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);

    @Update("""
            UPDATE hub_auth_token
               SET revoked = 1, updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND revoked = 0
            """)
    int revokeAllByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
