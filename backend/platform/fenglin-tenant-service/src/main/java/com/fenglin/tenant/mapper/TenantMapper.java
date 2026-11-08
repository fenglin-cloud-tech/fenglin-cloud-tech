package com.fenglin.tenant.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface TenantMapper {

    @Select("""
            SELECT id, code, name, status, contact_name, expire_at, created_at, updated_at
              FROM hub_tenant
             WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("id") long id);

    @Select("""
            SELECT id, code, name, status, contact_name, expire_at, created_at, updated_at
              FROM hub_tenant
             WHERE code = #{code} AND deleted = 0
            """)
    Map<String, Object> findByCode(@Param("code") String code);

    @Select("""
            <script>
            SELECT id, code, name, status, contact_name, expire_at, created_at, updated_at
              FROM hub_tenant
             WHERE deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="keyword != null and keyword != ''">
               AND (code LIKE '%' || #{keyword} || '%' OR name LIKE '%' || #{keyword} || '%')
             </if>
             ORDER BY id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> findPage(@Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM hub_tenant
             WHERE deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="keyword != null and keyword != ''">
               AND (code LIKE '%' || #{keyword} || '%' OR name LIKE '%' || #{keyword} || '%')
             </if>
            </script>
            """)
    long count(@Param("status") String status, @Param("keyword") String keyword);

    @Select("SELECT count(*) FROM hub_tenant WHERE code = #{code}")
    long countByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO hub_tenant (id, code, name, status, contact_name, hash_salt_enc, hash_version,
                                    expire_at, created_at, updated_at, deleted)
            VALUES (#{id}, #{code}, #{name}, #{status}, #{contactName}, #{hashSaltEnc}, 1,
                    #{expireAt,jdbcType=TIMESTAMP}, now(), now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("code") String code,
               @Param("name") String name,
               @Param("status") String status,
               @Param("contactName") String contactName,
               @Param("hashSaltEnc") String hashSaltEnc,
               @Param("expireAt") Instant expireAt);

    @Update("""
            UPDATE hub_tenant SET status = #{status}, updated_at = now()
             WHERE id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Select("""
            SELECT id, code, name FROM hub_package
             WHERE code = #{code} AND deleted = 0 AND status = 'ACTIVE'
            """)
    Map<String, Object> findActivePackageByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO hub_subscription (id, tenant_id, package_id, status, trial, auto_renew,
                                          start_at, end_at, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{packageId}, 'ACTIVE', #{trial}, 0,
                    #{startAt,jdbcType=TIMESTAMP}, #{endAt,jdbcType=TIMESTAMP}, now(), now(), 0)
            """)
    int insertSubscription(@Param("id") long id,
                           @Param("tenantId") long tenantId,
                           @Param("packageId") long packageId,
                           @Param("trial") int trial,
                           @Param("startAt") Instant startAt,
                           @Param("endAt") Instant endAt);

    /**
     * 初始化租户管理员角色时授予除平台专属权限外的全部功能权限（platform:* 不下放租户）。
     */
    @Select("""
            SELECT code FROM hub_permission
             WHERE deleted = 0 AND code NOT LIKE 'platform:%'
             ORDER BY code
            """)
    List<String> listTenantGrantablePermissionCodes();

    @Insert("""
            INSERT INTO hub_role (id, tenant_id, code, name, role_type, data_scope, builtin,
                                  status, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, 'SYS', 'ALL', 1,
                    'ENABLED', now(), now(), 0)
            """)
    int insertBuiltinRole(@Param("id") long id,
                          @Param("tenantId") long tenantId,
                          @Param("code") String code,
                          @Param("name") String name);

    @Insert("""
            INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{roleId}, #{permissionCode}, now(), now(), 0)
            ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING
            """)
    int grantPermission(@Param("id") long id,
                        @Param("tenantId") long tenantId,
                        @Param("roleId") long roleId,
                        @Param("permissionCode") String permissionCode);

    @Insert("""
            INSERT INTO hub_user (id, tenant_id, uuid, username, real_name, user_type,
                                  status, mfa_enabled, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, gen_random_uuid(), #{username}, #{realName}, 'INTERNAL',
                    'ACTIVE', 0, now(), now(), 0)
            """)
    int insertAdminUser(@Param("id") long id,
                        @Param("tenantId") long tenantId,
                        @Param("username") String username,
                        @Param("realName") String realName);

    @Insert("""
            INSERT INTO hub_account (id, tenant_id, user_id, password_hash, password_updated_at,
                                     fail_count, pwd_reset_required, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{userId}, #{passwordHash}, now(), 0, 1, now(), now(), 0)
            """)
    int insertAccount(@Param("id") long id,
                      @Param("tenantId") long tenantId,
                      @Param("userId") long userId,
                      @Param("passwordHash") String passwordHash);

    @Insert("""
            INSERT INTO hub_user_role (id, tenant_id, user_id, role_id, granted_at, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{userId}, #{roleId}, now(), now(), now(), 0)
            """)
    int insertUserRole(@Param("id") long id,
                       @Param("tenantId") long tenantId,
                       @Param("userId") long userId,
                       @Param("roleId") long roleId);
}
