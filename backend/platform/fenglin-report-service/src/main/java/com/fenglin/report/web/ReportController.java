package com.fenglin.report.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.service.DashboardService;
import com.fenglin.report.service.DatasetQueryService;
import com.fenglin.report.service.ReportExportService;
import com.fenglin.report.web.dto.ReportDtos.DashboardData;
import com.fenglin.report.web.dto.ReportDtos.DashboardQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryResult;
import com.fenglin.report.web.dto.ReportDtos.ExportRequest;
import com.fenglin.report.web.dto.ReportDtos.ExportResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private static final String VIEW_PERMISSION = "biz:report:view";
    private static final String EXPORT_PERMISSION = "biz:report:export";

    private final DatasetQueryService datasetQueryService;
    private final DashboardService dashboardService;
    private final ReportExportService exportService;

    public ReportController(DatasetQueryService datasetQueryService,
                            DashboardService dashboardService,
                            ReportExportService exportService) {
        this.datasetQueryService = datasetQueryService;
        this.dashboardService = dashboardService;
        this.exportService = exportService;
    }

    /** HUB-API-1103 数据集查询。 */
    @PostMapping("/api/hub/v1/report/datasets/{id}/query")
    public ApiResponse<DatasetQueryResult> query(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) DatasetQueryRequest request) {
        PermissionGuard.require(permissions, VIEW_PERMISSION);
        return ApiResponse.success(datasetQueryService.query(id,
                request == null ? new DatasetQueryRequest(null, null, null, null) : request));
    }

    /** HUB-API-1104 看板数据。 */
    @PostMapping("/api/hub/v1/report/dashboards/{id}/data")
    public ApiResponse<DashboardData> dashboard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) DashboardQueryRequest request) {
        PermissionGuard.require(permissions, VIEW_PERMISSION);
        return ApiResponse.success(dashboardService.data(id,
                request == null ? new DashboardQueryRequest(null, null) : request));
    }

    /** HUB-API-1105 报表导出。 */
    @PostMapping("/api/hub/v1/report:export")
    public ApiResponse<ExportResult> export(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody ExportRequest request) {
        PermissionGuard.require(permissions, EXPORT_PERMISSION);
        Long operator = userId;
        if (operator == null) {
            TenantContext.Snapshot context = TenantContext.get();
            operator = context == null ? null : context.userId();
        }
        return ApiResponse.success(exportService.export(request, operator));
    }
}
