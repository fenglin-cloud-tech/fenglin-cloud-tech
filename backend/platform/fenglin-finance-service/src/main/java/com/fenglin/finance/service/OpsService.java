package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.ExpenseMapper;
import com.fenglin.finance.mapper.OpsMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.BusinessRuleUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BusinessRuleView;
import com.fenglin.finance.web.dto.FinanceDtos.CurrencyUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CurrencyView;
import com.fenglin.finance.web.dto.FinanceDtos.ExchangeRateUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExchangeRateView;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceView;
import com.fenglin.finance.web.dto.FinanceDtos.PaymentPlanLine;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherImportRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherTemplateUpsertRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherTemplateView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云财收口：凭证模板/批量导入、发票、币种汇率、业财规则、付款计划与预算可用额度。
 */
@Service
@Transactional(readOnly = true)
public class OpsService {

    private final OpsMapper opsMapper;
    private final ExpenseMapper expenseMapper;
    private final VoucherService voucherService;
    private final LedgerService ledgerService;
    private final FinanceAudit audit;
    private final ObjectMapper objectMapper;

    public OpsService(OpsMapper opsMapper, ExpenseMapper expenseMapper, VoucherService voucherService,
                      LedgerService ledgerService, FinanceAudit audit, ObjectMapper objectMapper) {
        this.opsMapper = opsMapper;
        this.expenseMapper = expenseMapper;
        this.voucherService = voucherService;
        this.ledgerService = ledgerService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ---------- 凭证模板与导入 ----------

    @Transactional
    public VoucherTemplateView saveTemplate(Long id, VoucherTemplateUpsertRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.templateCode() == null || request.templateCode().isBlank()
                || request.templateName() == null || request.templateName().isBlank()
                || request.bizType() == null || request.bizType().isBlank()
                || request.debitSubjectCode() == null || request.debitSubjectCode().isBlank()
                || request.creditSubjectCode() == null || request.creditSubjectCode().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        String mappingJson = request.auxiliaryMapping() == null ? "{}" : writeJson(request.auxiliaryMapping());
        if (id == null) {
            long templateId = IdWorker.getId();
            opsMapper.insertTemplate(templateId, tenantId, request.templateCode(), request.templateName(),
                    request.bizType(), request.debitSubjectCode(), request.creditSubjectCode(), mappingJson,
                    request.conditionExpr(), FinanceContext.userId());
            audit.publish("finance.voucher_template.created", "CREATE", "fin_voucher_template", templateId, "LOW");
            return toTemplate(opsMapper.findTemplate(tenantId, templateId));
        }
        if (opsMapper.findTemplate(tenantId, id) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        opsMapper.updateTemplate(tenantId, id, request.templateName(), request.bizType(),
                request.debitSubjectCode(), request.creditSubjectCode(), mappingJson, request.conditionExpr());
        audit.publish("finance.voucher_template.updated", "UPDATE", "fin_voucher_template", id, "LOW");
        return toTemplate(opsMapper.findTemplate(tenantId, id));
    }

    public List<VoucherTemplateView> listTemplates() {
        long tenantId = FinanceContext.tenantId();
        List<VoucherTemplateView> views = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listTemplates(tenantId)) {
            views.add(toTemplate(row));
        }
        return views;
    }

    @Transactional
    public Map<String, Object> importVouchers(VoucherImportRequest request) {
        if (request.ledgerId() == null || request.vouchers() == null || request.vouchers().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        ledgerService.requireLedger(FinanceContext.tenantId(), request.ledgerId());
        List<Long> ids = new ArrayList<>();
        for (VoucherCreateRequest voucher : request.vouchers()) {
            ids.add(voucherService.create(voucher, null).id());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", ids.size());
        result.put("voucher_ids", ids);
        return result;
    }

    // ---------- 发票 ----------

    @Transactional
    public InvoiceView verifyInvoice(long id) {
        long tenantId = FinanceContext.tenantId();
        if (opsMapper.findInvoice(tenantId, id) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        opsMapper.markInvoiceVerified(tenantId, id);
        audit.publish("finance.invoice.verified", "VERIFY", "fin_invoice", id, "MEDIUM");
        return toInvoice(opsMapper.findInvoice(tenantId, id));
    }

    // ---------- 币种与汇率 ----------

    @Transactional
    public CurrencyView saveCurrency(CurrencyUpsertRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.currencyCode() == null || request.currencyCode().isBlank()
                || request.currencyName() == null || request.currencyName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        int decimals = request.decimalPlaces() == null ? 2 : request.decimalPlaces();
        Map<String, Object> existing = opsMapper.findCurrency(tenantId, request.currencyCode());
        if (existing == null) {
            long id = IdWorker.getId();
            opsMapper.insertCurrency(id, tenantId, request.currencyCode(), request.currencyName(),
                    request.symbol(), decimals, FinanceContext.userId());
            audit.publish("finance.currency.created", "CREATE", "fin_currency", id, "LOW");
        } else {
            long id = Rows.lngVal(existing, "id");
            opsMapper.updateCurrency(tenantId, id, request.currencyName(), request.symbol(), decimals);
            audit.publish("finance.currency.updated", "UPDATE", "fin_currency", id, "LOW");
        }
        return toCurrency(opsMapper.findCurrency(tenantId, request.currencyCode()));
    }

    public List<CurrencyView> listCurrencies() {
        long tenantId = FinanceContext.tenantId();
        List<CurrencyView> views = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listCurrencies(tenantId)) {
            views.add(toCurrency(row));
        }
        return views;
    }

    @Transactional
    public ExchangeRateView saveExchangeRate(ExchangeRateUpsertRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.fromCurrency() == null || request.toCurrency() == null
                || request.rate() == null || request.rate().signum() <= 0 || request.effectiveDate() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        Map<String, Object> existing = opsMapper.findExchangeRate(tenantId, request.fromCurrency(),
                request.toCurrency(), request.effectiveDate());
        if (existing == null) {
            long id = IdWorker.getId();
            opsMapper.insertExchangeRate(id, tenantId, request.fromCurrency(), request.toCurrency(),
                    request.rate(), request.effectiveDate(), request.source(), FinanceContext.userId());
            audit.publish("finance.exchange_rate.created", "CREATE", "fin_exchange_rate", id, "LOW");
        } else {
            long id = Rows.lngVal(existing, "id");
            opsMapper.updateExchangeRate(tenantId, id, request.rate(), request.source());
            audit.publish("finance.exchange_rate.updated", "UPDATE", "fin_exchange_rate", id, "LOW");
        }
        return toRate(opsMapper.findExchangeRate(tenantId, request.fromCurrency(), request.toCurrency(),
                request.effectiveDate()));
    }

    public List<ExchangeRateView> listExchangeRates(int limit) {
        long tenantId = FinanceContext.tenantId();
        List<ExchangeRateView> views = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listExchangeRates(tenantId, limit)) {
            views.add(toRate(row));
        }
        return views;
    }

    // ---------- 业财规则 ----------

    @Transactional
    public BusinessRuleView saveRule(BusinessRuleUpsertRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.ruleCode() == null || request.ruleCode().isBlank()
                || request.ruleName() == null || request.ruleName().isBlank()
                || request.ruleType() == null || request.ruleType().isBlank() || request.config() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        String configJson = writeJson(request.config());
        Map<String, Object> existing = opsMapper.findRule(tenantId, request.ruleCode());
        if (existing == null) {
            long id = IdWorker.getId();
            opsMapper.insertRule(id, tenantId, request.ruleCode(), request.ruleName(), request.ruleType(),
                    configJson, FinanceContext.userId());
            audit.publish("finance.rule.created", "CREATE", "fin_business_rule", id, "LOW");
        } else {
            long id = Rows.lngVal(existing, "id");
            opsMapper.updateRule(tenantId, id, request.ruleName(), request.ruleType(), configJson);
            audit.publish("finance.rule.updated", "UPDATE", "fin_business_rule", id, "LOW");
        }
        return toRule(opsMapper.findRule(tenantId, request.ruleCode()));
    }

    public List<BusinessRuleView> listRules() {
        long tenantId = FinanceContext.tenantId();
        List<BusinessRuleView> views = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listRules(tenantId)) {
            views.add(toRule(row));
        }
        return views;
    }

    // ---------- 付款计划与预算查询 ----------

    public List<PaymentPlanLine> paymentPlan(int limit) {
        long tenantId = FinanceContext.tenantId();
        List<PaymentPlanLine> lines = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listPaymentPlan(tenantId, limit)) {
            lines.add(new PaymentPlanLine(Rows.lngVal(row, "payableId"), Rows.str(row, "apNo"),
                    Rows.str(row, "supplierName"), Rows.str(row, "currencyCode"), Rows.str(row, "dueDate"),
                    Rows.str(row, "plannedPayDate"), Rows.dec(row, "amount"),
                    Rows.dec(row, "settledAmount"), Rows.dec(row, "outstanding")));
        }
        return lines;
    }

