package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, tenant_id, employee_no, username, real_name, primary_org_id, primary_position_id,
                   status, created_at
              FROM hub_user
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, tenant_id, employee_no, username, real_name, primary_org_id, primary_position_id,
                   status, created_at
              FROM hub_user
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="orgId != null">AND primary_org_id = #{orgId}</if>
             <if test="keyword != null and keyword != ''">
               AND (username LIKE '%' || #{keyword} || '%'
                    OR real_name LIKE '%' || #{keyword} || '%'
                    OR employee_no LIKE '%' || #{keyword} || '%')
             </if>
             ORDER BY id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> findPage(@Param("tenantId") long tenantId,
                                       @Param("orgId") Long orgId,
                                       @Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="status != null and status != ''">AND status = #{status}</if>
             <if test="orgId != null">AND primary_org_id = #{orgId}</if>
             <if test="keyword != null and keyword != ''">
               AND (username LIKE '%' || #{keyword} || '%'
                    OR real_name LIKE '%' || #{keyword} || '%'
                    OR employee_no LIKE '%' || #{keyword} || '%')
             </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("orgId") Long orgId,
               @Param("status") String status,
               @Param("keyword") String keyword);

    @Select("""
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND username = #{username} AND deleted = 0
            """)
    long countByUsername(@Param("tenantId") long tenantId, @Param("username") String username);

    @Select("""
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND employee_no = #{employeeNo} AND deleted = 0
            """)
    long countByEmployeeNo(@Param("tenantId") long tenantId, @Param("employeeNo") String employeeNo);

    @Insert("""
            INSERT INTO hub_user (id, tenant_id, uuid, employee_no, username, real_name, user_type,
                                  primary_org_id, primary_position_id, status, mfa_enabled,
                                  created_at, updated_at, deleted)
            VALUES (#{id}, #{tenantId}, gen_random_uuid(), #{employeeNo}, #{username}, #{realName},
                    'INTERNAL', #{primaryOrgId}, #{primaryPositionId}, 'ACTIVE', 0, now(), now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("employeeNo") String employeeNo,
               @Param("username") String username,
               @Param("realName") String realName,
               @Param("primaryOrgId") Long primaryOrgId,
               @Param("primaryPositionId") Long primaryPositionId);
}
