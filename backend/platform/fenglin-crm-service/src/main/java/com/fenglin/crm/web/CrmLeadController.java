package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.LeadService;
import com.fenglin.crm.web.dto.CrmDtos.AssignRequest;
import com.fenglin.crm.web.dto.CrmDtos.ConvertRequest;
import com.fenglin.crm.web.dto.CrmDtos.ConvertResult;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckRequest;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckResult;
import com.fenglin.crm.web.dto.CrmDtos.LeadCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.LeadDetailView;
import com.fenglin.crm.web.dto.CrmDtos.LeadView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线索接口（01_丰麟云销_05 CRM-API-001 ~ 006 / 026）。
 */
@RestController
@RequestMapping("/api/crm/v1/leads")
public class CrmLeadController {

    private final LeadService leadService;

    public CrmLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    public ApiResponse<LeadView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody LeadCreateRequest request) {
        PermissionGuard.require(permissions, "crm:lead:create");
        return ApiResponse.success(leadService.create(request));
    }

    @PostMapping("/check-duplicate")
    public ApiResponse<DuplicateCheckResult> checkDuplicate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DuplicateCheckRequest request) {
        PermissionGuard.require(permissions, "crm:lead:view");
        return ApiResponse.success(leadService.checkDuplicate(request));
    }

    @GetMapping
    public ApiResponse<PageResult<LeadView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:lead:view");
        return ApiResponse.success(leadService.list(status, ownerId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<LeadDetailView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:lead:view");
        return ApiResponse.success(leadService.detail(id));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<LeadView> assign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody AssignRequest request) {
        PermissionGuard.require(permissions, "crm:lead:assign");
        return ApiResponse.success(leadService.assign(id, request));
    }

    @PostMapping("/{id}/convert")
    public ApiResponse<ConvertResult> convert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) ConvertRequest request) {
        PermissionGuard.require(permissions, "crm:lead:transfer");
        return ApiResponse.success(leadService.convert(id, request == null ? new ConvertRequest(
                true, true, false, null, null, null) : request));
    }
}
