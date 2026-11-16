package com.fenglin.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionQueryMapper {

    @Select("""
            SELECT DISTINCT rp.permission_code
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
              JOIN hub_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
              JOIN hub_permission p ON p.code = rp.permission_code AND p.deleted = 0
             WHERE ur.user_id = #{userId}
               AND ur.deleted = 0
               AND (ur.expire_at IS NULL OR ur.expire_at > now())
             ORDER BY rp.permission_code
            """)
    List<String> findPermissionCodes(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT r.code
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
             WHERE ur.user_id = #{userId}
               AND ur.deleted = 0
               AND (ur.expire_at IS NULL OR ur.expire_at > now())
             ORDER BY r.code
            """)
    List<String> findRoleCodes(@Param("userId") Long userId);

    /**
     * OPEN-06：平台切换后按目标租户的 TENANT_ADMIN 角色取权限，供平台分支再按治理域前缀收敛。
     * RLS 由当前 X-Tenant-Id（目标租户）承担。
     */
    @Select("""
            SELECT DISTINCT rp.permission_code
              FROM hub_role r
              JOIN hub_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
              JOIN hub_permission p ON p.code = rp.permission_code AND p.deleted = 0
             WHERE r.tenant_id = #{tenantId}
               AND r.code = #{roleCode}
               AND r.deleted = 0
               AND r.status = 'ENABLED'
             ORDER BY rp.permission_code
            """)
    List<String> findRolePermissionCodes(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode);
}
