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

    /**
     * 平台管理员角色（PLATFORM_ADMIN）应持有的平台专属权限。
     * 只含代码中真实校验的 9 条，口径与 V1_0_49__align_platform_admin_permissions.sql 一致：
     * platform:billing:manage（能力未实现）与 platform:workflow:engine:manage（废弃，实际用 tenant:workflow:*）不授予。
     */
    @Select("""
            SELECT code FROM hub_permission
             WHERE deleted = 0
               AND code IN (
                   'platform:ai:model:manage',
                   'platform:audit:alert:manage',
                   'platform:audit:export',
                   'platform:openapi:app:review',
                   'platform:openapi:calllog:view',
                   'platform:openapi:key:manage',
                   'platform:openapi:quota:manage',
                   'platform:search:index:manage',
                   'platform:tenant:manage'
               )
             ORDER BY code
            """)
    List<String> listPlatformPermissionCodes();

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

    /** PLAT-08：新租户固定内置的普通员工角色，数据范围仅本人。 */
    @Insert("""
            INSERT INTO hub_role (id, tenant_id, code, name, role_type, data_scope, builtin,
                                  status, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, 'EMPLOYEE', '普通员工', 'BIZ', 'SELF', 1,
                    'ENABLED', now(), now(), 0)
            """)
    int insertEmployeeRole(@Param("id") long id, @Param("tenantId") long tenantId);

    /** 只含自助类权限（talent:self:*），不包含任何管理权限包。 */
    @Select("""
            SELECT code FROM hub_permission
             WHERE deleted = 0 AND code LIKE 'talent:self:%'
             ORDER BY code
            """)
    List<String> listSelfServicePermissionCodes();

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

    // ---------- 平台管理员分配/回收 ----------

    @Select("""
            SELECT id FROM hub_role
             WHERE tenant_id = #{tenantId} AND code = 'PLATFORM_ADMIN' AND deleted = 0
            """)
    Long findPlatformAdminRoleId(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id, username, status FROM hub_user
             WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
            """)
    Map<String, Object> findTenantUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT id, deleted FROM hub_user_role
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND role_id = #{roleId}
             ORDER BY deleted ASC
             LIMIT 1
            """)
    Map<String, Object> findUserRole(@Param("tenantId") long tenantId,
                                     @Param("userId") long userId,
                                     @Param("roleId") long roleId);

    @Update("""
            UPDATE hub_user_role SET deleted = 0, granted_at = now(), updated_at = now()
             WHERE id = #{id}
            """)
    int restoreUserRole(@Param("id") long id);

    @Update("""
            UPDATE hub_user_role SET deleted = 1, updated_at = now()
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND role_id = #{roleId} AND deleted = 0
            """)
    int revokeUserRole(@Param("tenantId") long tenantId,
                       @Param("userId") long userId,
                       @Param("roleId") long roleId);

    @Select("""
            SELECT count(DISTINCT ur.user_id)
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE ur.tenant_id = #{tenantId} AND r.code = 'PLATFORM_ADMIN' AND ur.deleted = 0
            """)
    long countPlatformAdmins(@Param("tenantId") long tenantId);

    @Select("""
            SELECT ur.role_id
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE ur.tenant_id = #{tenantId} AND ur.user_id = #{userId}
               AND r.code = 'PLATFORM_ADMIN' AND ur.deleted = 0
             LIMIT 1
            """)
    Long findPlatformAdminRoleOfUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
