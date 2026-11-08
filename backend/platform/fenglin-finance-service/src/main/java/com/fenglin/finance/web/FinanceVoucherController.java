package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.ReportService;
import com.fenglin.finance.service.VoucherService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.AuditTrailView;
import com.fenglin.finance.web.dto.FinanceDtos.BalanceSheetView;
import com.fenglin.finance.web.dto.FinanceDtos.IncomeStatementView;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherActionResult;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherView;
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
 * 凭证制单/审核/过账/冲销、审计链与财务报表接口。
 */
@RestController
@RequestMapping("/api/finance/v1")
public class FinanceVoucherController {

    private static final String CREATE = "biz:finance:voucher:create";
    private static final String SUBMIT = "biz:finance:voucher:submit";
    private static final String AUDIT = "biz:finance:voucher:audit";
    private static final String POST = "biz:finance:voucher:post";
    private static final String REVERSE = "biz:finance:voucher:reverse";
    private static final String VIEW = "biz:finance:voucher:view";
    private static final String AUDIT_VIEW = "tenant:finance:audit:view";
    private static final String REPORT_VIEW = "biz:finance:report:view";

    private final VoucherService voucherService;
    private final ReportService reportService;

    public FinanceVoucherController(VoucherService voucherService, ReportService reportService) {
        this.voucherService = voucherService;
        this.reportService = reportService;
    }

    @PostMapping("/vouchers")
    public ApiResponse<VoucherView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody VoucherCreateRequest request) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(voucherService.create(request, idempotencyKey));
    }

    @GetMapping("/vouchers")
    public ApiResponse<List<VoucherView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("ledgerId") long ledgerId,
            @RequestParam(value = "periodCode", required = false) String periodCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(voucherService.list(ledgerId, periodCode, status,
                FinanceContext.size(size), FinanceContext.offset(page, size)));
    }

    @GetMapping("/vouchers/{id}")
    public ApiResponse<VoucherView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(voucherService.detail(id));
    }

    @PostMapping("/vouchers/{id}/submit")
    public ApiResponse<VoucherActionResult> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SUBMIT);
        return ApiResponse.success(voucherService.submit(id));
    }

    @PostMapping("/vouchers/{id}/audit")
    public ApiResponse<VoucherActionResult> audit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, AUDIT);
        return ApiResponse.success(voucherService.audit(id));
    }

    @PostMapping("/vouchers/{id}/post")
    public ApiResponse<VoucherActionResult> post(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, POST);
        return ApiResponse.success(voucherService.post(id));
    }

    @PostMapping("/vouchers/{id}/reverse")
    public ApiResponse<VoucherActionResult> reverse(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REVERSE);
        return ApiResponse.success(voucherService.reverse(id));
    }

    @GetMapping("/vouchers/{id}/audit-trail")
    public ApiResponse<AuditTrailView> auditTrail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, AUDIT_VIEW);
        return ApiResponse.success(voucherService.auditTrail(id));
    }

    @GetMapping("/reports/balance-sheet")
    public ApiResponse<BalanceSheetView> balanceSheet(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("ledgerId") long ledgerId, @RequestParam("periodCode") String periodCode) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        return ApiResponse.success(reportService.balanceSheet(ledgerId, periodCode));
    }

    @GetMapping("/reports/income-statement")
    public ApiResponse<IncomeStatementView> incomeStatement(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("ledgerId") long ledgerId, @RequestParam("periodCode") String periodCode) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        return ApiResponse.success(reportService.incomeStatement(ledgerId, periodCode));
    }
}
