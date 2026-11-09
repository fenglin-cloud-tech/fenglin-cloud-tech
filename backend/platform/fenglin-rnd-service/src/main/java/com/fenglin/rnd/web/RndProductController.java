package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.ProductService;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.web.dto.RndDtos.MemberCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MemberUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MemberView;
import com.fenglin.rnd.web.dto.RndDtos.ProductCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ProductUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ProductView;
import com.fenglin.rnd.web.dto.RndDtos.StatusView;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/**
 * E1 产品与成员接口。
 */
@RestController
@RequestMapping("/api/rnd/v1/products")
public class RndProductController {

    private static final String MANAGE = "tenant:devops:product:manage";
    private static final String MEMBER_MANAGE = "tenant:devops:member:manage";
    private static final String VIEW = "biz:devops:product:view";
    private static final String MEMBER_VIEW = "biz:devops:member:view";

    private final ProductService productService;

    public RndProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ApiResponse<ProductView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ProductCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(productService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ProductView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(productService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(productService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductView> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ProductUpdateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(productService.update(id, request));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<MemberView> addMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody MemberCreateRequest request) {
        PermissionGuard.require(permissions, MEMBER_MANAGE);
        return ApiResponse.success(productService.addMember(id, request));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<MemberView>> listMembers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MEMBER_VIEW);
        return ApiResponse.success(productService.listMembers(id));
    }

    @PutMapping("/{id}/members/{memberId}")
    public ApiResponse<MemberView> updateMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @PathVariable("memberId") long memberId,
            @RequestBody MemberUpdateRequest request) {
        PermissionGuard.require(permissions, MEMBER_MANAGE);
        return ApiResponse.success(productService.updateMember(id, memberId, request));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<Void> deleteMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @PathVariable("memberId") long memberId) {
        PermissionGuard.require(permissions, MEMBER_MANAGE);
        productService.deleteMember(id, memberId);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/statuses")
    public ApiResponse<List<StatusView>> listStatuses(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(productService.listStatuses(id));
    }
}
