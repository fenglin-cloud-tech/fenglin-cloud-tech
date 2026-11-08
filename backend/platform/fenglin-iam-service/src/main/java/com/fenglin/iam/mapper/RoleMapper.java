package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper {

    @Select("""
            <script>
            SELECT id, code, name, role_type, data_scope, status
              FROM hub_role
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="roleType != null and roleType != ''">AND role_type = #{roleType}</if>
             <if test="keyword != null and keyword != ''">
               AND (code LIKE '%' || #{keyword} || '%' OR name LIKE '%' || #{keyword} || '%')
             </if>
             ORDER BY id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> findPage(@Param("tenantId") long tenantId,
                                       @Param("roleType") String roleType,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM hub_role
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="roleType != null and roleType != ''">AND role_type = #{roleType}</if>
             <if test="keyword != null and keyword != ''">
               AND (code LIKE '%' || #{keyword} || '%' OR name LIKE '%' || #{keyword} || '%')
             </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("roleType") String roleType,
               @Param("keyword") String keyword);

    @Select("""
            SELECT id, code, name, role_type, data_scope, status
              FROM hub_role
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, code, name, role_type, data_scope, status
              FROM hub_role
             WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0
            """)
    Map<String, Object> findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO hub_role (id, tenant_id, code, name, role_type, data_scope, parent_id, builtin,
                                  status, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{roleType}, #{dataScope},
                    #{parentId,jdbcType=BIGINT}, 0, 'ENABLED', now(), now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("roleType") String roleType,
               @Param("dataScope") String dataScope,
               @Param("parentId") Long parentId);

    @Insert("""
            INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{roleId}, #{permissionCode}, now(), now(), 0)
            ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING
            """)
    int grantPermission(@Param("id") long id,
                        @Param("tenantId") long tenantId,
                        @Param("roleId") long roleId,
                        @Param("permissionCode") String permissionCode);

    @Delete("""
            DELETE FROM hub_role_permission
             WHERE tenant_id = #{tenantId} AND role_id = #{roleId}
            """)
    int clearPermissions(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Select("""
            SELECT permission_code FROM hub_role_permission
             WHERE tenant_id = #{tenantId} AND role_id = #{roleId} AND deleted = 0
             ORDER BY permission_code
            """)
    List<String> listPermissionCodes(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Insert("""
            INSERT INTO hub_user_role (id, tenant_id, user_id, role_id, granted_at, expire_at,
                                       created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, #{userId}, #{roleId}, now(), #{expireAt,jdbcType=TIMESTAMP},
                    now(), now(), 0)
            ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING
            """)
    int grantUserRole(@Param("id") long id,
                      @Param("tenantId") long tenantId,
                      @Param("userId") long userId,
                      @Param("roleId") long roleId,
                      @Param("expireAt") Instant expireAt);

    @Delete("""
            DELETE FROM hub_user_role
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    int clearUserRoles(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT r.code
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE ur.tenant_id = #{tenantId} AND ur.user_id = #{userId}
               AND ur.deleted = 0
               AND (ur.expire_at IS NULL OR ur.expire_at > now())
             ORDER BY r.code
            """)
    List<String> listRoleCodes(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT ur.user_id, r.code
              FROM hub_user_role ur
              JOIN hub_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE ur.tenant_id = #{tenantId}
               AND ur.user_id IN
               <foreach collection="userIds" item="userId" open="(" separator="," close=")">#{userId}</foreach>
               AND ur.deleted = 0
               AND (ur.expire_at IS NULL OR ur.expire_at > now())
             ORDER BY ur.user_id, r.code
            </script>
            """)
    List<Map<String, Object>> listRoleCodesByUsers(@Param("tenantId") long tenantId,
                                                   @Param("userIds") Collection<Long> userIds);
}
