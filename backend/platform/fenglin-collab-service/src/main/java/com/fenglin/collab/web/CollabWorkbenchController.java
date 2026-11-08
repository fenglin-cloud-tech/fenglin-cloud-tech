package com.fenglin.collab.web;

import com.fenglin.collab.service.WorkbenchService;
import com.fenglin.collab.web.dto.CollabDtos.LayoutSaveRequest;
import com.fenglin.collab.web.dto.CollabDtos.LayoutView;
import com.fenglin.collab.web.dto.CollabDtos.NotificationReadResult;
import com.fenglin.collab.web.dto.CollabDtos.NotificationView;
import com.fenglin.collab.web.dto.CollabDtos.TodoView;
import com.fenglin.collab.web.dto.CollabDtos.WidgetCreateRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
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
 * 工作台 / 待办 / 通知接口（API-WB-001 ~ 006）。
 */
@RestController
@RequestMapping("/api/collab/v1")
public class CollabWorkbenchController {

    private static final String PERSONAL = "biz:collab:workbench:personal";
    private static final String TEMPLATE_ADMIN = "tenant:collab:workbench:template:manage";

    private final WorkbenchService workbenchService;

    public CollabWorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/workbench/layout")
    public ApiResponse<LayoutView> layout(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.layout());
    }

    @PutMapping("/workbench/layout")
    public ApiResponse<LayoutView> saveLayout(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody LayoutSaveRequest request) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.saveLayout(request));
    }

    @GetMapping("/workbench/widgets")
    public ApiResponse<List<Map<String, Object>>> widgets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.widgets());
    }

    @PostMapping("/admin/workbench/widgets")
    public ApiResponse<Void> createWidget(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody WidgetCreateRequest request) {
        PermissionGuard.require(permissions, TEMPLATE_ADMIN);
        workbenchService.createWidget(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/workbench/todos")
    public ApiResponse<PageResult<TodoView>> todos(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.todos(status, page, size));
    }

    @PostMapping("/workbench/todos/{todoId}/done")
    public ApiResponse<Void> completeTodo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("todoId") long todoId) {
        PermissionGuard.require(permissions, PERSONAL);
        workbenchService.completeTodo(todoId);
        return ApiResponse.success(null);
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResult<NotificationView>> notifications(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.notifications(unreadOnly, page, size));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<NotificationReadResult> readNotification(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("notificationId") long notificationId) {
        PermissionGuard.require(permissions, PERSONAL);
        return ApiResponse.success(workbenchService.markNotificationRead(notificationId));
    }
}
