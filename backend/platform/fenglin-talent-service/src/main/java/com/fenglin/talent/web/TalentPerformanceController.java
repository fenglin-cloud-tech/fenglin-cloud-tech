package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.PerformanceService;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfCycleCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfCycleView;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationSubmitResult;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationView;
import com.fenglin.talent.web.dto.TalentDtos.PerfGoalCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfGoalView;
import com.fenglin.talent.web.dto.TalentDtos.PerfMyView;
import com.fenglin.talent.web.dto.TalentDtos.PerfResultView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效接口：周期、目标、评估、结果。
 * 评估提交走工作流审批，审批通过后计入结果；结果全部发布后才可关闭周期。
 */
@RestController
@RequestMapping("/api/talent/v1/performance")
public class TalentPerformanceController {

    private static final String LIST = "talent:admin:performance:list";
    private static final String MANAGE = "talent:admin:performance:manage";
    private static final String APPROVE = "talent:admin:performance:approve";
    private static final String SELF_VIEW = "talent:self:performance:view";

    private final PerformanceService performanceService;

    public TalentPerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    /** 员工自助：本人有数据的绩效周期。 */
    @GetMapping("/my/cycles")
    public ApiResponse<PageResult<PerfCycleView>> myCycles(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(performanceService.myCycles(page, size));
    }

    /** 员工自助：本人目标、评估与已发布结果。 */
    @GetMapping("/my")
    public ApiResponse<PerfMyView> myPerformance(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "cycle_id", required = false) Long cycleId) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(performanceService.myPerformance(cycleId));
    }

    @GetMapping("/cycles")
    public ApiResponse<PageResult<PerfCycleView>> cycles(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(performanceService.cycles(page, size));
    }

    @PostMapping("/cycles")
    public ApiResponse<PerfCycleView> createCycle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PerfCycleCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.createCycle(request));
    }

    @GetMapping("/cycles/{id}")
    public ApiResponse<PerfCycleView> cycle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(performanceService.cycle(id));
    }

    @PostMapping("/cycles/{id}/open-goals")
    public ApiResponse<PerfCycleView> openGoals(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.openGoals(id));
    }

    @PostMapping("/cycles/{id}/start-evaluation")
    public ApiResponse<PerfCycleView> startEvaluation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.startEvaluation(id));
    }

    @PostMapping("/cycles/{id}/close")
    public ApiResponse<PerfCycleView> closeCycle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(performanceService.closeCycle(id));
    }

    @GetMapping("/cycles/{id}/goals")
    public ApiResponse<PageResult<PerfGoalView>> goals(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(performanceService.goals(id, page, size));
    }

    @PostMapping("/goals")
    public ApiResponse<PerfGoalView> createGoal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PerfGoalCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.createGoal(request));
    }

    @PostMapping("/goals/{id}/submit")
    public ApiResponse<PerfGoalView> submitGoal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.submitGoal(id));
    }

    @PostMapping("/goals/{id}/approval")
    public ApiResponse<PerfGoalView> approveGoal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(performanceService.approveGoal(id, request));
    }

    @GetMapping("/cycles/{id}/evaluations")
    public ApiResponse<PageResult<PerfEvaluationView>> evaluations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(performanceService.evaluations(id, page, size));
    }

    @PostMapping("/evaluations")
    public ApiResponse<PerfEvaluationView> createEvaluation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PerfEvaluationCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.createEvaluation(request));
    }

    @PostMapping("/evaluations/{id}/submit")
    public ApiResponse<PerfEvaluationSubmitResult> submitEvaluation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.submitEvaluation(id, permissions));
    }

    @PostMapping("/evaluations/{id}/approval")
    public ApiResponse<PerfEvaluationView> approveEvaluation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(performanceService.approveEvaluation(id, request));
    }

    @GetMapping("/cycles/{id}/results")
    public ApiResponse<PageResult<PerfResultView>> results(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(performanceService.results(id, page, size));
    }

    @PostMapping("/cycles/{id}/results/calculate")
    public ApiResponse<PageResult<PerfResultView>> calculateResults(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(performanceService.calculateResults(id, page, size));
    }

    @PostMapping("/cycles/{id}/results/publish")
    public ApiResponse<PageResult<PerfResultView>> publishResults(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(performanceService.publishResults(id, page, size));
    }
}
