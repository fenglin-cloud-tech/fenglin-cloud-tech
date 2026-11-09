package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.TestService;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionItemRequest;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionView;
import com.fenglin.rnd.web.dto.RndDtos.TestCaseCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TestCaseView;
import com.fenglin.rnd.web.dto.RndDtos.TestPlanCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TestPlanView;
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
 * E1 测试：用例评审、测试计划与执行接口。
 */
@RestController
@RequestMapping("/api/rnd/v1")
public class RndTestController {

    private static final String CASE_MANAGE = "biz:devops:testcase:manage";
    private static final String CASE_REVIEW = "biz:devops:testcase:review";
    private static final String CASE_VIEW = "biz:devops:testcase:view";
    private static final String PLAN_MANAGE = "biz:devops:testplan:manage";
    private static final String EXECUTE = "biz:devops:testplan:execute";
    private static final String PLAN_VIEW = "biz:devops:testplan:view";

    private final TestService testService;

    public RndTestController(TestService testService) {
        this.testService = testService;
    }

    @PostMapping("/test-cases")
    public ApiResponse<TestCaseView> createCase(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TestCaseCreateRequest request) {
        PermissionGuard.require(permissions, CASE_MANAGE);
        return ApiResponse.success(testService.createCase(request));
    }

    @GetMapping("/test-cases")
    public ApiResponse<List<TestCaseView>> listCases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CASE_VIEW);
        return ApiResponse.success(testService.listCases(productId, reviewStatus, page, size));
    }

    @GetMapping("/test-cases/{id}")
    public ApiResponse<TestCaseView> getCase(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CASE_VIEW);
        return ApiResponse.success(testService.getCase(id));
    }

    @PostMapping("/test-cases/{id}/review")
    public ApiResponse<TestCaseView> reviewCase(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam("reviewStatus") String reviewStatus) {
        PermissionGuard.require(permissions, CASE_REVIEW);
        return ApiResponse.success(testService.reviewCase(id, reviewStatus));
    }

    @PostMapping("/test-plans")
    public ApiResponse<TestPlanView> createPlan(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TestPlanCreateRequest request) {
        PermissionGuard.require(permissions, PLAN_MANAGE);
        return ApiResponse.success(testService.createPlan(request));
    }

    @GetMapping("/test-plans/{id}")
    public ApiResponse<TestPlanView> getPlan(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PLAN_VIEW);
        return ApiResponse.success(testService.getPlan(id));
    }

    @PostMapping("/test-plans/{id}/executions")
    public ApiResponse<ExecutionView> createExecution(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) ExecutionCreateRequest request) {
        PermissionGuard.require(permissions, EXECUTE);
        return ApiResponse.success(testService.createExecution(id, request));
    }

    @GetMapping("/executions/{id}")
    public ApiResponse<ExecutionView> getExecution(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PLAN_VIEW);
        return ApiResponse.success(testService.getExecution(id));
    }

    @PostMapping("/executions/{id}/items")
    public ApiResponse<ExecutionView> recordItem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ExecutionItemRequest request) {
        PermissionGuard.require(permissions, EXECUTE);
        return ApiResponse.success(testService.recordItem(id, request));
    }
}
