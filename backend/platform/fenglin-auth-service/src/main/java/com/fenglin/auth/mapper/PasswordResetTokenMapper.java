package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.PasswordResetTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenEntity> {

    /**
     * 单次使用：仅当未使用、未过期时置 used_at，返回 1 表示本次消费成功；
     * 并发重复使用时只有一个事务能拿到 1。
     */
    @Update("""
            UPDATE hub_password_reset_token
               SET used_at = now()
             WHERE tenant_id = #{tenantId} AND token_hash = #{tokenHash}
               AND used_at IS NULL AND expires_at > now() AND deleted = 0
            """)
    int consume(@Param("tenantId") long tenantId, @Param("tokenHash") String tokenHash);
}
