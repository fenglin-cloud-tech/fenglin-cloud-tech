package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.service.OpportunityService;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityView;
import com.fenglin.crm.web.dto.CrmDtos.PipelineView;
import com.fenglin.crm.web.dto.CrmDtos.StageChangeRequest;
import com.fenglin.crm.web.dto.CrmDtos.StageLogView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商机接口（01_丰麟云销_05 CRM-API-020 ~ 024 / 041）。
 */
@RestController
public class CrmOpportunityController {

    private final OpportunityService opportunityService;

    public CrmOpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @PostMapping("/api/crm/v1/opportunities")
    public ApiResponse<OpportunityView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody OpportunityCreateRequest request) {
        PermissionGuard.require(permissions, "crm:opportunity:create");
        return ApiResponse.success(opportunityService.create(request));
    }

    @GetMapping("/api/crm/v1/opportunities")
    public ApiResponse<PageResult<OpportunityView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:opportunity:view");
        return ApiResponse.success(opportunityService.list(stage, status, customerId, ownerId, page, size));
    }

    @GetMapping("/api/crm/v1/opportunities/pipeline")
    public ApiResponse<PipelineView> pipeline(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "crm:report:sales");
        return ApiResponse.success(opportunityService.pipeline());
    }

    /** 员工自助：只返回 owner_id = 当前登录用户的商机。 */
    @GetMapping("/api/crm/v1/opportunities/my")
    public ApiResponse<PageResult<OpportunityView>> myOpportunities(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:opportunity:self:view");
        return ApiResponse.success(opportunityService.list(stage, status, customerId, CrmContext.userId(), page, size));
    }

    /** 员工自助：仅允许读取本人的商机详情。 */
    @GetMapping("/api/crm/v1/opportunities/my/{id}")
    public ApiResponse<OpportunityView> myOpportunity(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:opportunity:self:view");
        return ApiResponse.success(opportunityService.detail(id, CrmContext.userId()));
    }

    /** 员工自助：仅允许读取本人商机的阶段记录。 */
    @GetMapping("/api/crm/v1/opportunities/my/{id}/stage-logs")
    public ApiResponse<List<StageLogView>> myStageLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:opportunity:self:view");
        return ApiResponse.success(opportunityService.stageLogs(id, CrmContext.userId()));
    }

    @GetMapping("/api/crm/v1/opportunities/{id}")
    public ApiResponse<OpportunityView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:opportunity:view");
        return ApiResponse.success(opportunityService.detail(id));
    }

    @GetMapping("/api/crm/v1/opportunities/{id}/stage-logs")
    public ApiResponse<List<StageLogView>> stageLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:opportunity:view");
        return ApiResponse.success(opportunityService.stageLogs(id));
    }

    @PatchMapping("/api/crm/v1/opportunities/{id}/stage")
    public ApiResponse<OpportunityView> changeStage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody StageChangeRequest request) {
        PermissionGuard.require(permissions, "crm:opportunity:manage");
        return ApiResponse.success(opportunityService.changeStage(id, request));
    }
}
