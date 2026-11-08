package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 租户数据密钥（DEK 密文 + 哈希盐）。
 */
@Mapper
public interface TalentTenantKeyMapper {

    @Select("""
            SELECT id AS "id", tenant_salt AS "tenantSalt", key_version AS "keyVersion"
            FROM talent_tenant_key
            WHERE tenant_id = #{tenantId} AND key_status = 'ACTIVE' AND deleted = 0
            ORDER BY key_version DESC
            LIMIT 1
            """)
    Map<String, Object> findActive(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO talent_tenant_key
                (id, tenant_id, key_version, dek_cipher, tenant_salt, key_status, created_by)
            VALUES (#{id}, #{tenantId}, #{keyVersion}, #{dekCipher}, #{tenantSalt}, 'ACTIVE', #{userId})
            """)
    void insertKey(@Param("id") long id, @Param("tenantId") long tenantId,
                   @Param("keyVersion") int keyVersion, @Param("dekCipher") String dekCipher,
                   @Param("tenantSalt") String tenantSalt, @Param("userId") long userId);
}
