package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT u.id, u.tenant_id, u.employee_no, u.username, u.nickname, u.real_name,
                   u.mobile_enc, u.primary_org_id, u.primary_position_id, u.status, u.created_at,
                   (SELECT o.name FROM hub_org o
                     WHERE o.tenant_id = u.tenant_id AND o.id = u.primary_org_id AND o.deleted = 0)
                       AS primary_org_name
              FROM hub_user u
             WHERE u.tenant_id = #{tenantId} AND u.id = #{id} AND u.deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT u.id, u.tenant_id, u.employee_no, u.username, u.nickname, u.real_name,
                   u.mobile_enc, u.primary_org_id, u.primary_position_id, u.status, u.created_at,
                   (SELECT o.name FROM hub_org o
                     WHERE o.tenant_id = u.tenant_id AND o.id = u.primary_org_id AND o.deleted = 0)
                       AS primary_org_name
              FROM hub_user u
             WHERE u.tenant_id = #{tenantId} AND u.deleted = 0
             <if test="status != null and status != ''">AND u.status = #{status}</if>
             <if test="orgId != null">AND u.primary_org_id = #{orgId}</if>
             <if test="keyword != null and keyword != ''">
               AND (u.username LIKE '%' || #{keyword} || '%'
                    OR u.real_name LIKE '%' || #{keyword} || '%'
                    OR u.employee_no LIKE '%' || #{keyword} || '%')
             </if>
             ORDER BY u.id DESC
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

    @Select("""
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND employee_no = #{employeeNo} AND id <> #{userId}
            """)
    long countByEmployeeNoExcluding(@Param("tenantId") long tenantId,
                                    @Param("employeeNo") String employeeNo,
                                    @Param("userId") long userId);

    @Select("""
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND mobile_hash = #{mobileHash}
               AND deleted = 0 AND id <> #{userId}
            """)
    long countByMobileHashExcluding(@Param("tenantId") long tenantId,
                                    @Param("mobileHash") String mobileHash,
                                    @Param("userId") long userId);

    @Select("""
            SELECT count(*) FROM hub_user
             WHERE tenant_id = #{tenantId} AND email_hash = #{emailHash}
               AND deleted = 0 AND id <> #{userId}
            """)
    long countByEmailHashExcluding(@Param("tenantId") long tenantId,
                                   @Param("emailHash") String emailHash,
                                   @Param("userId") long userId);

    @Update("""
            <script>
            UPDATE hub_user
            <set>
              <if test="nicknameSet">nickname = #{nickname,jdbcType=VARCHAR},</if>
              <if test="employeeNoSet">employee_no = #{employeeNo,jdbcType=VARCHAR},</if>
              <if test="mobileSet">mobile_enc = #{mobileEnc,jdbcType=VARCHAR},
                                   mobile_hash = #{mobileHash,jdbcType=VARCHAR},</if>
              <if test="emailSet">email_enc = #{emailEnc,jdbcType=VARCHAR},
                                 email_hash = #{emailHash,jdbcType=VARCHAR},</if>
              <if test="primaryOrgIdSet">primary_org_id = #{primaryOrgId,jdbcType=BIGINT},</if>
              <if test="status != null">status = #{status},</if>
              updated_by = #{operatorId,jdbcType=BIGINT},
              updated_at = now(),
              version = version + 1
            </set>
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
             <if test="version != null">AND version = #{version}</if>
            </script>
            """)
    int updateProfile(@Param("tenantId") long tenantId,
                      @Param("id") long id,
                      @Param("nicknameSet") boolean nicknameSet,
                      @Param("nickname") String nickname,
                      @Param("employeeNoSet") boolean employeeNoSet,
                      @Param("employeeNo") String employeeNo,
                      @Param("mobileSet") boolean mobileSet,
                      @Param("mobileEnc") String mobileEnc,
                      @Param("mobileHash") String mobileHash,
                      @Param("emailSet") boolean emailSet,
                      @Param("emailEnc") String emailEnc,
                      @Param("emailHash") String emailHash,
                      @Param("primaryOrgIdSet") boolean primaryOrgIdSet,
                      @Param("primaryOrgId") Long primaryOrgId,
                      @Param("status") String status,
                      @Param("version") Integer version,
                      @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_user
               SET primary_org_id = #{primaryOrgId,jdbcType=BIGINT},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePrimaryOrg(@Param("tenantId") long tenantId,
                         @Param("id") long id,
                         @Param("primaryOrgId") Long primaryOrgId,
                         @Param("operatorId") Long operatorId);

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
