package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.ReleaseService;
import com.fenglin.rnd.web.dto.RndDtos.DeployRequest;
import com.fenglin.rnd.web.dto.RndDtos.DeploymentView;
import com.fenglin.rnd.web.dto.RndDtos.ReleaseCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ReleaseView;
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
 * E1 发布：创建、审批、部署与回滚接口。
 */
@RestController
@RequestMapping("/api/rnd/v1/releases")
public class RndReleaseController {

    private static final String CREATE = "biz:devops:release:create";
    private static final String APPROVE = "biz:devops:release:approve";
    private static final String DEPLOY = "biz:devops:release:deploy";
    private static final String ROLLBACK = "biz:devops:release:rollback";
    private static final String VIEW = "biz:devops:release:view";

    private final ReleaseService releaseService;

    public RndReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @PostMapping
    public ApiResponse<ReleaseView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ReleaseCreateRequest request) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(releaseService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ReleaseView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "env", required = false) String env,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(releaseService.list(productId, env, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(releaseService.get(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ReleaseView> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(releaseService.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ReleaseView> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(releaseService.approve(id, true));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ReleaseView> reject(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(releaseService.approve(id, false));
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<DeploymentView> deploy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) DeployRequest request) {
        PermissionGuard.require(permissions, DEPLOY);
        return ApiResponse.success(releaseService.deploy(id, request == null ? null : request.strategy()));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<DeploymentView> rollback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ROLLBACK);
        return ApiResponse.success(releaseService.rollback(id));
    }

    @GetMapping("/{id}/deployments")
    public ApiResponse<List<DeploymentView>> listDeployments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(releaseService.listDeployments(id));
    }
}
