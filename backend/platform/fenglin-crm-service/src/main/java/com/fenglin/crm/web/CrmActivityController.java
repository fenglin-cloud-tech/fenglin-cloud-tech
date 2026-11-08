package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.ActivityService;
import com.fenglin.crm.web.dto.CrmDtos.ActivityCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ActivityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 跟进活动接口（01_丰麟云销_05 CRM-API-033）。
 */
@RestController
public class CrmActivityController {

    private final ActivityService activityService;

    public CrmActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/api/crm/v1/activities")
    public ApiResponse<ActivityView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ActivityCreateRequest request) {
        PermissionGuard.require(permissions, "crm:activity:create");
        return ApiResponse.success(activityService.create(request));
    }

    @GetMapping("/api/crm/v1/activities")
    public ApiResponse<List<ActivityView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") long bizId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, "crm:activity:view");
        return ApiResponse.success(activityService.list(bizType, bizId, limit));
    }
}
