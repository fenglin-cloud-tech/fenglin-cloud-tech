package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
