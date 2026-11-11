package com.fenglin.collab.web;

import com.fenglin.collab.service.ApprovalService;
import com.fenglin.collab.web.dto.CollabDtos.AddSignRequest;
import com.fenglin.collab.web.dto.CollabDtos.InstanceCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.InstanceView;
import com.fenglin.collab.web.dto.CollabDtos.RejectRequest;
import com.fenglin.collab.web.dto.CollabDtos.TaskActionRequest;
import com.fenglin.collab.web.dto.CollabDtos.TaskResult;
import com.fenglin.collab.web.dto.CollabDtos.TemplateView;
import com.fenglin.collab.web.dto.CollabDtos.TransferRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审批接口（API-APV-001 ~ 010）。
 */
@RestController
@RequestMapping("/api/collab/v1/approvals")
public class CollabApprovalController {

    private static final String SUBMIT = "biz:collab:approval:submit";
    private static final String HANDLE = "biz:collab:approval:handle";
    private static final String READ = "biz:collab:approval:read";

    private final ApprovalService approvalService;

    public CollabApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/templates")
    public ApiResponse<List<TemplateView>> templates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(approvalService.templates());
    }

    @PostMapping("/instances")
    public ApiResponse<InstanceView> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody InstanceCreateRequest request) {
        PermissionGuard.require(permissions, SUBMIT);
        return ApiResponse.success(approvalService.submit(request, permissions));
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<InstanceView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("instanceId") String instanceId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(approvalService.detail(instanceId));
    }

    @PostMapping("/instances/{instanceId}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("instanceId") String instanceId) {
        PermissionGuard.require(permissions, SUBMIT);
        approvalService.cancel(instanceId);
        return ApiResponse.success(null);
    }

    @GetMapping("/instances/my")
    public ApiResponse<List<Map<String, Object>>> myInstances(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(approvalService.myInstances(page, size));
    }

    /**
     * 缺口补齐 API-APV-009：我的待办审批列表。
     */
    @GetMapping("/tasks/my")
    public ApiResponse<List<Map<String, Object>>> myTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(approvalService.myTasks());
    }

    /** COL-01：我的已办（按 APPROVE/REJECT/TRANSFER 过滤），撤回后的实例状态仍可在“我发起的”里查到。 */
    @GetMapping("/tasks/done")
    public ApiResponse<Map<String, Object>> doneTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(approvalService.doneTasks(action, page, size));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<TaskResult> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("taskId") long taskId,
            @RequestBody(required = false) TaskActionRequest request) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(approvalService.approve(taskId, request == null ? null : request.comment()));
    }

    @PostMapping("/tasks/{taskId}/reject")
    public ApiResponse<TaskResult> reject(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("taskId") long taskId,
            @RequestBody(required = false) RejectRequest request) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(approvalService.reject(taskId, request == null ? null : request.comment()));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    public ApiResponse<Void> transfer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("taskId") long taskId,
            @RequestBody TransferRequest request) {
        PermissionGuard.require(permissions, HANDLE);
        approvalService.transfer(taskId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    public ApiResponse<Void> addSign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("taskId") long taskId,
            @RequestBody AddSignRequest request) {
        PermissionGuard.require(permissions, HANDLE);
        approvalService.addSign(taskId, request);
        return ApiResponse.success(null);
    }
}
