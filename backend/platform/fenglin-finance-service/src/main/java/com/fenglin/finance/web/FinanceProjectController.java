package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.ProjectService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.CostCollectionRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CostView;
import com.fenglin.finance.web.dto.FinanceDtos.MemberCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.MemberView;
import com.fenglin.finance.web.dto.FinanceDtos.MilestoneCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.MilestoneView;
import com.fenglin.finance.web.dto.FinanceDtos.ProfitabilityView;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectApproveResult;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectDetail;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectView;
import com.fenglin.finance.web.dto.FinanceDtos.RevenueRecognizeRequest;
import com.fenglin.finance.web.dto.FinanceDtos.RevenueRecognizeResult;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementView;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetApproveRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetApproveResult;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetRejectRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetView;
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
 * D2 项目核算接口：立项、成员、里程碑、工时、成本、收入、结算与盈利分析。
 */
@RestController
@RequestMapping("/api/finance/v1/projects")
public class FinanceProjectController {

    private static final String PROJECT_CREATE = "biz:finance:project:create";
    private static final String PROJECT_APPROVE = "biz:finance:project:approve";
    private static final String PROJECT_VIEW = "biz:finance:project:view";
    private static final String MEMBER = "biz:finance:project:member:manage";
    private static final String TIMESHEET_SUBMIT = "biz:finance:timesheet:submit";
    private static final String TIMESHEET_APPROVE = "biz:finance:timesheet:approve";
    private static final String TIMESHEET_VIEW = "biz:finance:timesheet:view";
    private static final String COST = "biz:finance:cost:collect";
    private static final String REVENUE = "biz:finance:revenue:recognize";
    private static final String SETTLEMENT_CREATE = "biz:finance:settlement:create";
    private static final String SETTLEMENT_APPROVE = "biz:finance:settlement:approve";
    private static final String PROFITABILITY = "biz:finance:profitability:view";

    private final ProjectService projectService;

    public FinanceProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ApiResponse<ProjectView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ProjectCreateRequest request) {
        PermissionGuard.require(permissions, PROJECT_CREATE);
        return ApiResponse.success(projectService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ProjectView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, PROJECT_VIEW);
        return ApiResponse.success(projectService.list(status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetail> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROJECT_VIEW);
        return ApiResponse.success(projectService.detail(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ProjectApproveResult> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROJECT_APPROVE);
        return ApiResponse.success(projectService.approve(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<MemberView> addMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody MemberCreateRequest request) {
        PermissionGuard.require(permissions, MEMBER);
        return ApiResponse.success(projectService.addMember(id, request));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<MemberView>> members(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROJECT_VIEW);
        return ApiResponse.success(projectService.listMembers(id));
    }

    @PostMapping("/{id}/milestones")
    public ApiResponse<MilestoneView> addMilestone(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody MilestoneCreateRequest request) {
        PermissionGuard.require(permissions, MEMBER);
        return ApiResponse.success(projectService.addMilestone(id, request));
    }

    @GetMapping("/{id}/milestones")
    public ApiResponse<List<MilestoneView>> milestones(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROJECT_VIEW);
        return ApiResponse.success(projectService.listMilestones(id));
    }

    @PostMapping("/{id}/timesheets")
    public ApiResponse<TimesheetView> createTimesheet(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody TimesheetCreateRequest request) {
        PermissionGuard.require(permissions, TIMESHEET_SUBMIT);
        TimesheetCreateRequest body = new TimesheetCreateRequest(request.employeeId(), request.employeeName(),
                id, request.milestoneId(), request.workDate(), request.hours(), request.taskDesc());
        return ApiResponse.success(projectService.createTimesheet(body));
    }

    @GetMapping("/{id}/timesheets")
    public ApiResponse<List<TimesheetView>> timesheets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, TIMESHEET_VIEW);
        return ApiResponse.success(projectService.listTimesheets(id, status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @PostMapping("/timesheets/approve")
    public ApiResponse<TimesheetApproveResult> approveTimesheets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TimesheetApproveRequest request) {
        PermissionGuard.require(permissions, TIMESHEET_APPROVE);
        return ApiResponse.success(projectService.approveTimesheets(request));
    }

    @PostMapping("/timesheets/{timesheetId}/reject")
    public ApiResponse<TimesheetView> rejectTimesheet(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("timesheetId") long timesheetId,
            @RequestBody(required = false) TimesheetRejectRequest request) {
        PermissionGuard.require(permissions, TIMESHEET_APPROVE);
        return ApiResponse.success(projectService.rejectTimesheet(timesheetId,
                request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/costs")
    public ApiResponse<CostView> collectCost(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody CostCollectionRequest request) {
        PermissionGuard.require(permissions, COST);
        CostCollectionRequest body = new CostCollectionRequest(id, request.costType(), request.sourceBizId(),
                request.subjectCode(), request.amount(), request.costDate(), request.description());
        return ApiResponse.success(projectService.collectCost(body));
    }

    @GetMapping("/{id}/costs")
    public ApiResponse<List<CostView>> costs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROJECT_VIEW);
        return ApiResponse.success(projectService.listCosts(id));
    }

    @PostMapping("/{id}/revenue")
    public ApiResponse<RevenueRecognizeResult> recognizeRevenue(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) RevenueRecognizeRequest request) {
        PermissionGuard.require(permissions, REVENUE);
        return ApiResponse.success(projectService.recognizeRevenue(id, request));
    }

    @PostMapping("/{id}/settlements")
    public ApiResponse<SettlementView> createSettlement(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody SettlementCreateRequest request) {
        PermissionGuard.require(permissions, SETTLEMENT_CREATE);
        return ApiResponse.success(projectService.createSettlement(id, request));
    }

    @PostMapping("/settlements/{settlementId}/approve")
    public ApiResponse<SettlementView> approveSettlement(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("settlementId") long settlementId) {
        PermissionGuard.require(permissions, SETTLEMENT_APPROVE);
        return ApiResponse.success(projectService.approveSettlement(settlementId));
    }

    @GetMapping("/{id}/profitability")
    public ApiResponse<ProfitabilityView> profitability(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROFITABILITY);
        return ApiResponse.success(projectService.profitability(id));
    }
}
