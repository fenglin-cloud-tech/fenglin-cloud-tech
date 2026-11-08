package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.LoginSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LoginSessionMapper extends BaseMapper<LoginSessionEntity> {

    @Update("""
            UPDATE hub_login_session
               SET status = #{status}, updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND status = 'ONLINE' AND deleted = 0
            """)
    int closeAllByUser(@Param("tenantId") Long tenantId,
                       @Param("userId") Long userId,
                       @Param("status") String status);

    @Update("""
            UPDATE hub_login_session
               SET status = #{status}, updated_at = now()
             WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}
               AND status = 'ONLINE' AND deleted = 0
            """)
    int closeBySession(@Param("tenantId") Long tenantId,
                       @Param("sessionId") String sessionId,
                       @Param("status") String status);
}
