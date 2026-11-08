package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.ExpenseService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetControlCheckRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetControlResult;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetView;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseActionResult;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpensePayRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseRejectRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseView;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceView;
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
 * 费用报销、预算与发票接口。
 */
@RestController
@RequestMapping("/api/finance/v1")
public class FinanceExpenseController {

    private static final String SUBMIT = "biz:finance:expense:submit";
    private static final String APPROVE = "biz:finance:expense:approve";
    private static final String PAY = "biz:finance:expense:pay";
    private static final String VIEW = "biz:finance:expense:view";
    private static final String BUDGET_MANAGE = "tenant:finance:budget:manage";
    private static final String BUDGET_VIEW = "biz:finance:budget:view";
    private static final String BUDGET_CONTROL = "biz:finance:budget:control";
    private static final String INVOICE = "biz:finance:invoice:manage";

    private final ExpenseService expenseService;

    public FinanceExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/expenses")
    public ApiResponse<ExpenseView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExpenseCreateRequest request) {
        PermissionGuard.require(permissions, SUBMIT);
        return ApiResponse.success(expenseService.create(request, idempotencyKey));
    }

    @GetMapping("/expenses")
    public ApiResponse<List<ExpenseView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(expenseService.list(status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @GetMapping("/expenses/{id}")
    public ApiResponse<ExpenseView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(expenseService.detail(id));
    }

    @PostMapping("/expenses/{id}/submit")
    public ApiResponse<ExpenseActionResult> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SUBMIT);
        return ApiResponse.success(expenseService.submit(id));
    }

    @PostMapping("/expenses/{id}/approve")
    public ApiResponse<ExpenseActionResult> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(expenseService.approve(id));
    }

    @PostMapping("/expenses/{id}/reject")
    public ApiResponse<ExpenseActionResult> reject(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) ExpenseRejectRequest request) {
        PermissionGuard.require(permissions, APPROVE);
        return ApiResponse.success(expenseService.reject(id, request == null ? null : request.reason()));
    }

    @PostMapping("/expenses/{id}/pay")
    public ApiResponse<ExpenseActionResult> pay(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) ExpensePayRequest request) {
        PermissionGuard.require(permissions, PAY);
        return ApiResponse.success(expenseService.pay(id, request, null));
    }

    @PostMapping("/budgets")
    public ApiResponse<BudgetView> createBudget(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BudgetCreateRequest request) {
        PermissionGuard.require(permissions, BUDGET_MANAGE);
        return ApiResponse.success(expenseService.createBudget(request));
    }

    @GetMapping("/budgets")
    public ApiResponse<List<BudgetView>> listBudgets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodYear", required = false) Integer periodYear,
            @RequestParam(value = "dimension", required = false) String dimension,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, BUDGET_VIEW);
        return ApiResponse.success(expenseService.listBudgets(periodYear, dimension,
                FinanceContext.size(size), FinanceContext.offset(page, size)));
    }

    @PostMapping("/budgets/{id}/release")
    public ApiResponse<BudgetView> releaseBudget(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, BUDGET_MANAGE);
        return ApiResponse.success(expenseService.releaseBudget(id));
    }

    @PostMapping("/budgets/control-check")
    public ApiResponse<BudgetControlResult> checkBudget(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BudgetControlCheckRequest request) {
        PermissionGuard.require(permissions, BUDGET_CONTROL);
        return ApiResponse.success(expenseService.checkBudget(request));
    }

    @PostMapping("/invoices")
    public ApiResponse<InvoiceView> createInvoice(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody InvoiceCreateRequest request) {
        PermissionGuard.require(permissions, INVOICE);
        return ApiResponse.success(expenseService.createInvoice(request));
    }

    @GetMapping("/invoices")
    public ApiResponse<List<InvoiceView>> listInvoices(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "invoiceType", required = false) String invoiceType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, INVOICE);
        return ApiResponse.success(expenseService.listInvoices(invoiceType, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }
}
