package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.BankService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.BankAccountCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BankAccountView;
import com.fenglin.finance.web.dto.FinanceDtos.BankTransactionView;
import com.fenglin.finance.web.dto.FinanceDtos.BankTxnImportRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BankTxnImportResult;
import com.fenglin.finance.web.dto.FinanceDtos.ReconcileRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ReconciliationView;
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
 * 银行账户、流水导入与银企对账接口。
 */
@RestController
@RequestMapping("/api/finance/v1/bank")
public class FinanceBankController {

    private static final String BANK = "biz:finance:bank:manage";
    private static final String RECONCILE = "tenant:finance:reconcile:manage";

    private final BankService bankService;

    public FinanceBankController(BankService bankService) {
        this.bankService = bankService;
    }

    @PostMapping("/accounts")
    public ApiResponse<BankAccountView> createAccount(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BankAccountCreateRequest request) {
        PermissionGuard.require(permissions, BANK);
        return ApiResponse.success(bankService.createAccount(request));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<BankAccountView>> listAccounts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, BANK);
        return ApiResponse.success(bankService.listAccounts());
    }

    @PostMapping("/transactions/import")
    public ApiResponse<BankTxnImportResult> importTransactions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BankTxnImportRequest request) {
        PermissionGuard.require(permissions, BANK);
        return ApiResponse.success(bankService.importTransactions(request));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ApiResponse<List<BankTransactionView>> listTransactions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("accountId") long accountId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, BANK);
        return ApiResponse.success(bankService.listTransactions(accountId, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @PostMapping("/reconciliations")
    public ApiResponse<ReconciliationView> reconcile(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ReconcileRequest request) {
        PermissionGuard.require(permissions, RECONCILE);
        return ApiResponse.success(bankService.reconcile(request));
    }

    @GetMapping("/reconciliations")
    public ApiResponse<List<ReconciliationView>> listReconciliations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, RECONCILE);
        return ApiResponse.success(bankService.listReconciliations(FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }
}
