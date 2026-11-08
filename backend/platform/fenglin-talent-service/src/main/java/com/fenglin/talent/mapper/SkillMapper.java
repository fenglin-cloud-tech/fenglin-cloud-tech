package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * C3 技能与活水 Mapper。
 */
@Mapper
public interface SkillMapper {

    // ---------- 技能标签 ----------

    @Insert("""
            INSERT INTO talent_skill_tag
                (id, tenant_id, name, category, parent_id, tag_status, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{category}, #{parentId,jdbcType=BIGINT}, 'ENABLED', #{userId})
            """)
    void insertTag(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                   @Param("category") String category, @Param("parentId") Long parentId,
                   @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", name AS "name", category AS "category", parent_id AS "parentId",
                   tag_status AS "tagStatus"
            FROM talent_skill_tag
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY category, name
            """)
    List<Map<String, Object>> listTags(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", name AS "name" FROM talent_skill_tag
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTag(@Param("tenantId") long tenantId, @Param("id") long id);

    // ---------- 员工技能 ----------

    @Select("""
            SELECT es.skill_id AS "skillId", t.name AS "skillName", es.self_level AS "selfLevel",
                   es.cert_level AS "certLevel", es.years AS "years",
                   to_char(es.last_used_at, 'YYYY-MM-DD') AS "lastUsedAt", es.verified_by AS "verifiedBy"
            FROM talent_employee_skill es
            JOIN talent_skill_tag t ON t.id = es.skill_id AND t.tenant_id = es.tenant_id
            WHERE es.tenant_id = #{tenantId} AND es.employee_id = #{employeeId} AND es.deleted = 0
            ORDER BY es.self_level DESC NULLS LAST
            """)
    List<Map<String, Object>> listEmployeeSkills(@Param("tenantId") long tenantId,
                                                 @Param("employeeId") long employeeId);

    @Select("""
            SELECT id AS "id", version AS "version" FROM talent_employee_skill
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND skill_id = #{skillId}
              AND deleted = 0
            """)
    Map<String, Object> findEmployeeSkill(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                                          @Param("skillId") long skillId);

    @Insert("""
            INSERT INTO talent_employee_skill
                (id, tenant_id, employee_id, skill_id, self_level, years, evidence, last_used_at, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{skillId}, #{selfLevel},
                    #{years,jdbcType=NUMERIC}, #{evidence,jdbcType=VARCHAR}, #{lastUsedAt,jdbcType=DATE}, #{userId})
            """)
    void insertEmployeeSkill(@Param("id") long id, @Param("tenantId") long tenantId,
                             @Param("employeeId") long employeeId, @Param("skillId") long skillId,
                             @Param("selfLevel") int selfLevel, @Param("years") BigDecimal years,
                             @Param("evidence") String evidence, @Param("lastUsedAt") LocalDate lastUsedAt,
                             @Param("userId") long userId);

    @Update("""
            UPDATE talent_employee_skill
            SET self_level = #{selfLevel}, years = #{years,jdbcType=NUMERIC},
                evidence = #{evidence,jdbcType=VARCHAR}, last_used_at = #{lastUsedAt,jdbcType=DATE},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateEmployeeSkill(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("selfLevel") int selfLevel, @Param("years") BigDecimal years,
                             @Param("evidence") String evidence, @Param("lastUsedAt") LocalDate lastUsedAt,
                             @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM talent_employee e
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0 AND e.emp_status <> 'RESIGNED'
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o.id FROM talent_org o
                                   WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
                                     AND o.path LIKE #{pathPrefix} || '%'))
            """)
    long countEmployees(@Param("tenantId") long tenantId, @Param("pathPrefix") String pathPrefix);

    @Select("""
            SELECT count(DISTINCT es.employee_id)
            FROM talent_employee_skill es
            JOIN talent_employee e ON e.id = es.employee_id AND e.tenant_id = es.tenant_id AND e.deleted = 0
            WHERE es.tenant_id = #{tenantId} AND es.deleted = 0 AND e.emp_status <> 'RESIGNED'
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o.id FROM talent_org o
                                   WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
                                     AND o.path LIKE #{pathPrefix} || '%'))
            """)
    long countEmployeesWithSkill(@Param("tenantId") long tenantId, @Param("pathPrefix") String pathPrefix);

    @Select("""
            SELECT es.skill_id AS "skillId", es.self_level AS "level", count(1) AS "count"
            FROM talent_employee_skill es
            JOIN talent_employee e ON e.id = es.employee_id AND e.tenant_id = es.tenant_id AND e.deleted = 0
            WHERE es.tenant_id = #{tenantId} AND es.deleted = 0 AND e.emp_status <> 'RESIGNED'
              AND (#{skillId,jdbcType=BIGINT} IS NULL OR es.skill_id = #{skillId})
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o.id FROM talent_org o
                                   WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
                                     AND o.path LIKE #{pathPrefix} || '%'))
            GROUP BY es.skill_id, es.self_level
            ORDER BY es.skill_id, es.self_level DESC
            """)
    List<Map<String, Object>> skillHeatmap(@Param("tenantId") long tenantId,
                                           @Param("pathPrefix") String pathPrefix,
                                           @Param("skillId") Long skillId);

    // ---------- 人才需求 ----------

    @Insert("""
            INSERT INTO talent_talent_demand
                (id, tenant_id, demand_no, org_id, position_id, skills_required, headcount, expected_date,
                 demand_type, publish_scope, dm_status, owner_id, created_by)
            VALUES (#{id}, #{tenantId}, #{demandNo}, #{orgId}, #{positionId,jdbcType=BIGINT},
                    CAST(#{skillsRequired,jdbcType=VARCHAR} AS jsonb), #{headcount},
                    #{expectedDate,jdbcType=DATE}, #{demandType}, #{publishScope,jdbcType=VARCHAR},
                    'DRAFT', #{ownerId}, #{userId})
            """)
    void insertDemand(@Param("id") long id, @Param("tenantId") long tenantId, @Param("demandNo") String demandNo,
                      @Param("orgId") long orgId, @Param("positionId") Long positionId,
                      @Param("skillsRequired") String skillsRequired, @Param("headcount") int headcount,
                      @Param("expectedDate") LocalDate expectedDate, @Param("demandType") String demandType,
                      @Param("publishScope") String publishScope, @Param("ownerId") long ownerId,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", demand_no AS "demandNo", org_id AS "orgId", position_id AS "positionId",
                   skills_required::text AS "skillsRequired", headcount AS "headcount",
                   to_char(expected_date, 'YYYY-MM-DD') AS "expectedDate", demand_type AS "demandType",
                   publish_scope AS "publishScope", dm_status AS "dmStatus", owner_id AS "ownerId",
                   created_by AS "createdBy"
            FROM talent_talent_demand
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findDemand(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_talent_demand
            SET dm_status = 'PUBLIC', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void publishDemand(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT d.id AS "demandId", d.demand_no AS "demandNo", o.org_name AS "orgName",
                   p.position_name AS "positionName", d.skills_required AS "skills",
                   d.headcount AS "headcount", to_char(d.expected_date, 'YYYY-MM-DD') AS "expectedDate"
            FROM talent_talent_demand d
            JOIN talent_org o ON o.id = d.org_id AND o.tenant_id = d.tenant_id
            LEFT JOIN talent_position p ON p.id = d.position_id AND p.tenant_id = d.tenant_id
            WHERE d.tenant_id = #{tenantId} AND d.deleted = 0 AND d.dm_status = 'PUBLIC'
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR d.org_id = #{orgId})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR d.demand_no ILIKE '%' || #{keyword} || '%'
                   OR p.position_name ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{skills,jdbcType=VARCHAR} AS text) IS NULL
                   OR d.skills_required::text ILIKE '%' || #{skills} || '%')
              AND (d.publish_scope IS NULL OR d.publish_scope = 'ALL'
                   OR d.publish_scope = CAST(#{viewerOrgId,jdbcType=BIGINT} AS text))
            ORDER BY d.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listMarket(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                         @Param("orgId") Long orgId, @Param("skills") String skills,
                                         @Param("viewerOrgId") Long viewerOrgId,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1)
            FROM talent_talent_demand d
            LEFT JOIN talent_position p ON p.id = d.position_id AND p.tenant_id = d.tenant_id
            WHERE d.tenant_id = #{tenantId} AND d.deleted = 0 AND d.dm_status = 'PUBLIC'
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR d.org_id = #{orgId})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR d.demand_no ILIKE '%' || #{keyword} || '%'
                   OR p.position_name ILIKE '%' || #{keyword} || '%')
              AND (CAST(#{skills,jdbcType=VARCHAR} AS text) IS NULL
                   OR d.skills_required::text ILIKE '%' || #{skills} || '%')
              AND (d.publish_scope IS NULL OR d.publish_scope = 'ALL'
                   OR d.publish_scope = CAST(#{viewerOrgId,jdbcType=BIGINT} AS text))
            """)
    long countMarket(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                     @Param("orgId") Long orgId, @Param("skills") String skills,
                     @Param("viewerOrgId") Long viewerOrgId);

    // ---------- 活水申请 ----------

    @Insert("""
            INSERT INTO talent_flow_application
                (id, tenant_id, demand_id, employee_id, apply_type, flow_status, reason, created_by)
            VALUES (#{id}, #{tenantId}, #{demandId}, #{employeeId}, #{applyType}, 'PENDING',
                    #{reason,jdbcType=VARCHAR}, #{userId})
            """)
    void insertFlowApplication(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("demandId") long demandId, @Param("employeeId") long employeeId,
                               @Param("applyType") String applyType, @Param("reason") String reason,
                               @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", demand_id AS "demandId", employee_id AS "employeeId",
                   apply_type AS "applyType", flow_status AS "flowStatus", version AS "version"
            FROM talent_flow_application
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findFlowApplication(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT count(1) FROM talent_flow_application
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0
              AND flow_status IN ('PENDING', 'CUR_MGR_AGREE', 'TARGET_INTERVIEW', 'HR_REVIEW')
            """)
    long countInProgress(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    @Update("""
            UPDATE talent_flow_application
            SET flow_status = #{flowStatus}, cur_manager_opinion = #{curOpinion,jdbcType=VARCHAR},
                target_opinion = #{targetOpinion,jdbcType=VARCHAR}, hr_opinion = #{hrOpinion,jdbcType=VARCHAR},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateFlowApplication(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("flowStatus") String flowStatus, @Param("curOpinion") String curOpinion,
                               @Param("targetOpinion") String targetOpinion, @Param("hrOpinion") String hrOpinion,
                               @Param("userId") long userId);

    @Select("""
            SELECT a.id AS "id", d.demand_no AS "demandNo", e.name AS "employeeName",
                   a.apply_type AS "applyType", a.flow_status AS "flowStatus",
                   extract(epoch FROM a.created_at) * 1000 AS "createdAt"
            FROM talent_flow_application a
            JOIN talent_talent_demand d ON d.id = a.demand_id AND d.tenant_id = a.tenant_id
            JOIN talent_employee e ON e.id = a.employee_id AND e.tenant_id = a.tenant_id
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
              AND (#{employeeId,jdbcType=BIGINT} IS NULL OR a.employee_id = #{employeeId})
              AND (#{demandId,jdbcType=BIGINT} IS NULL OR a.demand_id = #{demandId})
              AND (CAST(#{flowStatus,jdbcType=VARCHAR} AS text) IS NULL OR a.flow_status = #{flowStatus})
            ORDER BY a.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listFlowApplications(@Param("tenantId") long tenantId,
                                                   @Param("employeeId") Long employeeId,
                                                   @Param("demandId") Long demandId,
                                                   @Param("flowStatus") String flowStatus,
                                                   @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_flow_application a
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
              AND (#{employeeId,jdbcType=BIGINT} IS NULL OR a.employee_id = #{employeeId})
              AND (#{demandId,jdbcType=BIGINT} IS NULL OR a.demand_id = #{demandId})
              AND (CAST(#{flowStatus,jdbcType=VARCHAR} AS text) IS NULL OR a.flow_status = #{flowStatus})
            """)
    long countFlowApplications(@Param("tenantId") long tenantId, @Param("employeeId") Long employeeId,
                               @Param("demandId") Long demandId, @Param("flowStatus") String flowStatus);

    // ---------- 人岗匹配 ----------

    @Select("""
            SELECT e.id AS "id", e.name AS "name", e.grade_id AS "gradeId", g.grade_level AS "gradeLevel",
                   coalesce(json_agg(json_build_object('skillId', es.skill_id, 'level', es.self_level))
                            FILTER (WHERE es.id IS NOT NULL), '[]')::text AS "skills"
            FROM talent_employee e
            LEFT JOIN talent_employee_skill es ON es.employee_id = e.id AND es.tenant_id = e.tenant_id
                 AND es.deleted = 0
            LEFT JOIN talent_grade g ON g.id = e.grade_id AND g.tenant_id = e.tenant_id
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0 AND e.emp_status <> 'RESIGNED'
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o.id FROM talent_org o
                                   WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
                                     AND o.path LIKE #{pathPrefix} || '%'))
            GROUP BY e.id, e.name, e.grade_id, g.grade_level
            """)
    List<Map<String, Object>> listEmployeesWithSkills(@Param("tenantId") long tenantId,
                                                      @Param("pathPrefix") String pathPrefix);
}
