package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 岗位 / 职级 / 编制计划 Mapper。
 */
@Mapper
public interface AdminMapper {

    @Insert("""
            INSERT INTO talent_position
                (id, tenant_id, position_code, position_name, org_id, job_family, job_sequence, headcount,
                 description, status, created_by)
            VALUES (#{id}, #{tenantId}, #{positionCode}, #{positionName}, #{orgId,jdbcType=BIGINT},
                    #{jobFamily,jdbcType=VARCHAR}, #{jobSequence,jdbcType=VARCHAR}, #{headcount},
                    #{description,jdbcType=VARCHAR}, 'ENABLED', #{userId})
            """)
    void insertPosition(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("positionCode") String positionCode, @Param("positionName") String positionName,
                        @Param("orgId") Long orgId, @Param("jobFamily") String jobFamily,
                        @Param("jobSequence") String jobSequence, @Param("headcount") int headcount,
                        @Param("description") String description, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", position_code AS "positionCode", position_name AS "positionName",
                   job_family AS "jobFamily", job_sequence AS "jobSequence", headcount AS "headcount",
                   org_id AS "orgId", status AS "status"
            FROM talent_position
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listPositions(@Param("tenantId") long tenantId,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_position WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long countPositions(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", grade_code AS "gradeCode", grade_name AS "gradeName", sequence AS "sequence",
                   grade_level AS "gradeLevel", band_min AS "bandMin", band_max AS "bandMax"
            FROM talent_grade
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY sequence, grade_level
            """)
    List<Map<String, Object>> listGrades(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", grade_code AS "gradeCode", grade_name AS "gradeName", sequence AS "sequence",
                   grade_level AS "gradeLevel", band_min AS "bandMin", band_max AS "bandMax"
            FROM talent_grade
            WHERE tenant_id = #{tenantId} AND id = #{gradeId} AND deleted = 0
            """)
    Map<String, Object> findGrade(@Param("tenantId") long tenantId, @Param("gradeId") long gradeId);

    @Insert("""
            INSERT INTO talent_headcount_plan
                (id, tenant_id, plan_year, org_id, position_id, budget_headcount, used_headcount, status, created_by)
            VALUES (#{id}, #{tenantId}, #{planYear}, #{orgId}, #{positionId,jdbcType=BIGINT},
                    #{budgetHeadcount}, 0, 'DRAFT', #{userId})
            """)
    void insertHeadcountPlan(@Param("id") long id, @Param("tenantId") long tenantId,
                             @Param("planYear") int planYear, @Param("orgId") long orgId,
                             @Param("positionId") Long positionId, @Param("budgetHeadcount") int budgetHeadcount,
                             @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", plan_year AS "planYear", org_id AS "orgId", position_id AS "positionId",
                   budget_headcount AS "budgetHeadcount", used_headcount AS "usedHeadcount", status AS "status"
            FROM talent_headcount_plan
            WHERE tenant_id = #{tenantId} AND org_id = #{orgId} AND plan_year = #{planYear}
              AND deleted = 0 AND status <> 'CLOSED'
              AND (#{positionId,jdbcType=BIGINT} IS NULL OR position_id = #{positionId} OR position_id IS NULL)
            ORDER BY position_id NULLS LAST
            LIMIT 1
            """)
    Map<String, Object> findHeadcountPlan(@Param("tenantId") long tenantId, @Param("orgId") long orgId,
                                          @Param("positionId") Long positionId, @Param("planYear") int planYear);
}
