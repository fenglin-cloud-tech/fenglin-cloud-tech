package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.LedgerService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.LedgerCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.LedgerView;
import com.fenglin.finance.web.dto.FinanceDtos.PeriodCloseResult;
import com.fenglin.finance.web.dto.FinanceDtos.PeriodView;
import com.fenglin.finance.web.dto.FinanceDtos.SubjectCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.SubjectView;
import com.fenglin.finance.web.dto.FinanceDtos.TrialBalanceLine;
import com.fenglin.finance.web.dto.FinanceDtos.TrialBalanceView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账套、科目、会计期间与试算平衡接口。
 */
@RestController
@RequestMapping("/api/finance/v1/ledgers")
public class FinanceLedgerController {

    private static final String LEDGER_MANAGE = "tenant:finance:ledger:manage";
    private static final String SUBJECT_MANAGE = "tenant:finance:subject:manage";
    private static final String PERIOD_MANAGE = "tenant:finance:period:manage";
    private static final String LEDGER_VIEW = "biz:finance:ledger:view";
    private static final String SUBJECT_VIEW = "biz:finance:subject:view";
    private static final String PERIOD_VIEW = "biz:finance:period:view";
    private static final String REPORT_VIEW = "biz:finance:report:view";

    private final LedgerService ledgerService;

    public FinanceLedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ApiResponse<LedgerView> create(@RequestHeader(value = "X-Permissions", required = false) String permissions,
                                          @RequestBody LedgerCreateRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(ledgerService.create(request));
    }

    @GetMapping
    public ApiResponse<List<LedgerView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, LEDGER_VIEW);
        return ApiResponse.success(ledgerService.list());
    }

    @PostMapping("/{ledgerId}/subjects")
    public ApiResponse<SubjectView> createSubject(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId, @RequestBody SubjectCreateRequest request) {
        PermissionGuard.require(permissions, SUBJECT_MANAGE);
        return ApiResponse.success(ledgerService.createSubject(ledgerId, request));
    }

    @GetMapping("/{ledgerId}/subjects")
    public ApiResponse<List<SubjectView>> subjects(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId) {
        PermissionGuard.require(permissions, SUBJECT_VIEW);
        return ApiResponse.success(ledgerService.subjects(ledgerId));
    }

    @GetMapping("/{ledgerId}/periods")
    public ApiResponse<List<PeriodView>> periods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId) {
        PermissionGuard.require(permissions, PERIOD_VIEW);
        return ApiResponse.success(ledgerService.periods(ledgerId));
    }

    @PostMapping("/{ledgerId}/periods/{periodCode}/close")
    public ApiResponse<PeriodCloseResult> close(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId, @PathVariable("periodCode") String periodCode) {
        PermissionGuard.require(permissions, PERIOD_MANAGE);
        return ApiResponse.success(ledgerService.close(ledgerId, periodCode));
    }

    @PostMapping("/{ledgerId}/periods/{periodCode}/reopen")
    public ApiResponse<PeriodView> reopen(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId, @PathVariable("periodCode") String periodCode) {
        PermissionGuard.require(permissions, PERIOD_MANAGE);
        return ApiResponse.success(ledgerService.reopen(ledgerId, periodCode));
    }

    @GetMapping("/{ledgerId}/periods/{periodCode}/trial-balance")
    public ApiResponse<TrialBalanceView> trialBalance(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("ledgerId") long ledgerId, @PathVariable("periodCode") String periodCode) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        List<TrialBalanceLine> lines = ledgerService.trialBalance(ledgerId, periodCode);
        BigDecimal totalDebit = FinanceContext.zero();
        BigDecimal totalCredit = FinanceContext.zero();
        for (TrialBalanceLine line : lines) {
            totalDebit = totalDebit.add(line.closingDebit() == null ? BigDecimal.ZERO : line.closingDebit());
            totalCredit = totalCredit.add(line.closingCredit() == null ? BigDecimal.ZERO : line.closingCredit());
        }
        totalDebit = FinanceContext.amount(totalDebit);
        totalCredit = FinanceContext.amount(totalCredit);
        return ApiResponse.success(new TrialBalanceView(periodCode, lines, totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0));
    }
}
