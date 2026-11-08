package com.fenglin.report.web.dto;

import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record MetricCreateRequest(String code, String name, String metricType, String formula,
                                      String unit, String grain, List<String> dimensions,
                                      Map<String, Object> lineage) {
    }

    public record MetricView(long id, String code, String name, String metricType, String formula,
                             String unit, String grain, List<String> dimensions, Object lineage,
                             String status) {
    }

    public record Filter(String field, String op, Object value) {
    }

    public record DatasetQueryRequest(List<String> dimensions, List<String> measures,
                                      List<Filter> filters, Integer limit) {
    }

    public record DatasetQueryResult(List<String> columns, List<List<Object>> rows) {
    }

    /** 扩展：数据集列表（总纲 C-10 补表后未定义管理接口）。 */
    public record DatasetView(long id, String code, String name, String description,
                              long fieldCount, String status) {
    }

    /** 扩展：报表/看板定义列表（总纲未定义）。 */
    public record DashboardView(long id, String code, String name, String reportType,
                                Long datasetId, String status) {
    }

    public record DashboardQueryRequest(List<Long> chartIds, Map<String, Object> params) {
    }

    public record ChartData(long chartId, List<String> columns, List<List<Object>> rows,
                            boolean degraded, String errorCode) {
    }

    public record DashboardData(List<ChartData> charts, boolean degraded) {
    }

    public record ExportRequest(Long reportId, String format) {
    }

    public record ExportResult(String fileId, String downloadUrl, boolean watermark, String format) {
    }
}
