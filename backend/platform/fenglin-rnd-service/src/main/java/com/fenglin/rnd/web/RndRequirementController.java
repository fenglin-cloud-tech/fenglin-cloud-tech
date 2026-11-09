package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.RequirementService;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.web.dto.RndDtos.RelationCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RequirementCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RequirementReviewResult;
import com.fenglin.rnd.web.dto.RndDtos.RequirementTransitionRequest;
import com.fenglin.rnd.web.dto.RndDtos.RequirementView;
import com.fenglin.rnd.web.dto.RndDtos.SprintAssignRequest;
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
 * E1 需求：创建、流转、评审、关联与排期接口。
 */
@RestController
@RequestMapping("/api/rnd/v1/requirements")
public class RndRequirementController {

    private static final String CREATE = "biz:devops:requirement:create";
    private static final String UPDATE = "biz:devops:requirement:update";
    private static final String TRANSITION = "biz:devops:requirement:transition";
    private static final String REVIEW = "biz:devops:requirement:review";
    private static final String VIEW = "biz:devops:requirement:view";

    private final RequirementService requirementService;

    public RndRequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @PostMapping
    public ApiResponse<RequirementView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RequirementCreateRequest request) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(requirementService.create(request));
    }

    @GetMapping
    public ApiResponse<List<RequirementView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "sprintId", required = false) Long sprintId,
            @RequestParam(value = "statusCode", required = false) String statusCode,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(requirementService.list(productId, sprintId, statusCode, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RequirementView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(requirementService.get(id));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<RequirementView> transition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody RequirementTransitionRequest request) {
        PermissionGuard.require(permissions, TRANSITION);
        return ApiResponse.success(requirementService.transition(id, request.statusCode()));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<RequirementReviewResult> review(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REVIEW);
        return ApiResponse.success(requirementService.submitReview(id));
    }

    @PostMapping("/{id}/relations")
    public ApiResponse<Void> addRelation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody RelationCreateRequest request) {
        PermissionGuard.require(permissions, UPDATE);
        requirementService.addRelation(id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/sprint")
    public ApiResponse<RequirementView> assignSprint(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody SprintAssignRequest request) {
        PermissionGuard.require(permissions, UPDATE);
        return ApiResponse.success(requirementService.assignSprint(id, request));
    }
}
