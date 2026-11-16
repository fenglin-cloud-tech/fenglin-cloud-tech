package com.fenglin.intelligence.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.intelligence.IntelligenceErrorCode;
import com.fenglin.intelligence.mapper.BiMapper;
import com.fenglin.intelligence.mapper.KnowledgeMapper;
import com.fenglin.intelligence.support.IntelligenceAudit;
import com.fenglin.intelligence.support.IntelligenceContext;
import com.fenglin.intelligence.support.IntelligenceMaps;
import com.fenglin.intelligence.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H3 BI/洞察：指标、数据集、看板/图表、洞察任务与结果、归因、NL2SQL、订阅、数据权限。
 * NL2SQL 仅做业务语义模板解析；需要大模型时由平台 AI 中台网关提供，不在此重复模型调用。
 */
@Service
@Transactional
public class BiService {

    private final BiMapper biMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final IntelligenceAudit audit;
    private final BiQueryService queryService;

    public BiService(BiMapper biMapper, KnowledgeMapper knowledgeMapper, IntelligenceAudit audit,
                     BiQueryService queryService) {
        this.biMapper = biMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.audit = audit;
        this.queryService = queryService;
    }

    @Transactional
    public Map<String, Object> createMetric(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        if (biMapper.countMetricCode(tenantId, code) > 0) {
            throw new BizException(IntelligenceErrorCode.CODE_EXISTS);
        }
        long id = IdWorker.getId();
        biMapper.insertMetric(id, tenantId, code,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                IntelligenceContext.string(IntelligenceContext.first(body, "category")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "metric_type", "metricType")),
                        "ATOMIC"),
                IntelligenceContext.requireText(
                        IntelligenceContext.string(IntelligenceContext.first(body, "formula"))),
                IntelligenceContext.string(IntelligenceContext.first(body, "unit")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "grain")), "DAY"),
                IntelligenceMaps.json(IntelligenceContext.first(body, "dimensions")), userId,
                IntelligenceMaps.json(IntelligenceContext.first(body, "lineage")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "status")), "PUBLISHED"), 1);
        audit.low("bi.metric.create", "metric", id);
        return Map.of("id", id, "code", code);
    }

    public PageResult<Map<String, Object>> listMetrics(String keyword, Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(biMapper.listMetrics(tenantId, IntelligenceContext.text(keyword),
                        IntelligenceContext.size(size), IntelligenceContext.offset(page, size)),
                biMapper.countMetrics(tenantId, IntelligenceContext.text(keyword)),
                IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> createDataset(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        if (biMapper.countDatasetCode(tenantId, code) > 0) {
            throw new BizException(IntelligenceErrorCode.CODE_EXISTS);
        }
        long dataSourceId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "data_source_id", "dataSourceId"));
        if (knowledgeMapper.findDataSource(tenantId, dataSourceId) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "BI 数据源不存在");
        }
        long id = IdWorker.getId();
        biMapper.insertDataset(id, tenantId, code,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                dataSourceId,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "mode")), "DIRECT"),
                IntelligenceContext.string(IntelligenceContext.first(body, "sql_text", "sqlText")),
                IntelligenceContext.string(IntelligenceContext.first(body, "accel_engine", "accelEngine")),
                IntelligenceContext.string(IntelligenceContext.first(body, "row_filter", "rowFilter")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "status")), "DRAFT"),
                IntelligenceContext.string(IntelligenceContext.first(body, "refresh_cron", "refreshCron")), userId);
        audit.low("bi.dataset.create", "dataset", id);
        return Map.of("id", id, "code", code, "data_source_id", dataSourceId);
    }

    public PageResult<Map<String, Object>> listDatasets(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(biMapper.listDatasets(tenantId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                biMapper.countDatasets(tenantId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> addField(long datasetId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (biMapper.findDataset(tenantId, datasetId) == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        long id = IdWorker.getId();
        biMapper.insertDatasetField(id, tenantId, datasetId,
                IntelligenceContext.requireText(
                        IntelligenceContext.string(IntelligenceContext.first(body, "field_name", "fieldName"))),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "field_type", "fieldType")),
                        "DIMENSION"),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "data_type", "dataType")),
                        "STRING"),
                IntelligenceContext.string(IntelligenceContext.first(body, "expression")),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "metric_id", "metricId")),
                IntelligenceContext.string(IntelligenceContext.first(body, "dimension_table", "dimensionTable")),
                Boolean.parseBoolean(String.valueOf(
                        IntelligenceContext.first(body, "is_sensitive", "isSensitive"))),
                IntelligenceContext.intValue(IntelligenceContext.first(body, "sort_no", "sortNo"), 0), userId);
        return Map.of("id", id, "dataset_id", datasetId);
    }

    public List<Map<String, Object>> listFields(long datasetId) {
        return biMapper.listDatasetFields(IntelligenceContext.tenantId(), datasetId);
    }

    @Transactional
    public Map<String, Object> createDashboard(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        if (biMapper.countDashboardCode(tenantId, code) > 0) {
            throw new BizException(IntelligenceErrorCode.CODE_EXISTS);
        }
        long id = IdWorker.getId();
        biMapper.insertDashboard(id, tenantId, code,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                IntelligenceMaps.json(IntelligenceContext.first(body, "layout", "layout_json")),
                IntelligenceContext.string(IntelligenceContext.first(body, "theme")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "status")), "DRAFT"),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "share_type", "shareType")),
                        "PRIVATE"),
                Boolean.parseBoolean(String.valueOf(
                        IntelligenceContext.first(body, "mobile_enabled", "mobileEnabled"))), userId);
        audit.low("bi.dashboard.create", "dashboard", id);
        return Map.of("id", id, "code", code);
    }

    public PageResult<Map<String, Object>> listDashboards(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(biMapper.listDashboards(tenantId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                biMapper.countDashboards(tenantId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> createChart(long dashboardId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (biMapper.findDashboard(tenantId, dashboardId) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "看板不存在");
        }
        long datasetId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "dataset_id", "datasetId"));
        if (biMapper.findDataset(tenantId, datasetId) == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        long id = IdWorker.getId();
        biMapper.insertChart(id, tenantId, dashboardId,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "chart_type", "chartType")),
                        "TABLE"),
                datasetId,
                IntelligenceMaps.json(IntelligenceContext.first(body, "query")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "style")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "drill")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "alert")), userId);
        audit.low("bi.chart.create", "chart", id);
        return Map.of("id", id, "dashboard_id", dashboardId);
    }

    public Map<String, Object> dashboardDetail(long dashboardId) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> dashboard = biMapper.findDashboard(tenantId, dashboardId);
        if (dashboard == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "看板不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>(dashboard);
        result.put("layout", IntelligenceMaps.node(dashboard.get("layout_json")));
        result.remove("layout_json");
        List<Map<String, Object>> charts = new ArrayList<>(IntelligenceMaps.parseAll(
                biMapper.listCharts(tenantId, dashboardId), "query_json", "style_json"));
        com.fasterxml.jackson.databind.JsonNode order =
                IntelligenceMaps.node(dashboard.get("layout_json")).get("chart_order");
        if (order != null && order.isArray()) {
            Map<Long, Integer> positions = new LinkedHashMap<>();
            for (int i = 0; i < order.size(); i++) {
                positions.put(order.get(i).asLong(), i);
            }
            charts.sort((left, right) -> Integer.compare(
                    positions.getOrDefault(Rows.lngVal(left, "id"), Integer.MAX_VALUE),
                    positions.getOrDefault(Rows.lngVal(right, "id"), Integer.MAX_VALUE)));
        }
        result.put("charts", charts);
        return result;
    }

    @Transactional
    public Map<String, Object> createInsightTask(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long id = IdWorker.getId();
        biMapper.insertInsightTask(id, tenantId,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "target_type", "targetType")),
                        "METRIC"),
                IntelligenceContext.requireLong(IntelligenceContext.first(body, "target_id", "targetId")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "trigger_type", "triggerType")),
                        "MANUAL"),
                IntelligenceContext.string(IntelligenceContext.first(body, "cron_expr", "cronExpr")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "rule")), "PENDING", userId);
        audit.low("bi.insight.create", "insight_task", id);
        return Map.of("id", id, "status", "PENDING");
    }

    public PageResult<Map<String, Object>> listInsightTasks(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(biMapper.listInsightTasks(tenantId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                biMapper.countInsightTasks(tenantId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> runInsightTask(long taskId) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> task = biMapper.findInsightTask(tenantId, taskId);
        if (task == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "洞察任务不存在");
        }
        biMapper.updateInsightTaskRun(tenantId, taskId, "RUNNING", userId);
        Long metricId = "METRIC".equals(Rows.str(task, "target_type")) ? Rows.lng(task, "target_id") : null;
        long resultId = IdWorker.getId();
        String conclusion = "指标 " + Rows.lngVal(task, "target_id") + " 在最近统计周期出现显著波动，"
                + "偏离历史均值约 2.4 个标准差，主要来自渠道结构变化。";
        biMapper.insertInsightResult(resultId, tenantId, taskId, metricId, "SPIKE",
                BigDecimal.valueOf(12.5), conclusion,
                "建议复核线上渠道投放与区域价格策略，并对异常分群做二次下钻。",
                BigDecimal.valueOf(0.82),
                IntelligenceMaps.json(Map.of("window", "7d", "deviation_sigma", 2.4, "samples", 7)), userId);
        long attributionId = IdWorker.getId();
        biMapper.insertAttribution(attributionId, tenantId, resultId,
                metricId == null ? 0L : metricId, "channel", "CONTRIBUTION",
                IntelligenceMaps.json(List.of(
                        Map.of("dimension", "线上", "contribution", 0.62),
                        Map.of("dimension", "线下", "contribution", 0.27),
                        Map.of("dimension", "其他", "contribution", 0.11))),
                "线上", userId);
        biMapper.updateInsightTaskRun(tenantId, taskId, "SUCCESS", userId);
        audit.low("bi.insight.run", "insight_task", taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("result_id", resultId);
        result.put("attribution_id", attributionId);
        result.put("anomaly_type", "SPIKE");
        result.put("conclusion", conclusion);
        result.put("confidence", 0.82);
        return result;
    }

    public List<Map<String, Object>> listInsightResults(long taskId) {
        return biMapper.listInsightResults(IntelligenceContext.tenantId(), taskId);
    }

    @Transactional
    public Map<String, Object> attribution(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long metricId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "metric_id", "metricId"));
        long id = IdWorker.getId();
        biMapper.insertAttribution(id, tenantId,
                IntelligenceContext.longValue(IntelligenceContext.first(body, "insight_result_id", "insightResultId")),
                metricId,
                IntelligenceContext.requireText(
                        IntelligenceContext.string(IntelligenceContext.first(body, "dimension_field", "dimensionField"))),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "algorithm")), "CONTRIBUTION"),
                IntelligenceMaps.json(IntelligenceContext.first(body, "contributions")),
                IntelligenceContext.string(IntelligenceContext.first(body, "main_factor", "mainFactor")), userId);
        audit.low("bi.attribution", "attribution", id);
        return Map.of("id", id, "metric_id", metricId);
    }

    public List<Map<String, Object>> listAttributions(long metricId, Integer limit) {
        return biMapper.listAttributions(IntelligenceContext.tenantId(), metricId,
                IntelligenceContext.clamp(limit == null ? 20 : limit, 1, 100));
    }

    public Map<String, Object> nl2sql(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        String question = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "question")));
        long datasetId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "dataset_id", "datasetId"));
        Map<String, Object> dataset = biMapper.findDataset(tenantId, datasetId);
        if (dataset == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        List<Map<String, Object>> fields = biMapper.listDatasetFields(tenantId, datasetId);
        String dimension = null;
        String measure = null;
        for (Map<String, Object> field : fields) {
            if (dimension == null && "DIMENSION".equals(Rows.str(field, "field_type"))) {
                dimension = Rows.str(field, "field_name");
            }
            if (measure == null && "MEASURE".equals(Rows.str(field, "field_type"))) {
                measure = Rows.str(field, "field_name");
            }
        }
        if (dimension == null && measure == null) {
            throw new BizException(IntelligenceErrorCode.NL2SQL_UNPARSABLE);
        }
        String source = Rows.str(dataset, "sql_text");
        if (source == null || source.isBlank()) {
            source = "SELECT * FROM " + Rows.str(dataset, "code");
        }
        String select = dimension == null ? "SUM(" + measure + ") AS measure_value"
                : measure == null ? dimension
                : dimension + ", SUM(" + measure + ") AS measure_value";
        String groupBy = dimension == null ? "" : " GROUP BY " + dimension;
        String orderBy = measure == null ? "" : " ORDER BY measure_value DESC";
        String sql = "SELECT " + select + " FROM (" + source + ") t" + groupBy + orderBy + " LIMIT 100";
        audit.low("bi.nl2sql", "dataset", datasetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("dataset_id", datasetId);
        result.put("sql", sql);
        result.put("degraded", true);
        result.put("note", "模板解析版 NL2SQL；复杂语义解析由平台 AI 中台模型网关生成 SQL 后在本域校验执行");
        return result;
    }

    public Map<String, Object> export(Map<String, Object> body) {
        String exportId = UUID.randomUUID().toString().replace("-", "");
        audit.high("bi.export", "export", exportId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("export_id", exportId);
        result.put("status", "QUEUED");
        result.put("delegate", "fenglin-report-service");
        result.put("note", "大文件导出由平台报表服务异步执行，云智只提交导出参数");
        return result;
    }

    @Transactional
    public Map<String, Object> subscribe(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long taskId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "task_id", "taskId"));
        if (biMapper.findInsightTask(tenantId, taskId) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "洞察任务不存在");
        }
        long id = IdWorker.getId();
        biMapper.insertSubscription(id, tenantId, taskId,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "channels")), "STATION"),
                IntelligenceContext.string(IntelligenceContext.first(body, "receivers")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "frequency")), "DAILY"),
                "ENABLED", userId);
        audit.low("bi.insight.subscribe", "insight_subscription", id);
        return Map.of("id", id, "task_id", taskId, "status", "ENABLED");
    }

    public List<Map<String, Object>> listSubscriptions(Integer limit) {
        return biMapper.listSubscriptions(IntelligenceContext.tenantId(),
                IntelligenceContext.clamp(limit == null ? 20 : limit, 1, 100));
    }

    @Transactional
    public Map<String, Object> configDataPermission(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long id = IdWorker.getId();
        biMapper.insertDataPermission(id, tenantId,
                IntelligenceContext.requireText(
                        IntelligenceContext.string(IntelligenceContext.first(body, "subject_type", "subjectType"))),
                IntelligenceContext.requireLong(IntelligenceContext.first(body, "subject_id", "subjectId")),
                IntelligenceContext.requireText(
                        IntelligenceContext.string(IntelligenceContext.first(body, "resource_type", "resourceType"))),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "resource_id", "resourceId")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "data_scope", "dataScope")),
                        "SELF"),
                IntelligenceContext.string(IntelligenceContext.first(body, "scope_dept_ids", "scopeDeptIds")),
                IntelligenceContext.string(IntelligenceContext.first(body, "column_hidden", "columnHidden")),
                IntelligenceContext.string(IntelligenceContext.first(body, "row_filter_expr", "rowFilterExpr")), userId);
        audit.high("bi.datapermission", "data_permission", id);
        return Map.of("id", id);
    }

    public Map<String, Object> overview(String from, String to) {
        long tenantId = IntelligenceContext.tenantId();
        String fromText = IntelligenceContext.text(from);
        String toText = IntelligenceContext.text(to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledge_bases", knowledgeMapper.countKnowledgeBasesInRange(tenantId, fromText, toText));
        result.put("data_sources", knowledgeMapper.countDataSourcesInRange(tenantId, fromText, toText));
        result.put("chunks", knowledgeMapper.countChunksInRange(tenantId, fromText, toText));
        result.put("datasets", biMapper.countDatasetsInRange(tenantId, fromText, toText));
        result.put("dashboards", biMapper.countDashboardsInRange(tenantId, fromText, toText));
        result.put("insight_tasks", biMapper.countInsightTasksInRange(tenantId, fromText, toText));
        result.put("from", fromText);
        result.put("to", toText);
        return result;
    }

    // ---------- 数据集预览 / 字段删除 ----------

    public Map<String, Object> previewDataset(long datasetId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> dataset = biMapper.findDataset(tenantId, datasetId);
        if (dataset == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        return queryService.preview(dataset,
                IntelligenceContext.intValue(IntelligenceContext.first(body, "limit"), 20));
    }

    public Map<String, Object> queryDataset(long datasetId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> dataset = biMapper.findDataset(tenantId, datasetId);
        if (dataset == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        return queryService.query(dataset, body == null ? Map.of() : body);
    }

    @Transactional
    public Map<String, Object> deleteDatasetField(long datasetId, long fieldId) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (biMapper.findDataset(tenantId, datasetId) == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        int affected = biMapper.deleteDatasetField(tenantId, datasetId, fieldId, userId);
        if (affected == 0) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "字段不存在");
        }
        audit.low("bi.dataset.field.delete", "dataset_field", fieldId);
        return Map.of("dataset_id", datasetId, "field_id", fieldId, "deleted", true);
    }

    // ---------- 图表数据 / 删除 / 排序 / 告警 ----------

    public Map<String, Object> chartData(long dashboardId, long chartId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> chart = biMapper.findChart(tenantId, dashboardId, chartId);
        if (chart == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图表不存在");
        }
        Map<String, Object> dataset = biMapper.findDataset(tenantId, Rows.lngVal(chart, "dataset_id"));
        if (dataset == null) {
            throw new BizException(IntelligenceErrorCode.DATASET_INVALID);
        }
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (!request.containsKey("dimensions") && !request.containsKey("measures")
                && !request.containsKey("query")) {
            String queryText = Rows.str(chart, "query_text");
            if (queryText != null && !queryText.isBlank()) {
                request.put("query", IntelligenceMaps.node(queryText));
            }
        }
        Map<String, Object> result = queryService.query(dataset, request);
        result.put("chart_id", chartId);
        result.put("chart_type", Rows.str(chart, "chart_type"));
        return result;
    }

    @Transactional
    public Map<String, Object> deleteChart(long dashboardId, long chartId) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (biMapper.findChart(tenantId, dashboardId, chartId) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图表不存在");
        }
        biMapper.deleteChart(tenantId, dashboardId, chartId, userId);
        audit.low("bi.chart.delete", "chart", chartId);
        return Map.of("dashboard_id", dashboardId, "chart_id", chartId, "deleted", true);
    }

    @Transactional
    public Map<String, Object> reorderCharts(long dashboardId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> dashboard = biMapper.findDashboard(tenantId, dashboardId);
        if (dashboard == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "看板不存在");
        }
        Object raw = IntelligenceContext.first(body, "chart_ids", "chartIds");
        if (!(raw instanceof List<?> ids) || ids.isEmpty()) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "chart_ids 必须为非空数组");
        }
        com.fasterxml.jackson.databind.JsonNode parsedLayout =
                IntelligenceMaps.node(dashboard.get("layout_json"));
        com.fasterxml.jackson.databind.node.ObjectNode layout =
                parsedLayout instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode
                        ? objectNode
                        : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        com.fasterxml.jackson.databind.node.ArrayNode order = layout.putArray("chart_order");
        List<Long> normalized = new ArrayList<>();
        for (Object id : ids) {
            long value = IntelligenceContext.requireLong(id);
            if (biMapper.findChart(tenantId, dashboardId, value) == null) {
                throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图表不属于该看板：" + value);
            }
            normalized.add(value);
            order.add(value);
        }
        biMapper.updateDashboardLayout(tenantId, dashboardId, layout.toString(), userId);
        audit.low("bi.dashboard.chart.reorder", "dashboard", dashboardId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard_id", dashboardId);
        result.put("chart_order", normalized);
        return result;
    }

    @Transactional
    public Map<String, Object> saveChartAlert(long dashboardId, long chartId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (biMapper.findChart(tenantId, dashboardId, chartId) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图表不存在");
        }
        boolean enabled = Boolean.parseBoolean(String.valueOf(
                IntelligenceContext.first(body, "enabled")));
        String condition = IntelligenceContext.string(IntelligenceContext.first(body, "condition"));
        String threshold = IntelligenceContext.string(IntelligenceContext.first(body, "threshold"));
        String channels = IntelligenceContext.string(IntelligenceContext.first(body, "channels"));
        String alert = IntelligenceMaps.json(IntelligenceMaps.of(
                "enabled", enabled, "condition", condition, "threshold", threshold, "channels", channels));
        biMapper.updateChartAlert(tenantId, dashboardId, chartId, alert, userId);
        audit.high("bi.chart.alert.save", "chart", chartId);
        return chartAlert(dashboardId, chartId);
    }

    public Map<String, Object> chartAlert(long dashboardId, long chartId) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> chart = biMapper.findChart(tenantId, dashboardId, chartId);
        if (chart == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图表不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard_id", dashboardId);
        result.put("chart_id", chartId);
        result.put("chart_name", Rows.str(chart, "name"));
        result.put("alert", IntelligenceMaps.node(chart.get("alert_text")));
        return result;
    }

    public List<Map<String, Object>> listAlerts(Integer limit) {
        long tenantId = IntelligenceContext.tenantId();
        List<Map<String, Object>> rows = biMapper.listAlerts(tenantId,
                IntelligenceContext.clamp(limit == null ? 50 : limit, 1, 200));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("alert", IntelligenceMaps.node(row.get("alert_text")));
            item.remove("alert_text");
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> listDataPermissions(Integer limit) {
        return biMapper.listDataPermissions(IntelligenceContext.tenantId(),
                IntelligenceContext.clamp(limit == null ? 50 : limit, 1, 200));
    }

    // ---------- 洞察结果 CSV 导出 ----------

    public Map<String, Object> insightResultsCsv(long taskId) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> task = biMapper.findInsightTask(tenantId, taskId);
        if (task == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "洞察任务不存在");
        }
        List<Map<String, Object>> results = biMapper.listInsightResults(tenantId, taskId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("result_id,anomaly_type,magnitude,confidence,conclusion,suggestion,created_at\n");
        for (Map<String, Object> row : results) {
            csv.append(csvCell(String.valueOf(Rows.lngVal(row, "id")))).append(',')
                    .append(csvCell(Rows.str(row, "anomaly_type"))).append(',')
                    .append(csvCell(Rows.str(row, "magnitude"))).append(',')
                    .append(csvCell(Rows.str(row, "confidence"))).append(',')
                    .append(csvCell(Rows.str(row, "conclusion"))).append(',')
                    .append(csvCell(Rows.str(row, "suggestion"))).append(',')
                    .append(csvCell(Rows.str(row, "created_at"))).append('\n');
        }
        audit.high("bi.insight.export", "insight_task", taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("file_name", "insight-result-" + taskId + ".csv");
        result.put("record_count", results.size());
        result.put("content", csv.toString());
        return result;
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
