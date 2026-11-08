package com.fenglin.search.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.search.service.SearchAnalyticsService;
import com.fenglin.search.service.SearchIndexTaskService;
import com.fenglin.search.web.dto.AnalyticsResponse;
import com.fenglin.search.web.dto.IndexTaskRequest;
import com.fenglin.search.web.dto.IndexTaskView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SearchAdminController {

    private static final String MANAGE_PERMISSION = "platform:search:index:manage";

    private final SearchIndexTaskService indexTaskService;
    private final SearchAnalyticsService analyticsService;

    public SearchAdminController(SearchIndexTaskService indexTaskService,
                                 SearchAnalyticsService analyticsService) {
        this.indexTaskService = indexTaskService;
        this.analyticsService = analyticsService;
    }

    /**
     * HUB-API-903 索引任务创建：同租户同索引同类型的 PENDING/RUNNING 任务直接复用（强制幂等兜底）。
     */
    @PostMapping("/api/hub/v1/admin/search/index-tasks")
    public ApiResponse<IndexTaskView> createIndexTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody IndexTaskRequest request) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        Long operatorId = userId != null ? userId
                : (TenantContext.get() == null ? null : TenantContext.get().userId());
        return ApiResponse.success(indexTaskService.create(request, operatorId));
    }

    /**
     * 索引任务查询（契约缺口：903 仅定义创建，补充查询用于获取任务状态）。
     */
    @GetMapping("/api/hub/v1/admin/search/index-tasks/{taskId}")
    public ApiResponse<IndexTaskView> getIndexTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long taskId) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        return ApiResponse.success(indexTaskService.get(taskId));
    }

    /**
     * HUB-API-904 检索分析报表。
     */
    @GetMapping("/api/hub/v1/admin/search/analytics")
    public ApiResponse<AnalyticsResponse> analytics(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(defaultValue = "HOT") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        return ApiResponse.success(analyticsService.report(type, from, to));
    }
}
