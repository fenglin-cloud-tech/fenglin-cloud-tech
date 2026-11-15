package com.fenglin.mdm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.mdm.service.PartnerService;
import com.fenglin.mdm.support.MdmContext;
import com.fenglin.mdm.web.dto.MdmDtos.AliasView;
import com.fenglin.mdm.web.dto.MdmDtos.MergeRequest;
import com.fenglin.mdm.web.dto.MdmDtos.MergeView;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerCreateRequest;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerCreateResult;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerView;
import com.fenglin.mdm.web.dto.MdmDtos.ResolveView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一往来单位主数据接口。
 */
@RestController
@RequestMapping("/api/mdm/v1")
public class MdmPartnerController {

    private static final String VIEW = "platform:mdm:partner:view";
    private static final String MANAGE = "platform:mdm:partner:manage";
    private static final String MERGE = "platform:mdm:partner:merge";

    private final PartnerService partnerService;

    public MdmPartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping("/partners")
    public ApiResponse<List<PartnerView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(partnerService.list(keyword, status, MdmContext.size(size),
                MdmContext.offset(page, size)));
    }

    @PostMapping("/partners")
    public ApiResponse<PartnerCreateResult> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PartnerCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(partnerService.create(request));
    }

    @GetMapping("/partners/{id}")
    public ApiResponse<PartnerView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(partnerService.get(id));
    }

    @GetMapping("/partners/{id}/resolve")
    public ApiResponse<ResolveView> resolve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(partnerService.resolve(id));
    }

    @GetMapping("/partners/{id}/aliases")
    public ApiResponse<List<AliasView>> aliases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(partnerService.aliases(id));
    }

    @PostMapping("/partners/{id}/disable")
    public ApiResponse<PartnerView> disable(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(partnerService.disable(id));
    }

    @PostMapping("/partners/{id}/enable")
    public ApiResponse<PartnerView> enable(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(partnerService.enable(id));
    }

    @PostMapping("/partners/merge")
    public ApiResponse<MergeView> merge(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody MergeRequest request) {
        PermissionGuard.require(permissions, MERGE);
        return ApiResponse.success(partnerService.merge(request));
    }

    @PostMapping("/partners/merges/{mergeId}/revoke")
    public ApiResponse<MergeView> revoke(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("mergeId") long mergeId) {
        PermissionGuard.require(permissions, MERGE);
        return ApiResponse.success(partnerService.revoke(mergeId));
    }
}
