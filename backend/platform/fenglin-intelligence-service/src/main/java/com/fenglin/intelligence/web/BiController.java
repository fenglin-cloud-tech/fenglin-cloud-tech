package com.fenglin.intelligence.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.intelligence.service.BiService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * H3 BI/洞察接口（指标、数据集、看板、洞察任务、归因、NL2SQL、订阅）。
 */
@RestController
@RequestMapping("/api/intelligence/v1")
public class BiController {

    private final BiService service;

    public BiController(BiService service) {
        this.service = service;
    }

    @PostMapping("/admin/metrics")
    public ApiResponse<Map<String, Object>> createMetric(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:bi:metric:manage");
        return ApiResponse.success(service.createMetric(body));
    }

    @GetMapping("/metrics")
    public ApiResponse<PageResult<Map<String, Object>>> listMetrics(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listMetrics(keyword, page, size));
    }

    @PostMapping("/admin/datasets")
    public ApiResponse<Map<String, Object>> createDataset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:dataset:manage");
        return ApiResponse.success(service.createDataset(body));
    }

    @GetMapping("/datasets")
    public ApiResponse<PageResult<Map<String, Object>>> listDatasets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listDatasets(page, size));
    }

    @PostMapping("/admin/datasets/{id}/fields")
    public ApiResponse<Map<String, Object>> addField(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:dataset:manage");
        return ApiResponse.success(service.addField(id, body));
    }

    @GetMapping("/datasets/{id}/fields")
    public ApiResponse<List<Map<String, Object>>> listFields(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listFields(id));
    }

    @PostMapping("/admin/dashboards")
    public ApiResponse<Map<String, Object>> createDashboard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:dashboard:manage");
        return ApiResponse.success(service.createDashboard(body));
    }

    @GetMapping("/dashboards")
    public ApiResponse<PageResult<Map<String, Object>>> listDashboards(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listDashboards(page, size));
    }

    @PostMapping("/admin/dashboards/{id}/charts")
    public ApiResponse<Map<String, Object>> createChart(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:chart:manage");
        return ApiResponse.success(service.createChart(id, body));
    }

    @GetMapping("/dashboards/{id}")
    public ApiResponse<Map<String, Object>> dashboardDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.dashboardDetail(id));
    }

    @PostMapping("/admin/insight-tasks")
    public ApiResponse<Map<String, Object>> createInsightTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:insight:run");
        return ApiResponse.success(service.createInsightTask(body));
    }

    @GetMapping("/insight-tasks")
    public ApiResponse<PageResult<Map<String, Object>>> listInsightTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listInsightTasks(page, size));
    }

    @PostMapping("/admin/insight-tasks/{id}/run")
    public ApiResponse<Map<String, Object>> runInsightTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:bi:insight:run");
        return ApiResponse.success(service.runInsightTask(id));
    }

    @GetMapping("/insight-tasks/{id}/results")
    public ApiResponse<List<Map<String, Object>>> listInsightResults(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listInsightResults(id));
    }

    @PostMapping("/analytics/attribution")
    public ApiResponse<Map<String, Object>> attribution(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:attribution:run");
        return ApiResponse.success(service.attribution(body));
    }

    @GetMapping("/analytics/attributions")
    public ApiResponse<List<Map<String, Object>>> listAttributions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "metric_id") long metricId,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listAttributions(metricId, limit));
    }

    @PostMapping("/analytics/nl2sql")
    public ApiResponse<Map<String, Object>> nl2sql(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:nl2sql:use");
        return ApiResponse.success(service.nl2sql(body));
    }

    @PostMapping("/analytics/export")
    public ApiResponse<Map<String, Object>> export(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:export");
        return ApiResponse.success(service.export(body == null ? Map.of() : body));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<Map<String, Object>> subscribe(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:insight:subscribe");
        return ApiResponse.success(service.subscribe(body));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<Map<String, Object>>> listSubscriptions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.listSubscriptions(limit));
    }

    @PutMapping("/admin/data-permissions")
    public ApiResponse<Map<String, Object>> configDataPermission(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:bi:dataperm:config");
        return ApiResponse.success(service.configDataPermission(body));
    }

    @GetMapping("/report/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.overview(from, to));
    }

    @PostMapping("/datasets/{id}/preview")
    public ApiResponse<Map<String, Object>> previewDataset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.previewDataset(id, body == null ? Map.of() : body));
    }

    @PostMapping("/datasets/{id}/query")
    public ApiResponse<Map<String, Object>> queryDataset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.queryDataset(id, body == null ? Map.of() : body));
    }

    @DeleteMapping("/admin/datasets/{id}/fields/{fieldId}")
    public ApiResponse<Map<String, Object>> deleteDatasetField(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long fieldId) {
        PermissionGuard.require(permissions, "ai:bi:dataset:manage");
        return ApiResponse.success(service.deleteDatasetField(id, fieldId));
    }

    @PostMapping("/dashboards/{id}/charts/{chartId}/data")
    public ApiResponse<Map<String, Object>> chartData(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long chartId,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.chartData(id, chartId, body == null ? Map.of() : body));
    }

    @DeleteMapping("/admin/dashboards/{id}/charts/{chartId}")
    public ApiResponse<Map<String, Object>> deleteChart(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long chartId) {
        PermissionGuard.require(permissions, "ai:bi:chart:manage");
        return ApiResponse.success(service.deleteChart(id, chartId));
    }

    @PutMapping("/admin/dashboards/{id}/charts/order")
    public ApiResponse<Map<String, Object>> reorderCharts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:chart:manage");
        return ApiResponse.success(service.reorderCharts(id, body));
    }

    @PutMapping("/admin/dashboards/{id}/charts/{chartId}/alert")
    public ApiResponse<Map<String, Object>> saveChartAlert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long chartId,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:bi:chart:manage");
        return ApiResponse.success(service.saveChartAlert(id, chartId, body));
    }

    @GetMapping("/admin/dashboards/{id}/charts/{chartId}/alert")
    public ApiResponse<Map<String, Object>> chartAlert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long chartId) {
        PermissionGuard.require(permissions, "ai:bi:view");
        return ApiResponse.success(service.chartAlert(id, chartId));
    }

    @GetMapping("/admin/ai/alerts")
    public ApiResponse<List<Map<String, Object>>> listAlerts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "tenant:ai:bi:dataperm:config");
        return ApiResponse.success(service.listAlerts(limit));
    }

    @GetMapping("/admin/data-permissions")
    public ApiResponse<List<Map<String, Object>>> listDataPermissions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "tenant:ai:bi:dataperm:config");
        return ApiResponse.success(service.listDataPermissions(limit));
    }

    @GetMapping("/insight-tasks/{id}/results/export")
    public org.springframework.http.ResponseEntity<byte[]> exportInsightResults(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:bi:export");
        Map<String, Object> export = service.insightResultsCsv(id);
        byte[] bytes = String.valueOf(export.get("content")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + export.get("file_name") + "\"")
                .contentType(new org.springframework.http.MediaType("text", "csv",
                        java.nio.charset.StandardCharsets.UTF_8))
                .body(bytes);
    }
}
