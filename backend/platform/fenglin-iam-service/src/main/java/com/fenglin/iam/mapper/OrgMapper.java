package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 组织与组织成员关系（hub_org / hub_user_org），所有查询均带 tenant_id。
 */
@Mapper
public interface OrgMapper {

    @Select("""
            SELECT o.id, o.tenant_id, o.parent_id, o.name, o.org_type, o.leader_id, o.sort,
                   o.status, o.path_code,
                   (SELECT count(*) FROM hub_user_org uo
                     WHERE uo.tenant_id = o.tenant_id AND uo.org_id = o.id AND uo.deleted = 0) AS member_count
              FROM hub_org o
             WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
             ORDER BY o.sort, o.id
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId);

    @Select("""
            SELECT o.id, o.tenant_id, o.parent_id, o.name, o.org_type, o.leader_id, o.sort,
                   o.status, o.path_code,
                   (SELECT count(*) FROM hub_user_org uo
                     WHERE uo.tenant_id = o.tenant_id AND uo.org_id = o.id AND uo.deleted = 0) AS member_count
              FROM hub_org o
             WHERE o.tenant_id = #{tenantId} AND o.id = #{id} AND o.deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT count(*) FROM hub_org
             WHERE tenant_id = #{tenantId} AND parent_id = #{id} AND deleted = 0
            """)
    long countChildren(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT count(*) FROM hub_user_org
             WHERE tenant_id = #{tenantId} AND org_id = #{orgId} AND deleted = 0
            """)
    long countMembers(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    @Insert("""
            INSERT INTO hub_org (id, tenant_id, parent_id, path_code, name, org_type, leader_id, sort,
                                 status, created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{parentId,jdbcType=BIGINT}, #{pathCode}, #{name}, #{orgType},
                    #{leaderId,jdbcType=BIGINT}, #{sort}, 'ACTIVE',
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("parentId") Long parentId,
               @Param("pathCode") String pathCode,
               @Param("name") String name,
               @Param("orgType") String orgType,
               @Param("leaderId") Long leaderId,
               @Param("sort") int sort,
               @Param("operatorId") Long operatorId);

    @Update("""
            <script>
            UPDATE hub_org
            <set>
              <if test="name != null">name = #{name},</if>
              <if test="sort != null">sort = #{sort},</if>
              <if test="leaderId != null">leader_id = #{leaderId},</if>
              <if test="status != null">status = #{status},</if>
              updated_by = #{operatorId,jdbcType=BIGINT},
              updated_at = now(),
              version = version + 1
            </set>
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
             <if test="version != null">AND version = #{version}</if>
            </script>
            """)
    int update(@Param("tenantId") long tenantId,
               @Param("id") long id,
               @Param("name") String name,
               @Param("sort") Integer sort,
               @Param("leaderId") Long leaderId,
               @Param("status") String status,
               @Param("version") Integer version,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_org
               SET parent_id = #{parentId,jdbcType=BIGINT}, path_code = #{pathCode},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePath(@Param("tenantId") long tenantId,
                   @Param("id") long id,
                   @Param("parentId") Long parentId,
                   @Param("pathCode") String pathCode,
                   @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_org
               SET path_code = #{newPrefix} || substr(path_code, length(#{oldPrefix}) + 1),
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id <> #{id} AND path_code LIKE #{oldPrefix} || '%'
            """)
    int replaceDescendantPathPrefix(@Param("tenantId") long tenantId,
                                    @Param("id") long id,
                                    @Param("oldPrefix") String oldPrefix,
                                    @Param("newPrefix") String newPrefix,
                                    @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_org
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT},
                   updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int softDelete(@Param("tenantId") long tenantId,
                   @Param("id") long id,
                   @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, tenant_id, user_id, org_id, is_primary, deleted
              FROM hub_user_org
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND org_id = #{orgId}
            """)
    Map<String, Object> findMember(@Param("tenantId") long tenantId,
                                   @Param("userId") long userId,
                                   @Param("orgId") long orgId);

    @Insert("""
            INSERT INTO hub_user_org (id, tenant_id, user_id, org_id, is_primary,
                                      created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{userId}, #{orgId}, #{isPrimary},
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, user_id, org_id)
            DO UPDATE SET is_primary = EXCLUDED.is_primary,
                          deleted = 0,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_user_org.version + 1
            """)
    int upsertMember(@Param("id") long id,
                     @Param("tenantId") long tenantId,
                     @Param("userId") long userId,
                     @Param("orgId") long orgId,
                     @Param("isPrimary") int isPrimary,
                     @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_user_org
               SET is_primary = 0, updated_by = #{operatorId,jdbcType=BIGINT},
                   updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND deleted = 0 AND is_primary = 1 AND org_id <> #{orgId}
            """)
    int clearOtherPrimary(@Param("tenantId") long tenantId,
                          @Param("userId") long userId,
                          @Param("orgId") long orgId,
                          @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_user_org
               SET deleted = 1, is_primary = 0, updated_by = #{operatorId,jdbcType=BIGINT},
                   updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND org_id = #{orgId} AND deleted = 0
            """)
    int softDeleteMember(@Param("tenantId") long tenantId,
                         @Param("userId") long userId,
                         @Param("orgId") long orgId,
                         @Param("operatorId") Long operatorId);
}
