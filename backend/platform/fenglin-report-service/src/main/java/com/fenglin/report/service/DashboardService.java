package com.fenglin.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.mapper.ReportDefMapper;
import com.fenglin.report.web.dto.ReportDtos.ChartData;
import com.fenglin.report.web.dto.ReportDtos.DashboardData;
import com.fenglin.report.web.dto.ReportDtos.DashboardQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryResult;
import com.fenglin.report.web.dto.ReportDtos.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HUB-API-1104 看板数据。
 * hub_report_def.config 结构（契约缺口兜底，文档未定义）：
 * {"charts":[{"chartId":1,"datasetId":2,"dimensions":["region"],"measures":["gmv"],
 *            "filters":[{"field":"month","op":"EQ","value":"$month"}]}]}
 * 单个图表查询失败时降级返回，error_code 固定 0000008（依赖服务不可用），看板整体 degraded=true。
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ReportDefMapper reportDefMapper;
    private final DatasetQueryService datasetQueryService;
    private final ObjectMapper objectMapper;

    public DashboardService(ReportDefMapper reportDefMapper,
                            DatasetQueryService datasetQueryService,
                            ObjectMapper objectMapper) {
        this.reportDefMapper = reportDefMapper;
        this.datasetQueryService = datasetQueryService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardData data(long dashboardId, DashboardQueryRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> definition = reportDefMapper.findById(tenantId, dashboardId);
        if (definition == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "看板不存在: " + dashboardId);
        }
        if (!"DASHBOARD".equals(definition.get("report_type"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "该资源不是看板: " + dashboardId);
        }
        JsonNode charts = parse(definition.get("config_text")).path("charts");
        if (!charts.isArray() || charts.isEmpty()) {
            throw new BizException(CommonErrorCode.CONFLICT, "看板未配置图表: " + dashboardId);
        }
        List<Long> requested = request == null ? null : request.chartIds();
        Map<String, Object> params = request == null ? Map.of() : request.params();

        List<ChartData> results = new ArrayList<>();
        boolean degraded = false;
        for (JsonNode chart : charts) {
            long chartId = chart.path("chartId").asLong();
            if (requested != null && !requested.isEmpty() && !requested.contains(chartId)) {
                continue;
            }
            try {
                DatasetQueryResult result = datasetQueryService.query(
                        chart.path("datasetId").asLong(), toQuery(chart, params));
                results.add(new ChartData(chartId, result.columns(), result.rows(), false, null));
            } catch (RuntimeException ex) {
                degraded = true;
                log.warn("看板图表查询降级：dashboardId={} chartId={}", dashboardId, chartId, ex);
                results.add(new ChartData(chartId, List.of(), List.of(), true, "0000008"));
            }
        }
        return new DashboardData(results, degraded);
    }

    private DatasetQueryRequest toQuery(JsonNode chart, Map<String, Object> params) {
        return new DatasetQueryRequest(
                textList(chart.path("dimensions")),
                textList(chart.path("measures")),
                filters(chart.path("filters"), params),
                chart.hasNonNull("limit") ? chart.path("limit").asInt() : null);
    }

    private List<Filter> filters(JsonNode filters, Map<String, Object> params) {
        if (!filters.isArray()) {
            return List.of();
        }
        List<Filter> result = new ArrayList<>();
        for (JsonNode node : filters) {
            String field = node.path("field").asText(null);
            JsonNode valueNode = node.path("value");
            Object value;
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                value = params.get(field);
            } else if (valueNode.isTextual() && valueNode.asText().startsWith("$")) {
                value = params.get(valueNode.asText().substring(1));
            } else if (valueNode.isArray()) {
                List<Object> values = new ArrayList<>();
                valueNode.forEach(item -> values.add(item.isNumber() ? item.numberValue() : item.asText()));
                value = values;
            } else {
                value = valueNode.isNumber() ? valueNode.numberValue() : valueNode.asText();
            }
            result.add(new Filter(field, node.path("op").asText("EQ"), value));
        }
        return result;
    }

    private List<String> textList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private JsonNode parse(Object configText) {
        if (configText == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree((String) configText);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.CONFLICT, "看板配置不是合法 JSON");
        }
    }
}
