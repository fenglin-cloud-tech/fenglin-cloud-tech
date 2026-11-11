package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper {

    @Insert("""
            INSERT INTO hub_account (id, tenant_id, user_id, password_hash, password_updated_at,
                                     fail_count, pwd_reset_required, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{userId}, #{passwordHash}, now(), 0, 0, now(), now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("userId") long userId,
               @Param("passwordHash") String passwordHash);

    /**
     * 管理员重置口令：更新哈希与时间戳，并清零失败计数与锁定时间；permission_version 不在本更新范围。
     */
    @Update("""
            UPDATE hub_account
               SET password_hash = #{passwordHash},
                   password_updated_at = now(),
                   fail_count = 0,
                   locked_until = NULL,
                   updated_at = now()
             WHERE tenant_id = #{tenantId}
               AND user_id = #{userId}
               AND deleted = 0
            """)
    int resetPassword(@Param("tenantId") long tenantId,
                      @Param("userId") long userId,
                      @Param("passwordHash") String passwordHash);
}
