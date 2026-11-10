package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.PayrollService;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.MyPayslipPeriodView;
import com.fenglin.talent.web.dto.TalentDtos.MyPayslipView;
import com.fenglin.talent.web.dto.TalentDtos.PayItemCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PayItemView;
import com.fenglin.talent.web.dto.TalentDtos.PayPeriodCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PayPeriodView;
import com.fenglin.talent.web.dto.TalentDtos.PayrollRecordView;
import com.fenglin.talent.web.dto.TalentDtos.PayslipSubmitResult;
import com.fenglin.talent.web.dto.TalentDtos.PayslipView;
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
 * 薪酬接口：薪资项、薪资周期与核算、发放单（提交走工作流审批，通过后可发放）。
 */
@RestController
@RequestMapping("/api/talent/v1/payroll")
public class TalentPayrollController {

    private static final String LIST = "talent:admin:payroll:list";
    private static final String MANAGE = "talent:admin:payroll:manage";
    private static final String APPROVE = "talent:admin:payroll:approve";
    /** 员工自助薪资单权限：只读本人数据，服务端按令牌上下文收敛，不提供查询他人的入口。 */
    private static final String SELF_VIEW = "talent:self:payroll:view";

    private final PayrollService payrollService;

    public TalentPayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/items")
    public ApiResponse<List<PayItemView>> items(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(payrollService.items(permissions));
    }

    @PostMapping("/items")
    public ApiResponse<PayItemView> createItem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PayItemCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(payrollService.createItem(request, permissions));
    }

    @GetMapping("/periods")
    public ApiResponse<PageResult<PayPeriodView>> periods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(payrollService.periods(page, size, permissions));
    }

    @PostMapping("/periods")
    public ApiResponse<PayPeriodView> createPeriod(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PayPeriodCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(payrollService.createPeriod(request, permissions));
    }

    @PostMapping("/periods/{id}/calculate")
    public ApiResponse<PayPeriodView> calculate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(payrollService.calculate(id, permissions));
    }

    @GetMapping("/periods/{id}/records")
    public ApiResponse<PageResult<PayrollRecordView>> records(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(payrollService.records(id, page, size, permissions));
    }

    @PostMapping("/periods/{id}/slip")
    public ApiResponse<PayslipSubmitResult> submitSlip(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(payrollService.submitSlip(id, permissions));
    }

    /**
     * 员工自助薪资单：员工 ID 只来自令牌上下文，请求参数里没有 employeeId，
     * 因此不存在传入他人 ID 查询他人工资条的口子。
     */
    @GetMapping("/slips/my")
    public ApiResponse<PageResult<MyPayslipView>> mySlips(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(payrollService.mySlips(periodId, page, size, permissions));
    }

    /** 本人有工资条的期间列表，用于前端筛选，同样只按令牌上下文收敛。 */
    @GetMapping("/slips/my/periods")
    public ApiResponse<List<MyPayslipPeriodView>> mySlipPeriods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(payrollService.mySlipPeriods());
    }

    @GetMapping("/slips")
    public ApiResponse<PageResult<PayslipView>> slips(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(payrollService.slips(page, size, permissions));
    }

    @GetMapping("/slips/{id}")
    public ApiResponse<PayslipView> slip(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(payrollService.slip(id, permissions));
    }

    @PostMapping("/slips/{id}/approval")
    public ApiResponse<PayslipView> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(payrollService.approve(id, request, permissions));
    }

    @PostMapping("/slips/{id}/pay")
    public ApiResponse<PayslipView> pay(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(payrollService.pay(id, permissions));
    }
}
