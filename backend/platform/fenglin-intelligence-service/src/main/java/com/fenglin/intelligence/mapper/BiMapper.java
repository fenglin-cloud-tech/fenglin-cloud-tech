package com.fenglin.intelligence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * H3 BI/洞察数据访问。
 */
@Mapper
public interface BiMapper {

    @Insert("INSERT INTO ai_metric_definition (id, tenant_id, code, name, category, metric_type, formula, unit, grain, "
            + "dimensions, owner_id, lineage, status, version_no, created_by) VALUES (#{id}, #{tenantId}, #{code}, "
            + "#{name}, #{category}, #{type}, #{formula}, #{unit}, #{grain}, CAST(#{dimensions} AS jsonb), #{userId}, "
            + "CAST(#{lineage} AS jsonb), #{status}, #{version}, #{userId})")
    void insertMetric(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                      @Param("name") String name, @Param("category") String category, @Param("type") String type,
                      @Param("formula") String formula, @Param("unit") String unit, @Param("grain") String grain,
                      @Param("dimensions") String dimensions, @Param("userId") long userId,
                      @Param("lineage") String lineage, @Param("status") String status, @Param("version") int version);

    @Select("SELECT id, tenant_id, code, name, category, metric_type, formula, unit, grain, dimensions, status, "
            + "version_no, CASE WHEN tenant_id = 0 THEN 'PLATFORM' ELSE 'TENANT' END AS scope FROM ai_metric_definition "
            + "WHERE (tenant_id = #{tenantId} OR tenant_id = 0) AND deleted = 0 "
            + "AND (CAST(#{keyword} AS varchar) IS NULL OR name ILIKE '%' || #{keyword} || '%' "
            + "     OR code ILIKE '%' || #{keyword} || '%') "
            + "ORDER BY tenant_id DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listMetrics(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_metric_definition WHERE (tenant_id = #{tenantId} OR tenant_id = 0) "
            + "AND deleted = 0 AND (CAST(#{keyword} AS varchar) IS NULL OR name ILIKE '%' || #{keyword} || '%' "
            + "OR code ILIKE '%' || #{keyword} || '%')")
    long countMetrics(@Param("tenantId") long tenantId, @Param("keyword") String keyword);

