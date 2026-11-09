package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.OpsService;
import com.fenglin.finance.web.dto.FinanceDtos.BusinessRuleUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BusinessRuleView;
import com.fenglin.finance.web.dto.FinanceDtos.CurrencyUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CurrencyView;
import com.fenglin.finance.web.dto.FinanceDtos.ExchangeRateUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExchangeRateView;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceView;
import com.fenglin.finance.web.dto.FinanceDtos.PaymentPlanLine;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherImportRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherTemplateUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherTemplateView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 云财收口接口：凭证模板/导入、发票、币种汇率、业财规则、付款计划与预算查询。
 *
 * <p>文档未定义模板/币种/规则专属权限码，按总纲兜底复用账套管理权限（偏差已标注）。</p>
 */
@RestController
@RequestMapping("/api/finance/v1")
public class FinanceOpsController {

    private static final String LEDGER_MANAGE = "tenant:finance:ledger:manage";
    private static final String INVOICE_MANAGE = "biz:finance:invoice:manage";
    private static final String AP_VIEW = "biz:finance:ap:view";
    private static final String BUDGET_VIEW = "biz:finance:budget:view";
    private static final String VOUCHER_CREATE = "biz:finance:voucher:create";
    private static final String VOUCHER_VIEW = "biz:finance:voucher:view";

    private final OpsService opsService;

    public FinanceOpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @PostMapping("/voucher-templates")
    public ApiResponse<VoucherTemplateView> createTemplate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody VoucherTemplateUpsertRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(opsService.saveTemplate(null, request));
    }

    @PutMapping("/voucher-templates/{id}")
    public ApiResponse<VoucherTemplateView> updateTemplate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody VoucherTemplateUpsertRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(opsService.saveTemplate(id, request));
    }

    @GetMapping("/voucher-templates")
    public ApiResponse<List<VoucherTemplateView>> listTemplates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, VOUCHER_VIEW);
        return ApiResponse.success(opsService.listTemplates());
    }

    @PostMapping("/vouchers/import")
    public ApiResponse<Map<String, Object>> importVouchers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody VoucherImportRequest request) {
        PermissionGuard.require(permissions, VOUCHER_CREATE);
        return ApiResponse.success(opsService.importVouchers(request));
    }

    @PostMapping("/invoices/{id}/verify")
    public ApiResponse<InvoiceView> verifyInvoice(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, INVOICE_MANAGE);
        return ApiResponse.success(opsService.verifyInvoice(id));
    }

    @PostMapping("/currencies")
    public ApiResponse<CurrencyView> saveCurrency(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CurrencyUpsertRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(opsService.saveCurrency(request));
    }

    @GetMapping("/currencies")
    public ApiResponse<List<CurrencyView>> listCurrencies(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, BUDGET_VIEW);
        return ApiResponse.success(opsService.listCurrencies());
    }

    @PostMapping("/exchange-rates")
    public ApiResponse<ExchangeRateView> saveExchangeRate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ExchangeRateUpsertRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(opsService.saveExchangeRate(request));
    }

    @GetMapping("/exchange-rates")
    public ApiResponse<List<ExchangeRateView>> listExchangeRates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        PermissionGuard.require(permissions, BUDGET_VIEW);
        return ApiResponse.success(opsService.listExchangeRates(limit));
    }

    @PostMapping("/rules")
    public ApiResponse<BusinessRuleView> saveRule(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BusinessRuleUpsertRequest request) {
        PermissionGuard.require(permissions, LEDGER_MANAGE);
        return ApiResponse.success(opsService.saveRule(request));
    }

    @GetMapping("/rules")
    public ApiResponse<List<BusinessRuleView>> listRules(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, BUDGET_VIEW);
        return ApiResponse.success(opsService.listRules());
    }

    @GetMapping("/payment-plan")
    public ApiResponse<List<PaymentPlanLine>> paymentPlan(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        PermissionGuard.require(permissions, AP_VIEW);
        return ApiResponse.success(opsService.paymentPlan(limit));
    }

    @GetMapping("/budgets/available")
    public ApiResponse<Map<String, Object>> budgetAvailability(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodYear", required = false) Integer periodYear,
            @RequestParam(value = "dimension", required = false) String dimension,
            @RequestParam(value = "dimensionId", required = false) Long dimensionId,
            @RequestParam(value = "subjectCode", required = false) String subjectCode) {
        PermissionGuard.require(permissions, BUDGET_VIEW);
        return ApiResponse.success(opsService.budgetAvailability(periodYear, dimension, dimensionId, subjectCode));
    }
}
