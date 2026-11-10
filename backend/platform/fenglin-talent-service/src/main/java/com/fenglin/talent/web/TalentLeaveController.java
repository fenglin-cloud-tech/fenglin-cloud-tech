package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.LeaveService;
import com.fenglin.talent.web.dto.TalentDtos.LeaveApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.LeaveBalanceView;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestResult;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestView;
import com.fenglin.talent.web.dto.TalentDtos.LeaveTypeView;
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
 * 假期接口：类型与额度查询、请假申请（发起工作流）、审批与撤销。
 */
@RestController
@RequestMapping("/api/talent/v1/leave")
public class TalentLeaveController {

    private static final String ADMIN_LIST = "talent:admin:leave:list";
    private static final String APPROVE = "talent:admin:leave:approve";
    private static final String APPLY = "talent:leave:apply";
    private static final String VIEW = "talent:leave:view";

    private final LeaveService leaveService;

    public TalentLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/types")
    public ApiResponse<List<LeaveTypeView>> types(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(leaveService.types());
    }

    @GetMapping("/balances")
    public ApiResponse<List<LeaveBalanceView>> balances(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "year", required = false) Integer year) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(leaveService.balances(employeeId, year));
    }

    @PostMapping("/requests")
    public ApiResponse<LeaveRequestResult> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody LeaveRequestCreateRequest request) {
        PermissionGuard.require(permissions, APPLY);
        return ApiResponse.success(leaveService.create(request, permissions));
    }

    @GetMapping("/requests")
    public ApiResponse<PageResult<LeaveRequestView>> requests(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ADMIN_LIST);
        return ApiResponse.success(leaveService.list(employeeId, status, page, size));
    }

    @GetMapping("/requests/my")
    public ApiResponse<PageResult<LeaveRequestView>> myRequests(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(leaveService.myRequests(status, page, size));
    }

    @PostMapping("/requests/{id}/approval")
    public ApiResponse<LeaveRequestResult> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody LeaveApprovalRequest request) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(leaveService.approve(id, request));
    }

    @PostMapping("/requests/{id}/cancel")
    public ApiResponse<LeaveRequestResult> cancel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPLY);
        return ApiResponse.success(leaveService.cancel(id));
    }
}