    public Map<String, Object> budgetAvailability(Integer periodYear, String dimension, Long dimensionId,
                                                  String subjectCode) {
        long tenantId = FinanceContext.tenantId();
        int year = periodYear == null ? LocalDate.now().getYear() : periodYear;
        String dim = dimension == null || dimension.isBlank() ? "DEPARTMENT" : dimension;
        Map<String, Object> budget = expenseMapper.findBudgetForUse(tenantId, year, dim, dimensionId,
                subjectCode == null ? "" : subjectCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", budget != null);
        result.put("period_year", year);
        result.put("dimension", dim);
        result.put("dimension_id", dimensionId);
        result.put("subject_code", subjectCode);
        if (budget != null) {
            result.put("budget_id", Rows.lngVal(budget, "id"));
            result.put("budget_amount", Rows.dec(budget, "budgetAmount"));
            result.put("occupied_amount", Rows.dec(budget, "occupiedAmount"));
            result.put("executed_amount", Rows.dec(budget, "executedAmount"));
            result.put("available", Rows.dec(budget, "available"));
            result.put("control_mode", Rows.str(budget, "controlMode"));
        }
        return result;
    }

    // ---------- 转换 ----------

    private VoucherTemplateView toTemplate(Map<String, Object> row) {
        Map<String, Object> mapping;
        try {
            mapping = objectMapper.readValue(Rows.str(row, "auxiliaryMapping"),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("凭证模板辅助核算映射解析失败", e);
        }
        return new VoucherTemplateView(Rows.lngVal(row, "id"), Rows.str(row, "templateCode"),
                Rows.str(row, "templateName"), Rows.str(row, "bizType"), Rows.str(row, "debitSubjectCode"),
                Rows.str(row, "creditSubjectCode"), mapping, Rows.str(row, "conditionExpr"),
                Rows.str(row, "status"));
    }

    private static InvoiceView toInvoice(Map<String, Object> row) {
        return new InvoiceView(Rows.lngVal(row, "id"), Rows.str(row, "invoiceCode"),
                Rows.str(row, "invoiceNumber"), Rows.str(row, "invoiceType"), Rows.str(row, "buyerName"),
                Rows.str(row, "sellerName"), Rows.dec(row, "amount"), Rows.dec(row, "taxRate"),
                Rows.dec(row, "taxAmount"), Rows.dec(row, "totalAmount"), Rows.str(row, "invoiceDate"),
                Rows.str(row, "verifyStatus"), Rows.str(row, "status"), Rows.lng(row, "sourceBizId"));
    }

    private static CurrencyView toCurrency(Map<String, Object> row) {
        return new CurrencyView(Rows.lngVal(row, "id"), Rows.str(row, "currencyCode"),
                Rows.str(row, "currencyName"), Rows.str(row, "symbol"),
                Rows.intVal(row, "decimalPlaces"), Rows.str(row, "status"));
    }

    private static ExchangeRateView toRate(Map<String, Object> row) {
        return new ExchangeRateView(Rows.lngVal(row, "id"), Rows.str(row, "fromCurrency"),
                Rows.str(row, "toCurrency"), Rows.dec(row, "rate"), Rows.str(row, "effectiveDate"),
                Rows.str(row, "source"));
    }

    private BusinessRuleView toRule(Map<String, Object> row) {
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(Rows.str(row, "configJson"),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("业财规则配置解析失败", e);
        }
        return new BusinessRuleView(Rows.lngVal(row, "id"), Rows.str(row, "ruleCode"),
                Rows.str(row, "ruleName"), Rows.str(row, "ruleType"), config, Rows.str(row, "status"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
    }
}
