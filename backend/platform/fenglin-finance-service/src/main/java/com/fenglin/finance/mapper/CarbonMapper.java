package com.fenglin.finance.mapper;

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
 * D4 排放因子、碳活动、核算结果、双碳目标、ESG 指标与报告 Mapper。
 */
@Mapper
public interface CarbonMapper {

    @Insert("""
            INSERT INTO fin_emission_factor
                (id, tenant_id, factor_code, factor_name, scope, activity_type, activity_unit, factor_value,
                 factor_unit, factor_version, source, effective_from, effective_to, status, created_by)
            VALUES (#{id}, #{tenantId}, #{factorCode}, #{factorName}, #{scope}, #{activityType}, #{activityUnit},
                    #{factorValue}, #{factorUnit}, #{factorVersion}, #{source,jdbcType=VARCHAR}, #{effectiveFrom},
                    #{effectiveTo,jdbcType=DATE}, 'ENABLED', #{userId})
            """)
    void insertFactor(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("factorCode") String factorCode, @Param("factorName") String factorName,
                      @Param("scope") String scope, @Param("activityType") String activityType,
                      @Param("activityUnit") String activityUnit, @Param("factorValue") BigDecimal factorValue,
                      @Param("factorUnit") String factorUnit, @Param("factorVersion") String factorVersion,
                      @Param("source") String source, @Param("effectiveFrom") LocalDate effectiveFrom,
                      @Param("effectiveTo") LocalDate effectiveTo, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", factor_code AS "factorCode", factor_name AS "factorName", scope AS "scope",
                   activity_type AS "activityType", activity_unit AS "activityUnit",
                   factor_value AS "factorValue", factor_unit AS "factorUnit",
                   factor_version AS "factorVersion", source AS "source",
                   effective_from::text AS "effectiveFrom", effective_to::text AS "effectiveTo",
                   status AS "status"
            FROM fin_emission_factor
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findFactor(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", factor_code AS "factorCode", factor_name AS "factorName", scope AS "scope",
                   activity_type AS "activityType", activity_unit AS "activityUnit",
                   factor_value AS "factorValue", factor_unit AS "factorUnit",
                   factor_version AS "factorVersion", source AS "source",
                   effective_from::text AS "effectiveFrom", effective_to::text AS "effectiveTo",
                   status AS "status"
            FROM fin_emission_factor
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{scope,jdbcType=VARCHAR} AS text) IS NULL OR scope = #{scope})
              AND (CAST(#{activityType,jdbcType=VARCHAR} AS text) IS NULL OR activity_type = #{activityType})
              AND status = 'ENABLED'
            ORDER BY scope, activity_type, factor_version DESC
            """)
    List<Map<String, Object>> listFactors(@Param("tenantId") long tenantId,
                                          @Param("scope") String scope,
                                          @Param("activityType") String activityType);

    @Select("""
            SELECT id AS "id", factor_value AS "factorValue", factor_version AS "factorVersion",
                   factor_unit AS "factorUnit", activity_unit AS "activityUnit", scope AS "scope",
                   activity_type AS "activityType"
            FROM fin_emission_factor
            WHERE tenant_id = #{tenantId} AND scope = #{scope} AND activity_type = #{activityType}
              AND activity_unit = #{activityUnit} AND status = 'ENABLED' AND deleted = 0
              AND effective_from <= #{activityDate}
              AND (effective_to IS NULL OR effective_to >= #{activityDate})
            ORDER BY effective_from DESC, factor_version DESC
            LIMIT 1
            """)
    Map<String, Object> findApplicableFactor(@Param("tenantId") long tenantId,
                                             @Param("scope") String scope,
                                             @Param("activityType") String activityType,
                                             @Param("activityUnit") String activityUnit,
                                             @Param("activityDate") LocalDate activityDate);

    @Insert("""
            INSERT INTO fin_carbon_activity
                (id, tenant_id, activity_no, org_id, dept_id, activity_type, scope, activity_date, period_code,
                 amount, unit, factor_id, data_source, evidence_file_id, status, created_by)
            VALUES (#{id}, #{tenantId}, #{activityNo}, #{orgId,jdbcType=BIGINT}, #{deptId,jdbcType=BIGINT},
                    #{activityType}, #{scope}, #{activityDate}, #{periodCode}, #{amount}, #{unit},
                    #{factorId,jdbcType=BIGINT}, #{dataSource}, #{evidenceFileId,jdbcType=BIGINT},
                    'SUBMITTED', #{userId})
            """)
    void insertActivity(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("activityNo") String activityNo, @Param("orgId") Long orgId,
                        @Param("deptId") Long deptId, @Param("activityType") String activityType,
                        @Param("scope") String scope, @Param("activityDate") LocalDate activityDate,
                        @Param("periodCode") String periodCode, @Param("amount") BigDecimal amount,
                        @Param("unit") String unit, @Param("factorId") Long factorId,
                        @Param("dataSource") String dataSource, @Param("evidenceFileId") Long evidenceFileId,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", activity_no AS "activityNo", org_id AS "orgId", dept_id AS "deptId",
                   activity_type AS "activityType", scope AS "scope", activity_date::text AS "activityDate",
                   period_code AS "periodCode", amount AS "amount", unit AS "unit", factor_id AS "factorId",
                   data_source AS "dataSource", evidence_file_id AS "evidenceFileId", status AS "status",
                   verified_by AS "verifiedBy", verified_at AS "verifiedAt", locked_by AS "lockedBy",
                   locked_at AS "lockedAt"
            FROM fin_carbon_activity
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findActivity(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", activity_no AS "activityNo", org_id AS "orgId", dept_id AS "deptId",
                   activity_type AS "activityType", scope AS "scope", activity_date::text AS "activityDate",
                   period_code AS "periodCode", amount AS "amount", unit AS "unit", factor_id AS "factorId",
                   data_source AS "dataSource", status AS "status"
            FROM fin_carbon_activity
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodCode,jdbcType=VARCHAR} AS text) IS NULL OR period_code = #{periodCode})
              AND (CAST(#{scope,jdbcType=VARCHAR} AS text) IS NULL OR scope = #{scope})
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY activity_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listActivities(@Param("tenantId") long tenantId,
                                             @Param("periodCode") String periodCode,
                                             @Param("scope") String scope,
                                             @Param("status") String status,
                                             @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 员工自助：只返回本人填报的碳活动数据，tenant_id + created_by 双重收敛。
     */
    @Select("""
            SELECT id AS "id", activity_no AS "activityNo", org_id AS "orgId", dept_id AS "deptId",
                   activity_type AS "activityType", scope AS "scope", activity_date::text AS "activityDate",
                   period_code AS "periodCode", amount AS "amount", unit AS "unit", factor_id AS "factorId",
                   data_source AS "dataSource", status AS "status"
            FROM fin_carbon_activity
            WHERE tenant_id = #{tenantId} AND created_by = #{userId} AND deleted = 0
              AND (CAST(#{periodCode,jdbcType=VARCHAR} AS text) IS NULL OR period_code = #{periodCode})
              AND (CAST(#{scope,jdbcType=VARCHAR} AS text) IS NULL OR scope = #{scope})
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY activity_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listActivitiesByCreator(@Param("tenantId") long tenantId,
                                                      @Param("userId") long userId,
                                                      @Param("periodCode") String periodCode,
                                                      @Param("scope") String scope,
                                                      @Param("status") String status,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset);

    @Update("""
            UPDATE fin_carbon_activity
            SET status = 'VERIFIED', verified_by = #{userId}, verified_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int verifyActivity(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("userId") long userId);

    @Update("""
            UPDATE fin_carbon_activity
            SET status = 'LOCKED', locked_by = #{userId}, locked_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'VERIFIED' AND deleted = 0
            """)
    int lockActivity(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", activity_no AS "activityNo", activity_type AS "activityType",
                   scope AS "scope", activity_date::text AS "activityDate", period_code AS "periodCode",
                   amount AS "amount", unit AS "unit", factor_id AS "factorId"
            FROM fin_carbon_activity a
            WHERE a.tenant_id = #{tenantId} AND a.period_code = #{periodCode}
              AND a.status IN ('VERIFIED', 'LOCKED') AND a.deleted = 0
              AND (CAST(#{scope,jdbcType=VARCHAR} AS text) IS NULL OR a.scope = #{scope})
              AND (CAST(#{orgId,jdbcType=BIGINT} AS bigint) IS NULL OR a.org_id = #{orgId})
              AND (CAST(#{recalc} AS boolean) = true OR NOT EXISTS (
                    SELECT 1 FROM fin_carbon_emission e
                    WHERE e.tenant_id = a.tenant_id AND e.activity_id = a.id
                      AND e.is_latest = true AND e.deleted = 0))
            ORDER BY a.id
            """)
    List<Map<String, Object>> listCalculableActivities(@Param("tenantId") long tenantId,
                                                       @Param("periodCode") String periodCode,
                                                       @Param("scope") String scope,
                                                       @Param("orgId") Long orgId,
                                                       @Param("recalc") boolean recalc);

    @Insert("""
            INSERT INTO fin_carbon_emission
                (id, tenant_id, emission_no, batch_no, activity_id, factor_id, factor_version, amount,
                 factor_value, emission_amount, unit, period_code, scope, is_latest, calculated_at,
                 created_by)
            VALUES (#{id}, #{tenantId}, #{emissionNo}, #{batchNo}, #{activityId}, #{factorId}, #{factorVersion},
                    #{amount}, #{factorValue}, #{emissionAmount}, 'tCO2e', #{periodCode}, #{scope},
                    #{isLatest}, now(), #{userId})
            """)
    void insertEmission(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("emissionNo") String emissionNo, @Param("batchNo") String batchNo,
                        @Param("activityId") long activityId, @Param("factorId") long factorId,
                        @Param("factorVersion") String factorVersion, @Param("amount") BigDecimal amount,
                        @Param("factorValue") BigDecimal factorValue,
                        @Param("emissionAmount") BigDecimal emissionAmount, @Param("periodCode") String periodCode,
                        @Param("scope") String scope, @Param("isLatest") boolean isLatest,
                        @Param("userId") long userId);

    @Update("""
            UPDATE fin_carbon_emission SET is_latest = false, version = version + 1
            WHERE tenant_id = #{tenantId} AND activity_id = #{activityId} AND is_latest = true AND deleted = 0
            """)
    int markNotLatest(@Param("tenantId") long tenantId, @Param("activityId") long activityId);

    @Select("""
            SELECT id AS "id", emission_no AS "emissionNo", batch_no AS "batchNo", activity_id AS "activityId",
                   factor_id AS "factorId", factor_version AS "factorVersion", amount AS "amount",
                   factor_value AS "factorValue", emission_amount AS "emissionAmount", unit AS "unit",
                   period_code AS "periodCode", scope AS "scope", is_latest AS "isLatest",
                   calculated_at AS "calculatedAt"
            FROM fin_carbon_emission
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodCode,jdbcType=VARCHAR} AS text) IS NULL OR period_code = #{periodCode})
              AND (CAST(#{scope,jdbcType=VARCHAR} AS text) IS NULL OR scope = #{scope})
              AND (CAST(#{latestOnly} AS boolean) = false OR is_latest = true)
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listEmissions(@Param("tenantId") long tenantId,
                                            @Param("periodCode") String periodCode,
                                            @Param("scope") String scope,
                                            @Param("latestOnly") boolean latestOnly,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT COALESCE(SUM(emission_amount), 0) FROM fin_carbon_emission
            WHERE tenant_id = #{tenantId} AND period_code LIKE #{periodPattern} AND is_latest = true
              AND deleted = 0
            """)
    BigDecimal sumLatestEmission(@Param("tenantId") long tenantId,
                                 @Param("periodPattern") String periodPattern);

    @Insert("""
            INSERT INTO fin_carbon_target
                (id, tenant_id, target_no, target_name, target_year, base_year, base_emission,
                 target_emission, reduction_rate, current_emission, progress, owner_id, status, created_by)
            VALUES (#{id}, #{tenantId}, #{targetNo}, #{targetName}, #{targetYear}, #{baseYear}, #{baseEmission},
                    #{targetEmission}, #{reductionRate}, 0, 0, #{ownerId,jdbcType=BIGINT}, 'ACTIVE', #{userId})
            """)
    void insertTarget(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("targetNo") String targetNo, @Param("targetName") String targetName,
                      @Param("targetYear") int targetYear, @Param("baseYear") int baseYear,
                      @Param("baseEmission") BigDecimal baseEmission,
                      @Param("targetEmission") BigDecimal targetEmission,
                      @Param("reductionRate") BigDecimal reductionRate, @Param("ownerId") Long ownerId,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", target_no AS "targetNo", target_name AS "targetName",
                   target_year AS "targetYear", base_year AS "baseYear", base_emission AS "baseEmission",
                   target_emission AS "targetEmission", reduction_rate AS "reductionRate",
                   current_emission AS "currentEmission", progress AS "progress", owner_id AS "ownerId",
                   status AS "status"
            FROM fin_carbon_target
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTarget(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", target_no AS "targetNo", target_name AS "targetName",
                   target_year AS "targetYear", base_year AS "baseYear", base_emission AS "baseEmission",
                   target_emission AS "targetEmission", reduction_rate AS "reductionRate",
                   current_emission AS "currentEmission", progress AS "progress", status AS "status"
            FROM fin_carbon_target
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY target_year, id
            """)
    List<Map<String, Object>> listTargets(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE fin_carbon_target
            SET current_emission = #{currentEmission}, progress = #{progress}, status = #{status},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateTargetProgress(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("currentEmission") BigDecimal currentEmission,
                             @Param("progress") BigDecimal progress, @Param("status") String status);

    @Insert("""
            INSERT INTO fin_esg_indicator
                (id, tenant_id, indicator_code, indicator_name, category, unit, period_year, target_value,
                 actual_value, data_source, status, created_by)
            VALUES (#{id}, #{tenantId}, #{indicatorCode}, #{indicatorName}, #{category}, #{unit,jdbcType=VARCHAR},
                    #{periodYear}, #{targetValue,jdbcType=NUMERIC}, #{actualValue,jdbcType=NUMERIC},
                    #{dataSource,jdbcType=VARCHAR}, 'DRAFT', #{userId})
            ON CONFLICT (tenant_id, period_year, indicator_code) WHERE deleted = 0 DO NOTHING
            """)
    int insertIndicator(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("indicatorCode") String indicatorCode,
                        @Param("indicatorName") String indicatorName, @Param("category") String category,
                        @Param("unit") String unit, @Param("periodYear") int periodYear,
                        @Param("targetValue") BigDecimal targetValue, @Param("actualValue") BigDecimal actualValue,
                        @Param("dataSource") String dataSource, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", indicator_code AS "indicatorCode", indicator_name AS "indicatorName",
                   category AS "category", unit AS "unit", period_year AS "periodYear",
                   target_value AS "targetValue", actual_value AS "actualValue", data_source AS "dataSource",
                   status AS "status"
            FROM fin_esg_indicator
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodYear,jdbcType=INTEGER} AS integer) IS NULL OR period_year = #{periodYear})
            ORDER BY category, indicator_code
            """)
    List<Map<String, Object>> listIndicators(@Param("tenantId") long tenantId,
                                             @Param("periodYear") Integer periodYear);

    @Insert("""
            INSERT INTO fin_esg_report
                (id, tenant_id, report_no, report_name, period_year, report_type, report_boundary,
                 summary, status, created_by)
            VALUES (#{id}, #{tenantId}, #{reportNo}, #{reportName}, #{periodYear}, #{reportType},
                    #{reportBoundary,jdbcType=VARCHAR}, CAST(#{summary} AS jsonb), 'DRAFT', #{userId})
            """)
    void insertEsgReport(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("reportNo") String reportNo, @Param("reportName") String reportName,
                         @Param("periodYear") int periodYear, @Param("reportType") String reportType,
                         @Param("reportBoundary") String reportBoundary, @Param("summary") String summary,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", report_no AS "reportNo", report_name AS "reportName",
                   period_year AS "periodYear", report_type AS "reportType",
                   report_boundary AS "reportBoundary", summary::text AS "summary", file_id AS "fileId",
                   status AS "status",
                   (extract(epoch FROM published_at) * 1000)::bigint AS "publishedAt"
            FROM fin_esg_report
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findEsgReport(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", report_no AS "reportNo", report_name AS "reportName",
                   period_year AS "periodYear", report_type AS "reportType", status AS "status",
                   (extract(epoch FROM published_at) * 1000)::bigint AS "publishedAt"
            FROM fin_esg_report
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY period_year DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listEsgReports(@Param("tenantId") long tenantId,
                                             @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE fin_esg_report SET status = #{status},
                published_at = CASE WHEN #{status} = 'PUBLISHED' THEN now() ELSE published_at END,
                file_id = COALESCE(#{fileId,jdbcType=BIGINT}, file_id), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateEsgReportStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                              @Param("status") String status, @Param("fileId") Long fileId);
}
