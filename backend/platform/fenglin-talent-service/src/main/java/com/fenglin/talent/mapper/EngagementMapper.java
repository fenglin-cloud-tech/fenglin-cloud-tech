package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * C4 敬业度与离职预测 Mapper。
 */
@Mapper
public interface EngagementMapper {

    // ---------- 问卷 ----------

    @Insert("""
            INSERT INTO talent_engagement_survey
                (id, tenant_id, survey_no, title, survey_type, start_at, end_at, anonymous, questions,
                 dimensions, target_scope, survey_status, response_count, created_by)
            VALUES (#{id}, #{tenantId}, #{surveyNo}, #{title}, #{surveyType}, #{startAt}, #{endAt},
                    #{anonymous}, CAST(#{questions,jdbcType=VARCHAR} AS jsonb),
                    CAST(#{dimensions,jdbcType=VARCHAR} AS jsonb),
                    CAST(#{targetScope,jdbcType=VARCHAR} AS jsonb), 'DRAFT', 0, #{userId})
            """)
    void insertSurvey(@Param("id") long id, @Param("tenantId") long tenantId, @Param("surveyNo") String surveyNo,
                      @Param("title") String title, @Param("surveyType") String surveyType,
                      @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                      @Param("anonymous") boolean anonymous, @Param("questions") String questions,
                      @Param("dimensions") String dimensions, @Param("targetScope") String targetScope,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", survey_no AS "surveyNo", title AS "title", survey_type AS "surveyType",
                   anonymous AS "anonymous", questions::text AS "questions", dimensions::text AS "dimensions",
                   survey_status AS "surveyStatus", response_count AS "responseCount", version AS "version",
                   extract(epoch FROM start_at) * 1000 AS "startAt",
                   extract(epoch FROM end_at) * 1000 AS "endAt"
            FROM talent_engagement_survey
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSurvey(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", survey_no AS "surveyNo", title AS "title", survey_type AS "surveyType",
                   survey_status AS "surveyStatus", response_count AS "responseCount",
                   extract(epoch FROM start_at) * 1000 AS "startAt",
                   extract(epoch FROM end_at) * 1000 AS "endAt"
            FROM talent_engagement_survey
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listSurveys(@Param("tenantId") long tenantId, @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Select("SELECT count(1) FROM talent_engagement_survey WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countSurveys(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE talent_engagement_survey
            SET survey_status = 'PUBLISHED', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void publishSurvey(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND emp_status <> 'RESIGNED'
            """)
    long countTargetEmployees(@Param("tenantId") long tenantId);

    // ---------- 答卷 ----------

    @Insert("""
            INSERT INTO talent_survey_response
                (id, tenant_id, survey_id, employee_hash, dept_id, answers, score_total, dim_scores,
                 submitted_at, duration_sec, created_by)
            VALUES (#{id}, #{tenantId}, #{surveyId}, #{employeeHash,jdbcType=CHAR}, #{deptId,jdbcType=BIGINT},
                    CAST(#{answers,jdbcType=VARCHAR} AS jsonb), #{scoreTotal},
                    CAST(#{dimScores,jdbcType=VARCHAR} AS jsonb), #{submittedAt}, #{durationSec}, #{userId})
            """)
    void insertResponse(@Param("id") long id, @Param("tenantId") long tenantId, @Param("surveyId") long surveyId,
                        @Param("employeeHash") String employeeHash, @Param("deptId") Long deptId,
                        @Param("answers") String answers, @Param("scoreTotal") BigDecimal scoreTotal,
                        @Param("dimScores") String dimScores, @Param("submittedAt") OffsetDateTime submittedAt,
                        @Param("durationSec") Integer durationSec, @Param("userId") long userId);

    @Update("""
            UPDATE talent_engagement_survey
            SET response_count = response_count + 1, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void incrementResponseCount(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM talent_survey_response
            WHERE tenant_id = #{tenantId} AND survey_id = #{surveyId} AND deleted = 0
              AND employee_hash = #{employeeHash}
            """)
    long countResponseByHash(@Param("tenantId") long tenantId, @Param("surveyId") long surveyId,
                             @Param("employeeHash") String employeeHash);

    @Select("""
            SELECT id AS "id", dept_id AS "deptId", score_total AS "scoreTotal",
                   dim_scores::text AS "dimScores"
            FROM talent_survey_response
            WHERE tenant_id = #{tenantId} AND survey_id = #{surveyId} AND deleted = 0
            """)
    List<Map<String, Object>> listResponses(@Param("tenantId") long tenantId, @Param("surveyId") long surveyId);

    @Select("""
            SELECT count(1) FROM talent_survey_response
            WHERE tenant_id = #{tenantId} AND survey_id = #{surveyId} AND deleted = 0
            """)
    long countResponses(@Param("tenantId") long tenantId, @Param("surveyId") long surveyId);

    @Select("""
            SELECT score_total AS "scoreTotal"
            FROM talent_survey_response
            WHERE tenant_id = #{tenantId} AND employee_hash = #{employeeHash} AND deleted = 0
            ORDER BY submitted_at DESC
            LIMIT 1
            """)
    Map<String, Object> findLatestScoreByHash(@Param("tenantId") long tenantId,
                                              @Param("employeeHash") String employeeHash);

    @Select("""
            SELECT extract(epoch FROM submitted_at) * 1000 AS "submittedAt", score_total AS "scoreTotal"
            FROM talent_survey_response
            WHERE tenant_id = #{tenantId} AND survey_id = #{surveyId} AND deleted = 0
            ORDER BY submitted_at
            """)
    List<Map<String, Object>> listScoreTrend(@Param("tenantId") long tenantId, @Param("surveyId") long surveyId);

    // ---------- 离职风险 ----------

    @Insert("""
            INSERT INTO talent_attrition_risk
                (id, tenant_id, employee_id, risk_score, risk_level, factors, model_version, scored_at, trend,
                 created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{riskScore}, #{riskLevel},
                    CAST(#{factors,jdbcType=VARCHAR} AS jsonb), #{modelVersion}, #{scoredAt}, #{trend}, #{userId})
            """)
    void insertRisk(@Param("id") long id, @Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                    @Param("riskScore") int riskScore, @Param("riskLevel") String riskLevel,
                    @Param("factors") String factors, @Param("modelVersion") String modelVersion,
                    @Param("scoredAt") OffsetDateTime scoredAt, @Param("trend") Integer trend,
                    @Param("userId") long userId);

    @Select("""
            SELECT r.employee_id AS "employeeId", e.name AS "name", o.org_name AS "orgName",
                   r.risk_score AS "riskScore", r.risk_level AS "riskLevel", r.trend AS "trend",
                   r.factors::text AS "factors",
                   extract(epoch FROM r.scored_at) * 1000 AS "scoredAt"
            FROM talent_attrition_risk r
            JOIN talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
            JOIN talent_org o ON o.id = e.org_id AND o.tenant_id = e.tenant_id
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{riskLevel,jdbcType=VARCHAR} AS text) IS NULL OR r.risk_level = #{riskLevel})
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR e.org_id = #{orgId})
            ORDER BY r.risk_score DESC, r.scored_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listRisks(@Param("tenantId") long tenantId, @Param("riskLevel") String riskLevel,
                                        @Param("orgId") Long orgId, @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_attrition_risk r
            JOIN talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{riskLevel,jdbcType=VARCHAR} AS text) IS NULL OR r.risk_level = #{riskLevel})
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR e.org_id = #{orgId})
            """)
    long countRisks(@Param("tenantId") long tenantId, @Param("riskLevel") String riskLevel,
                    @Param("orgId") Long orgId);

    @Select("""
            SELECT risk_score AS "riskScore" FROM talent_attrition_risk
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0
            ORDER BY scored_at DESC
            LIMIT 1
            """)
    Map<String, Object> findLatestRisk(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    @Select("""
            SELECT e.id AS "id", e.name AS "name", e.grade_id AS "gradeId", g.grade_level AS "gradeLevel",
                   g.band_min AS "bandMin", g.band_max AS "bandMax",
                   to_char(e.hire_date, 'YYYY-MM-DD') AS "hireDate"
            FROM talent_employee e
            LEFT JOIN talent_grade g ON g.id = e.grade_id AND g.tenant_id = e.tenant_id
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0 AND e.emp_status <> 'RESIGNED'
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o.id FROM talent_org o
                                   WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
                                     AND o.path LIKE #{pathPrefix} || '%'))
            """)
    List<Map<String, Object>> listEmployeesForScoring(@Param("tenantId") long tenantId,
                                                      @Param("pathPrefix") String pathPrefix);

    @Select("""
            SELECT after_json::text AS "afterJson", to_char(effective_date, 'YYYY-MM-DD') AS "effectiveDate"
            FROM talent_transfer
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0
              AND tf_status = 'EFFECTIVE'
            ORDER BY effective_date DESC
            LIMIT 1
            """)
    Map<String, Object> findLatestTransfer(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    // ---------- 干预 ----------

    @Insert("""
            INSERT INTO talent_intervention
                (id, tenant_id, employee_id, risk_id, measure_type, content, owner_id, iv_status, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{riskId,jdbcType=BIGINT}, #{measureType},
                    #{content,jdbcType=VARCHAR}, #{ownerId}, 'OPEN', #{userId})
            """)
    void insertIntervention(@Param("id") long id, @Param("tenantId") long tenantId,
                            @Param("employeeId") long employeeId, @Param("riskId") Long riskId,
                            @Param("measureType") String measureType, @Param("content") String content,
                            @Param("ownerId") long ownerId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", risk_id AS "riskId", measure_type AS "measureType",
                   content AS "content", owner_id AS "ownerId", follow_up AS "followUp", iv_status AS "ivStatus"
            FROM talent_intervention
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findIntervention(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_intervention
            SET follow_up = #{followUp,jdbcType=VARCHAR}, iv_status = #{ivStatus}, updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateIntervention(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("followUp") String followUp, @Param("ivStatus") String ivStatus,
                            @Param("userId") long userId);
}