    @Select("SELECT id, tenant_id, code, name, formula, grain, status FROM ai_metric_definition "
            + "WHERE (tenant_id = #{tenantId} OR tenant_id = 0) AND id = #{id} AND deleted = 0")
    Map<String, Object> findMetric(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT count(1) FROM ai_metric_definition WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0")
    long countMetricCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("INSERT INTO ai_dataset (id, tenant_id, code, name, data_source_id, mode, sql_text, accel_engine, "
            + "row_filter, status, refresh_cron, created_by) VALUES (#{id}, #{tenantId}, #{code}, #{name}, "
            + "#{dataSourceId}, #{mode}, #{sqlText}, #{accelEngine}, #{rowFilter}, #{status}, #{refreshCron}, #{userId})")
    void insertDataset(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                       @Param("name") String name, @Param("dataSourceId") long dataSourceId, @Param("mode") String mode,
                       @Param("sqlText") String sqlText, @Param("accelEngine") String accelEngine,
                       @Param("rowFilter") String rowFilter, @Param("status") String status,
                       @Param("refreshCron") String refreshCron, @Param("userId") long userId);

    @Select("SELECT id, code, name, data_source_id, mode, accel_engine, status, refresh_cron, created_at "
            + "FROM ai_dataset WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listDatasets(@Param("tenantId") long tenantId,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_dataset WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countDatasets(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM ai_dataset WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0")
    long countDatasetCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("SELECT id, code, name, data_source_id, mode, sql_text, row_filter, status FROM ai_dataset "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findDataset(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("INSERT INTO ai_dataset_field (id, tenant_id, dataset_id, field_name, field_type, data_type, expression, "
            + "metric_id, dimension_table, is_sensitive, sort_no, created_by) VALUES (#{id}, #{tenantId}, #{datasetId}, "
            + "#{fieldName}, #{fieldType}, #{dataType}, #{expression}, #{metricId}, #{dimensionTable}, #{sensitive}, "
            + "#{sortNo}, #{userId})")
    void insertDatasetField(@Param("id") long id, @Param("tenantId") long tenantId, @Param("datasetId") long datasetId,
                            @Param("fieldName") String fieldName, @Param("fieldType") String fieldType,
                            @Param("dataType") String dataType, @Param("expression") String expression,
                            @Param("metricId") Long metricId, @Param("dimensionTable") String dimensionTable,
                            @Param("sensitive") boolean sensitive, @Param("sortNo") int sortNo,
                            @Param("userId") long userId);

    @Select("SELECT id, dataset_id, field_name, field_type, data_type, expression, metric_id, dimension_table, "
            + "is_sensitive, sort_no FROM ai_dataset_field WHERE tenant_id = #{tenantId} AND dataset_id = #{datasetId} "
            + "AND deleted = 0 ORDER BY sort_no, id")
    List<Map<String, Object>> listDatasetFields(@Param("tenantId") long tenantId, @Param("datasetId") long datasetId);

    @Insert("INSERT INTO ai_dashboard (id, tenant_id, code, name, layout_json, theme, status, share_type, "
            + "mobile_enabled, created_by) VALUES (#{id}, #{tenantId}, #{code}, #{name}, CAST(#{layout} AS jsonb), "
            + "#{theme}, #{status}, #{shareType}, #{mobile}, #{userId})")
    void insertDashboard(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                         @Param("name") String name, @Param("layout") String layout, @Param("theme") String theme,
                         @Param("status") String status, @Param("shareType") String shareType,
                         @Param("mobile") boolean mobile, @Param("userId") long userId);

    @Select("SELECT id, code, name, theme, status, share_type, mobile_enabled, created_at FROM ai_dashboard "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listDashboards(@Param("tenantId") long tenantId,
                                             @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_dashboard WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countDashboards(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM ai_dashboard WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0")
    long countDashboardCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("SELECT id, code, name, layout_json, theme, status, share_type, mobile_enabled, created_at "
            + "FROM ai_dashboard WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findDashboard(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("INSERT INTO ai_chart (id, tenant_id, dashboard_id, name, chart_type, dataset_id, query_json, style_json, "
            + "drill_json, alert_json, created_by) VALUES (#{id}, #{tenantId}, #{dashboardId}, #{name}, #{chartType}, "
            + "#{datasetId}, CAST(#{query} AS jsonb), CAST(#{style} AS jsonb), CAST(#{drill} AS jsonb), "
            + "CAST(#{alert} AS jsonb), #{userId})")
    void insertChart(@Param("id") long id, @Param("tenantId") long tenantId, @Param("dashboardId") long dashboardId,
                     @Param("name") String name, @Param("chartType") String chartType,
                     @Param("datasetId") long datasetId, @Param("query") String query,
                     @Param("style") String style, @Param("drill") String drill, @Param("alert") String alert,
                     @Param("userId") long userId);

    @Select("SELECT id, dashboard_id, name, chart_type, dataset_id, query_json, style_json, created_at "
            + "FROM ai_chart WHERE tenant_id = #{tenantId} AND dashboard_id = #{dashboardId} AND deleted = 0 "
            + "ORDER BY id")
    List<Map<String, Object>> listCharts(@Param("tenantId") long tenantId, @Param("dashboardId") long dashboardId);

    @Insert("INSERT INTO ai_insight_task (id, tenant_id, name, target_type, target_id, trigger_type, cron_expr, "
            + "rule_json, status, created_by) VALUES (#{id}, #{tenantId}, #{name}, #{targetType}, #{targetId}, "
            + "#{triggerType}, #{cron}, CAST(#{rule} AS jsonb), #{status}, #{userId})")
    void insertInsightTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                           @Param("targetType") String targetType, @Param("targetId") long targetId,
                           @Param("triggerType") String triggerType, @Param("cron") String cron,
                           @Param("rule") String rule, @Param("status") String status, @Param("userId") long userId);

    @Select("SELECT id, name, target_type, target_id, trigger_type, cron_expr, status, last_run_at, created_at "
            + "FROM ai_insight_task WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listInsightTasks(@Param("tenantId") long tenantId,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_insight_task WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countInsightTasks(@Param("tenantId") long tenantId);

    @Select("SELECT id, name, target_type, target_id, trigger_type, status FROM ai_insight_task "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findInsightTask(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE ai_insight_task SET status = #{status}, last_run_at = now(), updated_by = #{userId} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int updateInsightTaskRun(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("status") String status, @Param("userId") long userId);

    @Insert("INSERT INTO ai_insight_result (id, tenant_id, task_id, metric_id, anomaly_type, magnitude, conclusion, "
            + "suggestion, confidence, evidence_json, created_by) VALUES (#{id}, #{tenantId}, #{taskId}, #{metricId}, "
            + "#{anomalyType}, #{magnitude}, #{conclusion}, #{suggestion}, #{confidence}, CAST(#{evidence} AS jsonb), "
            + "#{userId})")
    void insertInsightResult(@Param("id") long id, @Param("tenantId") long tenantId, @Param("taskId") long taskId,
                             @Param("metricId") Long metricId, @Param("anomalyType") String anomalyType,
                             @Param("magnitude") BigDecimal magnitude, @Param("conclusion") String conclusion,
                             @Param("suggestion") String suggestion, @Param("confidence") BigDecimal confidence,
                             @Param("evidence") String evidence, @Param("userId") long userId);

    @Select("SELECT id, task_id, metric_id, anomaly_type, magnitude, conclusion, suggestion, confidence, created_at "
            + "FROM ai_insight_result WHERE tenant_id = #{tenantId} AND task_id = #{taskId} ORDER BY id DESC")
    List<Map<String, Object>> listInsightResults(@Param("tenantId") long tenantId, @Param("taskId") long taskId);

    @Insert("INSERT INTO ai_attribution_analysis (id, tenant_id, insight_result_id, metric_id, dimension_field, "
            + "algorithm, contributions, main_factor, created_by) VALUES (#{id}, #{tenantId}, #{resultId}, #{metricId}, "
            + "#{dimensionField}, #{algorithm}, CAST(#{contributions} AS jsonb), #{mainFactor}, #{userId})")
    void insertAttribution(@Param("id") long id, @Param("tenantId") long tenantId, @Param("resultId") Long resultId,
                           @Param("metricId") long metricId, @Param("dimensionField") String dimensionField,
                           @Param("algorithm") String algorithm, @Param("contributions") String contributions,
                           @Param("mainFactor") String mainFactor, @Param("userId") long userId);

    @Select("SELECT id, insight_result_id, metric_id, dimension_field, algorithm, contributions, main_factor, "
            + "created_at FROM ai_attribution_analysis WHERE tenant_id = #{tenantId} AND metric_id = #{metricId} "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> listAttributions(@Param("tenantId") long tenantId, @Param("metricId") long metricId,
                                               @Param("limit") int limit);

    @Insert("INSERT INTO ai_insight_subscription (id, tenant_id, task_id, channels, receivers, frequency, status, "
            + "created_by) VALUES (#{id}, #{tenantId}, #{taskId}, #{channels}, #{receivers}, #{frequency}, #{status}, #{userId})")
    void insertSubscription(@Param("id") long id, @Param("tenantId") long tenantId, @Param("taskId") long taskId,
                            @Param("channels") String channels, @Param("receivers") String receivers,
                            @Param("frequency") String frequency, @Param("status") String status,
                            @Param("userId") long userId);

    @Select("SELECT id, task_id, channels, receivers, frequency, status, created_at FROM ai_insight_subscription "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> listSubscriptions(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Insert("INSERT INTO ai_data_permission (id, tenant_id, subject_type, subject_id, resource_type, resource_id, "
            + "data_scope, scope_dept_ids, column_hidden, row_filter_expr, created_by) VALUES (#{id}, #{tenantId}, "
            + "#{subjectType}, #{subjectId}, #{resourceType}, #{resourceId}, #{dataScope}, "
            + "CASE WHEN CAST(#{deptCsv} AS varchar) IS NULL THEN NULL "
            + "ELSE string_to_array(#{deptCsv}, ',')::bigint[] END, "
            + "#{columnHidden}, #{rowFilter}, #{userId})")
    void insertDataPermission(@Param("id") long id, @Param("tenantId") long tenantId,
                              @Param("subjectType") String subjectType, @Param("subjectId") long subjectId,
                              @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId,
                              @Param("dataScope") String dataScope, @Param("deptCsv") String deptCsv,
                              @Param("columnHidden") String columnHidden, @Param("rowFilter") String rowFilter,
                              @Param("userId") long userId);
}
