package com.fenglin.workflow.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.workflow.service.WorkflowInstanceService;
import com.fenglin.workflow.service.WorkflowTaskService;
import com.fenglin.workflow.web.dto.HandleTaskRequest;
import com.fenglin.workflow.web.dto.HandleTaskResult;
import com.fenglin.workflow.web.dto.InstanceDetailResult;
import com.fenglin.workflow.web.dto.PendingTaskView;
import com.fenglin.workflow.web.dto.StartInstanceRequest;
import com.fenglin.workflow.web.dto.StartInstanceResult;
import com.fenglin.workflow.web.dto.TerminateRequest;
import com.fenglin.workflow.web.dto.TerminateResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkflowController {

    private static final String START_PERMISSION = "biz:workflow:instance:start";
    private static final String VIEW_PERMISSION = "biz:workflow:instance:view";
    private static final String HANDLE_PERMISSION = "biz:workflow:task:handle";

    private final WorkflowInstanceService instanceService;
    private final WorkflowTaskService taskService;

    public WorkflowController(WorkflowInstanceService instanceService, WorkflowTaskService taskService) {
        this.instanceService = instanceService;
        this.taskService = taskService;
    }

    @PostMapping("/api/hub/v1/workflow/instances")
    public ApiResponse<StartInstanceResult> start(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody StartInstanceRequest request) {
        PermissionGuard.require(permissions, START_PERMISSION);
        return ApiResponse.success(instanceService.start(request));
    }

    @GetMapping("/api/hub/v1/workflow/pending")
    public ApiResponse<PageResult<PendingTaskView>> pending(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "urgent", required = false) Boolean urgent,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW_PERMISSION);
        return ApiResponse.success(instanceService.pending(domain, urgent, page, size));
    }

    @PostMapping("/api/hub/v1/workflow/tasks/{id}:handle")
    public ApiResponse<HandleTaskResult> handle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody HandleTaskRequest request) {
        PermissionGuard.require(permissions, HANDLE_PERMISSION);
        return ApiResponse.success(taskService.handle(id, request));
    }

    @GetMapping("/api/hub/v1/workflow/instances/{id}")
    public ApiResponse<InstanceDetailResult> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW_PERMISSION);
        return ApiResponse.success(instanceService.detail(id));
    }

    @PostMapping("/api/hub/v1/workflow/instances/{id}:terminate")
    public ApiResponse<TerminateResult> terminate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) TerminateRequest request) {
        PermissionGuard.require(permissions, START_PERMISSION);
        return ApiResponse.success(instanceService.terminate(id, request == null ? null : request.reason()));
    }
}
