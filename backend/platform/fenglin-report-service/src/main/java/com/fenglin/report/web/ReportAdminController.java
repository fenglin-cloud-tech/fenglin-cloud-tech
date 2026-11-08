package com.fenglin.report.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.service.DashboardService;
import com.fenglin.report.service.DatasetQueryService;
import com.fenglin.report.service.MetricService;
import com.fenglin.report.web.dto.ReportDtos.DashboardView;
import com.fenglin.report.web.dto.ReportDtos.DatasetView;
import com.fenglin.report.web.dto.ReportDtos.MetricCreateRequest;
import com.fenglin.report.web.dto.ReportDtos.MetricView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportAdminController {

    private static final String METRIC_PERMISSION = "tenant:report:metric:manage";

    private final MetricService metricService;
    private final DatasetQueryService datasetQueryService;
    private final DashboardService dashboardService;

    public ReportAdminController(MetricService metricService,
                                 DatasetQueryService datasetQueryService,
                                 DashboardService dashboardService) {
        this.metricService = metricService;
        this.datasetQueryService = datasetQueryService;
        this.dashboardService = dashboardService;
    }

    /** HUB-API-1101 指标创建。 */
    @PostMapping("/api/hub/v1/admin/metrics")
    public ApiResponse<MetricView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody MetricCreateRequest request) {
        PermissionGuard.require(permissions, METRIC_PERMISSION);
        return ApiResponse.success(metricService.create(request, operator(userId)));
    }

    /** HUB-API-1102 指标列表/血缘。 */
    @GetMapping("/api/hub/v1/admin/metrics")
    public ApiResponse<PageResult<MetricView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, METRIC_PERMISSION);
        return ApiResponse.success(metricService.list(category, status, page, size));
    }

    /** 扩展：数据集列表（总纲未定义管理接口，复用指标管理权限）。 */
    @GetMapping("/api/hub/v1/admin/report/datasets")
    public ApiResponse<PageResult<DatasetView>> listDatasets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, METRIC_PERMISSION);
        return ApiResponse.success(datasetQueryService.listDatasets(status, page, size));
    }

    /** 扩展：报表/看板定义列表（总纲未定义管理接口）。 */
    @GetMapping("/api/hub/v1/admin/report/dashboards")
    public ApiResponse<PageResult<DashboardView>> listDashboards(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, METRIC_PERMISSION);
        return ApiResponse.success(dashboardService.listDashboards(reportType, status, page, size));
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
