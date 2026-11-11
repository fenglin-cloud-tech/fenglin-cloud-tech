package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    /** 登录成功：清零失败次数并解锁（locked_until 需显式置空，updateById 不会写 null） */
    @Update("""
            UPDATE hub_account
               SET fail_count = 0, locked_until = NULL, updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    int resetFailState(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /** 记录一次失败，达到阈值时写入锁定截止时间 */
    @Update("""
            UPDATE hub_account
               SET fail_count = #{failCount},
                   locked_until = #{lockedUntil,jdbcType=TIMESTAMP},
                   updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    int updateFailState(@Param("tenantId") Long tenantId,
                        @Param("userId") Long userId,
                        @Param("failCount") int failCount,
                        @Param("lockedUntil") java.time.Instant lockedUntil);

    /** 自助改密：更新哈希与时间戳，并清零失败计数与锁定时间 */
    @Update("""
            UPDATE hub_account
               SET password_hash = #{passwordHash},
                   password_updated_at = now(),
                   fail_count = 0,
                   locked_until = NULL,
                   updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = 0
            """)
    int changePassword(@Param("tenantId") Long tenantId,
                       @Param("userId") Long userId,
                       @Param("passwordHash") String passwordHash);
}
