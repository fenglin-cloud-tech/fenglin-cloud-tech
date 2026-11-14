package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * E2 度量配置、快照、贡献度、DORA、预警 Mapper。
 */
@Mapper
public interface MetricMapper {

    @Select("""
            SELECT id AS "id", tenant_id AS "tenantId", metric_code AS "metricCode",
                   metric_name AS "metricName", metric_group AS "metricGroup", formula AS "formula",
                   caliber_version AS "caliberVersion", weight AS "weight", target_value AS "targetValue",
                   unit AS "unit", status AS "status"
            FROM (
                SELECT DISTINCT ON (metric_code) *
                FROM devops_metric_config
                WHERE (tenant_id = #{tenantId} OR tenant_id = 0) AND deleted = 0
                ORDER BY metric_code, (tenant_id = #{tenantId}) DESC, caliber_version DESC
            ) c
            ORDER BY metric_group, metric_code
            """)
    List<Map<String, Object>> listConfigs(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", tenant_id AS "tenantId", metric_code AS "metricCode",
                   metric_name AS "metricName", metric_group AS "metricGroup", formula AS "formula",
                   caliber_version AS "caliberVersion", weight AS "weight", target_value AS "targetValue",
                   unit AS "unit", status AS "status"
            FROM devops_metric_config
            WHERE tenant_id = #{tenantId} AND metric_code = #{metricCode} AND deleted = 0
            ORDER BY caliber_version DESC
            LIMIT 1
            """)
    Map<String, Object> findTenantConfig(@Param("tenantId") long tenantId,
                                         @Param("metricCode") String metricCode);

    @Select("""
            SELECT id AS "id", tenant_id AS "tenantId", metric_code AS "metricCode",
                   metric_name AS "metricName", metric_group AS "metricGroup", formula AS "formula",
                   caliber_version AS "caliberVersion", weight AS "weight", target_value AS "targetValue",
                   unit AS "unit", status AS "status"
            FROM devops_metric_config
            WHERE tenant_id = 0 AND metric_code = #{metricCode} AND deleted = 0
            ORDER BY caliber_version DESC
            LIMIT 1
            """)
    Map<String, Object> findPlatformConfig(@Param("metricCode") String metricCode);

    @Update("""
            UPDATE devops_metric_config
            SET metric_name = #{metricName}, metric_group = #{metricGroup}, formula = #{formula},
                caliber_version = #{caliberVersion}, weight = #{weight,jdbcType=NUMERIC},
                target_value = #{targetValue,jdbcType=NUMERIC}, unit = #{unit,jdbcType=VARCHAR},
                status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateConfig(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("metricName") String metricName, @Param("metricGroup") String metricGroup,
                     @Param("formula") String formula, @Param("caliberVersion") String caliberVersion,
                     @Param("weight") BigDecimal weight, @Param("targetValue") BigDecimal targetValue,
                     @Param("unit") String unit, @Param("status") String status,
                     @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_metric_caliber
                (id, tenant_id, metric_code, caliber_version, formula, effective_from, status, created_by)
            VALUES (#{id}, #{tenantId}, #{metricCode}, #{caliberVersion}, #{formula},
                    CAST(#{effectiveFrom} AS timestamptz), 'published', #{userId})
            ON CONFLICT (tenant_id, metric_code, caliber_version) WHERE deleted = 0
            DO UPDATE SET formula = EXCLUDED.formula, effective_from = EXCLUDED.effective_from,
                          status = 'published', updated_by = #{userId},
                          version = devops_metric_caliber.version + 1
            """)
    int upsertCaliber(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("metricCode") String metricCode, @Param("caliberVersion") String caliberVersion,
                      @Param("formula") String formula, @Param("effectiveFrom") String effectiveFrom,
                      @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id AS "id", metric_code AS "metricCode", caliber_version AS "caliberVersion",
                   formula AS "formula", effective_from::text AS "effectiveFrom",
                   created_at::text AS "createdAt"
            FROM devops_metric_caliber
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="metricCode != null">AND metric_code = #{metricCode}</if>
            ORDER BY metric_code, caliber_version DESC
            </script>
            """)
    List<Map<String, Object>> listCalibers(@Param("tenantId") long tenantId,
                                           @Param("metricCode") String metricCode);

    @Select("""
            <script>
            SELECT count(1) AS "sampleCount",
                   count(1) FILTER (WHERE data_quality = 'missing') AS "missingCount",
                   count(1) FILTER (WHERE data_quality = 'outlier') AS "outlierCount"
            FROM devops_metric_snapshot
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND period_type = #{periodType} AND period_key = #{periodKey}
              <if test="metricCode != null">AND metric_code = #{metricCode}</if>
            </script>
            """)
    Map<String, Object> aggregateDataQuality(@Param("tenantId") long tenantId,
                                             @Param("metricCode") String metricCode,
                                             @Param("periodType") String periodType,
                                             @Param("periodKey") String periodKey);

    @Insert("""
            INSERT INTO devops_metric_config
                (id, tenant_id, metric_code, metric_name, metric_group, formula, caliber_version, weight,
                 target_value, unit, status, created_by)
            VALUES (#{id}, #{tenantId}, #{metricCode}, #{metricName}, #{metricGroup}, #{formula},
                    #{caliberVersion}, #{weight,jdbcType=NUMERIC}, #{targetValue,jdbcType=NUMERIC},
                    #{unit,jdbcType=VARCHAR}, #{status}, #{userId})
            ON CONFLICT (tenant_id, metric_code, caliber_version) WHERE deleted = 0
            DO UPDATE SET metric_name = EXCLUDED.metric_name, metric_group = EXCLUDED.metric_group,
                          formula = EXCLUDED.formula, weight = EXCLUDED.weight,
                          target_value = EXCLUDED.target_value, unit = EXCLUDED.unit,
                          status = EXCLUDED.status,
                          updated_by = #{userId}, version = devops_metric_config.version + 1
            """)
    int upsertConfig(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("metricCode") String metricCode, @Param("metricName") String metricName,
                     @Param("metricGroup") String metricGroup, @Param("formula") String formula,
                     @Param("caliberVersion") String caliberVersion,
                     @Param("weight") BigDecimal weight, @Param("targetValue") BigDecimal targetValue,
                     @Param("unit") String unit, @Param("status") String status,
                     @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_metric_snapshot
                (id, tenant_id, metric_code, scope_type, scope_id, period_type, period_key, metric_value,
                 numerator, denominator, sample_count, data_quality, caliber_version, computed_at, created_by)
            VALUES (#{id}, #{tenantId}, #{metricCode}, 'product', #{productId}, #{periodType}, #{periodKey},
                    #{metricValue}, #{numerator,jdbcType=NUMERIC}, #{denominator,jdbcType=NUMERIC},
                    #{sampleCount,jdbcType=INTEGER}, #{dataQuality}, 'v1', now(), #{userId})
            ON CONFLICT (tenant_id, metric_code, scope_type, scope_id, period_type, period_key, caliber_version)
                WHERE deleted = 0
            DO UPDATE SET metric_value = EXCLUDED.metric_value, numerator = EXCLUDED.numerator,
                          denominator = EXCLUDED.denominator, sample_count = EXCLUDED.sample_count,
                          data_quality = EXCLUDED.data_quality, computed_at = now(),
                          updated_by = #{userId}, version = devops_metric_snapshot.version + 1
            """)
    int upsertSnapshot(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("metricCode") String metricCode, @Param("productId") long productId,
                       @Param("periodType") String periodType, @Param("periodKey") String periodKey,
                       @Param("metricValue") BigDecimal metricValue, @Param("numerator") BigDecimal numerator,
                       @Param("denominator") BigDecimal denominator, @Param("sampleCount") Integer sampleCount,
                       @Param("dataQuality") String dataQuality, @Param("userId") long userId);

    @Select("""
            SELECT s.metric_code AS "metricCode", c.metric_name AS "metricName",
                   c.metric_group AS "metricGroup", s.metric_value AS "metricValue", c.unit AS "unit",
                   s.data_quality AS "dataQuality", s.caliber_version AS "caliberVersion"
            FROM devops_metric_snapshot s
            LEFT JOIN devops_metric_config c ON c.tenant_id = s.tenant_id
                 AND c.metric_code = s.metric_code AND c.deleted = 0
            WHERE s.tenant_id = #{tenantId} AND s.scope_type = 'product' AND s.scope_id = #{productId}
              AND s.period_type = #{periodType} AND s.period_key = #{periodKey} AND s.deleted = 0
            ORDER BY s.metric_code
            """)
    List<Map<String, Object>> listSnapshots(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                            @Param("periodType") String periodType,
                                            @Param("periodKey") String periodKey);

    @Select("""
            SELECT metric_value FROM devops_metric_snapshot
            WHERE tenant_id = #{tenantId} AND metric_code = #{metricCode} AND scope_type = 'product'
              AND scope_id = #{productId} AND period_type = #{periodType} AND period_key = #{periodKey}
              AND deleted = 0
            """)
    BigDecimal findSnapshotValue(@Param("tenantId") long tenantId, @Param("metricCode") String metricCode,
                                 @Param("productId") long productId, @Param("periodType") String periodType,
                                 @Param("periodKey") String periodKey);

    @Insert("""
            INSERT INTO devops_contribution
                (id, tenant_id, user_id, product_id, period_type, period_key, commit_count, add_lines,
                 del_lines, files_changed, effective_commits, complexity_score, created_by)
            VALUES (#{id}, #{tenantId}, #{userId}, #{productId}, #{periodType}, #{periodKey}, #{commitCount},
                    #{addLines}, #{delLines}, #{filesChanged}, #{effectiveCommits}, #{complexityScore,jdbcType=NUMERIC},
                    #{operatorId})
            ON CONFLICT (tenant_id, user_id, product_id, period_type, period_key) WHERE deleted = 0
            DO UPDATE SET commit_count = EXCLUDED.commit_count, add_lines = EXCLUDED.add_lines,
                          del_lines = EXCLUDED.del_lines, files_changed = EXCLUDED.files_changed,
                          effective_commits = EXCLUDED.effective_commits,
                          complexity_score = EXCLUDED.complexity_score, updated_by = #{operatorId},
                          version = devops_contribution.version + 1
            """)
    int upsertContribution(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("userId") long userId, @Param("productId") long productId,
                           @Param("periodType") String periodType, @Param("periodKey") String periodKey,
                           @Param("commitCount") int commitCount, @Param("addLines") int addLines,
                           @Param("delLines") int delLines, @Param("filesChanged") int filesChanged,
                           @Param("effectiveCommits") int effectiveCommits,
                           @Param("complexityScore") BigDecimal complexityScore,
                           @Param("operatorId") long operatorId);

    @Select("""
            <script>
            SELECT user_id AS "userId", product_id AS "productId", period_type AS "periodType",
                   period_key AS "periodKey", commit_count AS "commitCount", add_lines AS "addLines",
                   del_lines AS "delLines", effective_commits AS "effectiveCommits",
                   complexity_score AS "complexityScore"
            FROM devops_contribution
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
              <if test="periodType != null">AND period_type = #{periodType}</if>
              <if test="periodKey != null">AND period_key = #{periodKey}</if>
            ORDER BY commit_count DESC, user_id
            </script>
            """)
    List<Map<String, Object>> listContributions(@Param("tenantId") long tenantId,
                                                @Param("productId") long productId,
                                                @Param("periodType") String periodType,
                                                @Param("periodKey") String periodKey);

    /**
     * 员工自助：只按 user_id 返回本人贡献度，productId 可选。
     */
    @Select("""
            <script>
            SELECT user_id AS "userId", product_id AS "productId", period_type AS "periodType",
                   period_key AS "periodKey", commit_count AS "commitCount", add_lines AS "addLines",
                   del_lines AS "delLines", effective_commits AS "effectiveCommits",
                   complexity_score AS "complexityScore"
            FROM devops_contribution
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = 0
              <if test="productId != null">AND product_id = #{productId}</if>
              <if test="periodType != null">AND period_type = #{periodType}</if>
              <if test="periodKey != null">AND period_key = #{periodKey}</if>
            ORDER BY period_key DESC, product_id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listContributionsByUser(@Param("tenantId") long tenantId,
                                                      @Param("userId") long userId,
                                                      @Param("productId") Long productId,
                                                      @Param("periodType") String periodType,
                                                      @Param("periodKey") String periodKey,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset);

    @Select("""
            SELECT c.author_user_id AS "userId", count(1) AS "commitCount",
                   COALESCE(sum(c.add_lines), 0) AS "addLines", COALESCE(sum(c.del_lines), 0) AS "delLines",
                   COALESCE(sum(c.files_changed), 0) AS "filesChanged",
                   count(DISTINCT l.commit_id) AS "linkedCommits"
            FROM devops_commit c
            JOIN devops_repo p ON p.tenant_id = c.tenant_id AND p.id = c.repo_id AND p.deleted = 0
            LEFT JOIN devops_commit_link l ON l.tenant_id = c.tenant_id AND l.commit_id = c.id AND l.deleted = 0
            WHERE c.tenant_id = #{tenantId} AND p.product_id = #{productId}
              AND c.is_excluded = 0 AND c.is_merge = 0 AND c.author_user_id IS NOT NULL
              AND c.committed_at >= CAST(#{from} AS timestamptz) AND c.committed_at < CAST(#{to} AS timestamptz)
              AND c.deleted = 0
            GROUP BY c.author_user_id
            """)
    List<Map<String, Object>> aggregateCommitContribution(@Param("tenantId") long tenantId,
                                                          @Param("productId") long productId,
                                                          @Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT count(1) FROM devops_commit c
            JOIN devops_repo p ON p.tenant_id = c.tenant_id AND p.id = c.repo_id AND p.deleted = 0
            WHERE c.tenant_id = #{tenantId} AND p.product_id = #{productId}
              AND c.committed_at >= CAST(#{from} AS timestamptz) AND c.committed_at < CAST(#{to} AS timestamptz)
              AND c.deleted = 0
            """)
    long countCommits(@Param("tenantId") long tenantId, @Param("productId") long productId,
                      @Param("from") String from, @Param("to") String to);

    @Insert("""
            INSERT INTO devops_dora_metric
                (id, tenant_id, product_id, period_type, period_key, deploy_freq, lead_time_hours,
                 change_failure_rate, mttr_hours, level, computed_at, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{periodType}, #{periodKey}, #{deployFreq},
                    #{leadTimeHours}, #{changeFailureRate}, #{mttrHours}, #{level}, now(), #{userId})
            ON CONFLICT (tenant_id, product_id, period_type, period_key) WHERE deleted = 0
            DO UPDATE SET deploy_freq = EXCLUDED.deploy_freq, lead_time_hours = EXCLUDED.lead_time_hours,
                          change_failure_rate = EXCLUDED.change_failure_rate, mttr_hours = EXCLUDED.mttr_hours,
                          level = EXCLUDED.level, computed_at = now(), updated_by = #{userId},
                          version = devops_dora_metric.version + 1
            """)
    int upsertDora(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                   @Param("periodType") String periodType, @Param("periodKey") String periodKey,
                   @Param("deployFreq") BigDecimal deployFreq, @Param("leadTimeHours") BigDecimal leadTimeHours,
                   @Param("changeFailureRate") BigDecimal changeFailureRate,
                   @Param("mttrHours") BigDecimal mttrHours, @Param("level") String level,
                   @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", period_type AS "periodType",
                   period_key AS "periodKey", deploy_freq AS "deployFreq", lead_time_hours AS "leadTimeHours",
                   change_failure_rate AS "changeFailureRate", mttr_hours AS "mttrHours", level AS "level",
                   computed_at::text AS "computedAt"
            FROM devops_dora_metric
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND period_type = #{periodType}
              AND period_key = #{periodKey} AND deleted = 0
            """)
    Map<String, Object> findDora(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                 @Param("periodType") String periodType, @Param("periodKey") String periodKey);

    @Insert("""
            INSERT INTO devops_metric_alert
                (id, tenant_id, metric_code, scope_type, scope_id, threshold, compare_op, status,
                 notify_channel, created_by)
            VALUES (#{id}, #{tenantId}, #{metricCode}, #{scopeType}, #{scopeId}, #{threshold}, #{compareOp},
                    'active', #{notifyChannel,jdbcType=VARCHAR}, #{userId})
            """)
    void insertAlert(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("metricCode") String metricCode, @Param("scopeType") String scopeType,
                     @Param("scopeId") long scopeId, @Param("threshold") BigDecimal threshold,
                     @Param("compareOp") String compareOp, @Param("notifyChannel") String notifyChannel,
                     @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id AS "id", metric_code AS "metricCode", scope_type AS "scopeType", scope_id AS "scopeId",
                   threshold AS "threshold", compare_op AS "compareOp", triggered_value AS "triggeredValue",
                   status AS "status", notify_channel AS "notifyChannel", triggered_at::text AS "triggeredAt"
            FROM devops_metric_alert
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null">AND status = #{status}</if>
              <if test="metricCode != null">AND metric_code = #{metricCode}</if>
            ORDER BY id DESC
            </script>
            """)
    List<Map<String, Object>> listAlerts(@Param("tenantId") long tenantId, @Param("status") String status,
                                         @Param("metricCode") String metricCode);

    @Select("""
            SELECT id AS "id", metric_code AS "metricCode", scope_type AS "scopeType", scope_id AS "scopeId",
                   threshold AS "threshold", compare_op AS "compareOp", triggered_value AS "triggeredValue",
                   status AS "status", notify_channel AS "notifyChannel", triggered_at::text AS "triggeredAt"
            FROM devops_metric_alert
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findAlert(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE devops_metric_alert
            SET threshold = #{threshold}, compare_op = #{compareOp},
                notify_channel = #{notifyChannel,jdbcType=VARCHAR}, status = #{status},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateAlert(@Param("tenantId") long tenantId, @Param("id") long id,
                    @Param("threshold") BigDecimal threshold, @Param("compareOp") String compareOp,
                    @Param("notifyChannel") String notifyChannel, @Param("status") String status,
                    @Param("userId") long userId);

    @Update("""
            UPDATE devops_metric_alert
            SET status = 'resolved', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int resolveAlert(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE devops_metric_alert
            SET status = 'triggered', triggered_value = #{value}, triggered_at = now(),
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int triggerAlert(@Param("tenantId") long tenantId, @Param("id") long id, @Param("value") BigDecimal value,
                     @Param("userId") long userId);
}
