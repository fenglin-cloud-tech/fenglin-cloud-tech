package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 组织架构 Mapper：materialized path + parent_id 双写，移动时按 path 前缀批量重算。
 */
@Mapper
public interface OrgMapper {

    @Insert("""
            INSERT INTO talent_org
                (id, tenant_id, org_code, org_name, parent_id, path, level, org_type, leader_id, sort_no,
                 status, created_by)
            VALUES (#{id}, #{tenantId}, #{orgCode}, #{orgName}, #{parentId,jdbcType=BIGINT}, #{path}, #{level},
                    #{orgType}, #{leaderId,jdbcType=BIGINT}, #{sortNo}, #{status}, #{userId})
            """)
    void insertOrg(@Param("id") long id, @Param("tenantId") long tenantId, @Param("orgCode") String orgCode,
                   @Param("orgName") String orgName, @Param("parentId") Long parentId, @Param("path") String path,
                   @Param("level") int level, @Param("orgType") String orgType, @Param("leaderId") Long leaderId,
                   @Param("sortNo") int sortNo, @Param("status") String status, @Param("userId") long userId);

    @Select("""
            SELECT o.id AS "id", o.org_code AS "orgCode", o.org_name AS "orgName", o.parent_id AS "parentId",
                   o.path AS "path", o.level AS "level", o.org_type AS "orgType", o.leader_id AS "leaderId",
                   o.sort_no AS "sortNo", o.status AS "status", o.version AS "version",
                   l.name AS "leaderName"
            FROM talent_org o
            LEFT JOIN talent_employee l ON l.id = o.leader_id AND l.tenant_id = o.tenant_id AND l.deleted = 0
            WHERE o.tenant_id = #{tenantId} AND o.id = #{id} AND o.deleted = 0
            """)
    Map<String, Object> findOrg(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", org_code AS "orgCode", path AS "path"
            FROM talent_org
            WHERE tenant_id = #{tenantId} AND org_code = #{orgCode} AND deleted = 0
            """)
    Map<String, Object> findOrgByCode(@Param("tenantId") long tenantId, @Param("orgCode") String orgCode);

    @Select("""
            SELECT o.id AS "id", o.org_code AS "orgCode", o.org_name AS "orgName", o.parent_id AS "parentId",
                   o.path AS "path", o.level AS "level", o.org_type AS "orgType", o.leader_id AS "leaderId",
                   o.sort_no AS "sortNo", o.status AS "status"
            FROM talent_org o
            WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
              AND (CAST(#{includeDisabled} AS boolean) = true OR o.status = 'ENABLED')
              AND (CAST(#{rootPath,jdbcType=VARCHAR} AS text) IS NULL OR o.path LIKE #{rootPath} || '%')
            ORDER BY o.level, o.sort_no, o.id
            """)
    List<Map<String, Object>> listOrgs(@Param("tenantId") long tenantId,
                                       @Param("rootPath") String rootPath,
                                       @Param("includeDisabled") boolean includeDisabled);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND org_id = #{orgId} AND emp_status <> 'RESIGNED' AND deleted = 0
            """)
    long countActiveEmployees(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    @Select("""
            SELECT count(1) FROM talent_org
            WHERE tenant_id = #{tenantId} AND parent_id = #{orgId} AND deleted = 0
            """)
    long countChildren(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    @Select("""
            SELECT count(1) FROM talent_recruit_requisition
            WHERE tenant_id = #{tenantId} AND org_id = #{orgId} AND deleted = 0
              AND req_status IN ('DRAFT', 'APPROVING', 'RECRUITING', 'PAUSED')
            """)
    long countOpenRequisitions(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    @Update("""
            UPDATE talent_org
            SET org_name = #{orgName}, leader_id = #{leaderId,jdbcType=BIGINT}, sort_no = #{sortNo},
                status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} AND deleted = 0
            """)
    int updateOrg(@Param("tenantId") long tenantId, @Param("id") long id, @Param("orgName") String orgName,
                  @Param("leaderId") Long leaderId, @Param("sortNo") int sortNo, @Param("status") String status,
                  @Param("version") int version, @Param("userId") long userId);

    /**
     * 移动后批量重算子树 path 与 level：newPath + 原 path 去掉 oldPath 前缀的部分。
     */
    @Update("""
            UPDATE talent_org
            SET path = #{newPath} || substring(path FROM length(#{oldPath}) + 1),
                level = level + #{levelDelta}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND path LIKE #{oldPath} || '%' AND deleted = 0
            """)
    void updateSubtreePath(@Param("tenantId") long tenantId, @Param("oldPath") String oldPath,
                           @Param("newPath") String newPath, @Param("levelDelta") int levelDelta,
                           @Param("userId") long userId);

    @Update("""
            UPDATE talent_org
            SET parent_id = #{parentId,jdbcType=BIGINT}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateParent(@Param("tenantId") long tenantId, @Param("id") long id, @Param("parentId") Long parentId,
                      @Param("userId") long userId);

    @Update("""
            UPDATE talent_org
            SET status = 'DISABLED', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void disableOrg(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);
}
