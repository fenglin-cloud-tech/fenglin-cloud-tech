package com.fenglin.workflow.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.security.WorkflowPermissions;
import com.fenglin.workflow.service.WorkflowDefinitionService;
import com.fenglin.workflow.service.WorkflowMonitorService;
import com.fenglin.workflow.web.dto.DefinitionView;
import com.fenglin.workflow.web.dto.DeployDefinitionRequest;
import com.fenglin.workflow.web.dto.MonitorStatsResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class WorkflowAdminController {

    private static final String DEF_PERMISSION = WorkflowPermissions.DEFINITION_DEPLOY;
    private static final String MONITOR_PERMISSION = "tenant:workflow:monitor:view";

    private final WorkflowDefinitionService definitionService;
    private final WorkflowMonitorService monitorService;

    public WorkflowAdminController(WorkflowDefinitionService definitionService,
                                   WorkflowMonitorService monitorService) {
        this.definitionService = definitionService;
        this.monitorService = monitorService;
    }

    @PostMapping("/api/hub/v1/admin/workflow/definitions")
    public ApiResponse<DefinitionView> deploy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DeployDefinitionRequest request) {
        PermissionGuard.require(permissions, DEF_PERMISSION);
        return ApiResponse.success(definitionService.deploy(request));
    }

    @GetMapping("/api/hub/v1/admin/workflow/definitions")
    public ApiResponse<PageResult<DefinitionView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, DEF_PERMISSION);
        return ApiResponse.success(definitionService.list(category, status, page, size));
    }

    @GetMapping("/api/hub/v1/admin/workflow/monitor/stats")
    public ApiResponse<MonitorStatsResult> stats(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PermissionGuard.require(permissions, MONITOR_PERMISSION);
        return ApiResponse.success(monitorService.stats(from, to));
    }
}
