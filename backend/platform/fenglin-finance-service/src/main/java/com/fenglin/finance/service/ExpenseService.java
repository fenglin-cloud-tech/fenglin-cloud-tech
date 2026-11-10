package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.client.WorkflowClient;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.ExpenseMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetControlCheckRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetControlResult;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BudgetView;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseActionResult;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseItemRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseItemView;
import com.fenglin.finance.web.dto.FinanceDtos.ExpensePayRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ExpenseView;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.InvoiceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D1 费用报销、预算控制与发票。
 *
 * <p>偏差标注：报销提交按“部门 + 费用科目”匹配当年预算，预算维度与科目来自第一条明细；
 * 文档 F-D1-05 的预算调整/预算转移流程留待收口。</p>
 */
@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final CommonsMapper commonsMapper;
    private final LedgerMapper ledgerMapper;
    private final VoucherService voucherService;
    private final ArapService arapService;
    private final WorkflowClient workflowClient;
    private final FinanceAudit audit;

    public ExpenseService(ExpenseMapper expenseMapper, CommonsMapper commonsMapper, LedgerMapper ledgerMapper,
                          VoucherService voucherService, ArapService arapService,
                          WorkflowClient workflowClient, FinanceAudit audit) {
        this.expenseMapper = expenseMapper;
        this.commonsMapper = commonsMapper;
        this.ledgerMapper = ledgerMapper;
        this.voucherService = voucherService;
        this.arapService = arapService;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    @Transactional
    public ExpenseView create(ExpenseCreateRequest request, String idempotencyKey) {
        long tenantId = FinanceContext.tenantId();
        String key = FinanceContext.idempotencyKey(idempotencyKey);
        if (key != null) {
            Long existing = commonsMapper.findIdempotentBizId(tenantId, key);
            if (existing != null) {
                return detail(existing);
            }
        }
        if (request.applicantId() == null || request.items() == null || request.items().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (ExpenseItemRequest item : request.items()) {
            if (item.amount() == null || item.amount().signum() <= 0) {
                throw new BizException(FinanceErrorCode.PARAM_INVALID);
            }
            total = total.add(item.amount());
            if (item.taxAmount() != null) {
                tax = tax.add(item.taxAmount());
            }
        }
        LocalDate claimDate = request.claimDate() == null ? LocalDate.now() : request.claimDate();
        long id = IdWorker.getId();
        String claimNo = "EX-" + String.format("%06d", commonsMapper.nextClaimNo());
        expenseMapper.insertClaim(id, tenantId, claimNo, claimDate, request.applicantId(),
                request.applicantName(), request.deptId(), request.claimType(),
                request.currencyCode() == null || request.currencyCode().isBlank() ? "CNY"
                        : request.currencyCode(),
                FinanceContext.amount(total), FinanceContext.amount(tax), request.projectId(), key,
                FinanceContext.userId());
        int lineNo = 1;
        for (ExpenseItemRequest item : request.items()) {
            expenseMapper.insertClaimItem(IdWorker.getId(), tenantId, id, lineNo++,
                    item.expenseDate() == null ? claimDate : item.expenseDate(), item.expenseCategory(),
                    item.subjectCode() == null || item.subjectCode().isBlank() ? "6602" : item.subjectCode(),
                    FinanceContext.amount(item.amount()), FinanceContext.amount(item.taxAmount()),
                    item.invoiceId(), item.attachmentId(), item.remark(), FinanceContext.userId());
        }
        if (key != null) {
            commonsMapper.insertIdempotent(IdWorker.getId(), tenantId, key, "POST /api/finance/v1/expenses",
                    id, FinanceContext.userId());
        }
        audit.publish("fin_expense.created", "CREATE", "fin_expense_claim", id, "LOW");
        return detail(id);
    }

    @Transactional
    public ExpenseActionResult submit(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> claim = requireClaim(tenantId, id);
        if (!"DRAFT".equals(Rows.str(claim, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        BigDecimal total = Rows.dec(claim, "totalAmount");
        List<Map<String, Object>> items = expenseMapper.listClaimItems(tenantId, id);
        String subjectCode = items.isEmpty() ? "6602" : Rows.str(items.get(0), "subjectCode");
        int year = LocalDate.parse(Rows.str(claim, "claimDate")).getYear();
        Map<String, Object> budget = expenseMapper.findBudgetForUse(tenantId, year, "DEPARTMENT",
                Rows.lng(claim, "deptId"), subjectCode);
        Long budgetId = null;
        BigDecimal occupied = BigDecimal.ZERO;
        if (budget != null) {
            long candidateId = Rows.lngVal(budget, "id");
            String controlMode = Rows.str(budget, "controlMode");
            int updated = expenseMapper.occupyBudget(tenantId, candidateId, total);
            if (updated > 0) {
                budgetId = candidateId;
                occupied = total;
                expenseMapper.insertBudgetControl(IdWorker.getId(), tenantId, candidateId, "EXPENSE", id,
                        "OCCUPY", total, OffsetDateTime.now(), FinanceContext.userId());
            } else if ("STRICT".equals(controlMode)) {
                throw new BizException(FinanceErrorCode.BUDGET_INSUFFICIENT);
            }
        }
        if (expenseMapper.markClaimSubmitted(tenantId, id, budgetId, occupied) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        String instance = workflowClient.startApproval("biz:finance:expense:submit", null,
                "financeExpenseClaim", Rows.str(claim, "claimNo"), "FIN_EXPENSE",
                "费用报销 " + Rows.str(claim, "claimNo"), Map.of("claim_id", id, "amount", total));
        audit.publish("fin_expense.submitted", "SUBMIT", "fin_expense_claim", id, "MEDIUM");
        return new ExpenseActionResult(id, Rows.str(claim, "claimNo"), "SUBMITTED", instance);
    }

    @Transactional
    public ExpenseActionResult approve(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> claim = requireClaim(tenantId, id);
        if (!"SUBMITTED".equals(Rows.str(claim, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        LocalDate claimDate = LocalDate.parse(Rows.str(claim, "claimDate"));
        long payableId = arapService.createPayable("EXPENSE", id, Rows.lng(claim, "projectId"), null,
                Rows.str(claim, "applicantName"),
                Rows.str(claim, "currencyCode"), Rows.dec(claim, "totalAmount"), claimDate, claimDate);
        if (expenseMapper.markClaimApproved(tenantId, id, payableId) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_expense.approved", "APPROVE", "fin_expense_claim", id, "MEDIUM");
        return new ExpenseActionResult(id, Rows.str(claim, "claimNo"), "APPROVED", null);
    }

    @Transactional
    public ExpenseActionResult reject(long id, String reason) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> claim = requireClaim(tenantId, id);
        BigDecimal occupied = Rows.dec(claim, "budgetOccupied");
        Long budgetId = Rows.lng(claim, "budgetId");
        if (expenseMapper.markClaimRejected(tenantId, id) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        if (budgetId != null && occupied != null && occupied.signum() > 0) {
            expenseMapper.releaseBudgetAmount(tenantId, budgetId, occupied);
            expenseMapper.insertBudgetControl(IdWorker.getId(), tenantId, budgetId, "EXPENSE", id,
                    "RELEASE", occupied, OffsetDateTime.now(), FinanceContext.userId());
        }
        audit.publish("fin_expense.rejected", "REJECT", "fin_expense_claim", id, "MEDIUM");
        return new ExpenseActionResult(id, Rows.str(claim, "claimNo"), "REJECTED", reason);
    }

    @Transactional
    public ExpenseActionResult pay(long id, ExpensePayRequest request, String idempotencyKey) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> claim = requireClaim(tenantId, id);
        if (!"APPROVED".equals(Rows.str(claim, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        LocalDate date = request == null || request.paymentDate() == null
                ? LocalDate.now() : request.paymentDate();
        Long budgetId = Rows.lng(claim, "budgetId");
        BigDecimal occupied = Rows.dec(claim, "budgetOccupied");
        if (budgetId != null && occupied != null && occupied.signum() > 0) {
            expenseMapper.consumeBudget(tenantId, budgetId, occupied);
            expenseMapper.insertBudgetControl(IdWorker.getId(), tenantId, budgetId, "EXPENSE", id,
                    "CONSUME", occupied, OffsetDateTime.now(), FinanceContext.userId());
        }
        List<VoucherService.EntryDraft> drafts = new ArrayList<>();
        for (Map<String, Object> item : expenseMapper.listClaimItems(tenantId, id)) {
            drafts.add(new VoucherService.EntryDraft(Rows.str(item, "expenseCategory"),
                    Rows.str(item, "subjectCode"), "DEBIT", Rows.dec(item, "amount")));
        }
        BigDecimal total = Rows.dec(claim, "totalAmount");
        drafts.add(new VoucherService.EntryDraft("报销付款", "1002", "CREDIT", total));
        long ledgerId = defaultLedgerId();
        long voucherId = voucherService.createSystem(ledgerId, date, "报销付款 " + Rows.str(claim, "claimNo"),
                "EXPENSE", id, "POSTED", Rows.str(claim, "currencyCode"), drafts);
        if (expenseMapper.markClaimPaid(tenantId, id, voucherId) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_expense.paid", "PAY", "fin_expense_claim", id, "MEDIUM");
        return new ExpenseActionResult(id, Rows.str(claim, "claimNo"), "PAID", null);
    }

    public ExpenseView detail(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> claim = requireClaim(tenantId, id);
        List<ExpenseItemView> items = new ArrayList<>();
        for (Map<String, Object> item : expenseMapper.listClaimItems(tenantId, id)) {
            items.add(new ExpenseItemView(Rows.intVal(item, "lineNo"), Rows.str(item, "expenseDate"),
                    Rows.str(item, "expenseCategory"), Rows.str(item, "subjectCode"),
                    Rows.dec(item, "amount"), Rows.dec(item, "taxAmount"), Rows.lng(item, "invoiceId"),
                    Rows.lng(item, "attachmentId"), Rows.str(item, "remark")));
        }
        return new ExpenseView(id, Rows.str(claim, "claimNo"), Rows.str(claim, "claimDate"),
                Rows.lngVal(claim, "applicantId"), Rows.str(claim, "applicantName"), Rows.lng(claim, "deptId"),
                Rows.str(claim, "claimType"), Rows.str(claim, "currencyCode"), Rows.dec(claim, "totalAmount"),
                Rows.dec(claim, "taxAmount"), Rows.lng(claim, "budgetId"), Rows.dec(claim, "budgetOccupied"),
                Rows.lng(claim, "projectId"), Rows.str(claim, "status"), Rows.lng(claim, "payableId"),
                Rows.lng(claim, "voucherId"), Rows.instant(claim, "submittedAt"),
                Rows.instant(claim, "paidAt"), items);
    }

    public List<ExpenseView> list(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<ExpenseView> views = new ArrayList<>();
        for (Map<String, Object> row : expenseMapper.listClaims(tenantId, status, limit, offset)) {
            views.add(claimView(row));
        }
        return views;
    }

    /**
     * 员工自助：强制按当前登录用户收敛，前端不再需要按 applicant_id 过滤。
     */
    public List<ExpenseView> listMine(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        long userId = FinanceContext.userId();
        List<ExpenseView> views = new ArrayList<>();
        for (Map<String, Object> row : expenseMapper.listClaimsByApplicant(tenantId, userId, status,
                limit, offset)) {
            views.add(claimView(row));
        }
        return views;
    }

    private ExpenseView claimView(Map<String, Object> row) {
        return new ExpenseView(Rows.lngVal(row, "id"), Rows.str(row, "claimNo"),
                Rows.str(row, "claimDate"), Rows.lngVal(row, "applicantId"),
                Rows.str(row, "applicantName"), null, Rows.str(row, "claimType"), null,
                Rows.dec(row, "totalAmount"), null, Rows.lng(row, "budgetId"),
                Rows.dec(row, "budgetOccupied"), null, Rows.str(row, "status"),
                Rows.lng(row, "payableId"), Rows.lng(row, "voucherId"), null, null, List.of());
    }

    // ---------- 预算 ----------

    @Transactional
    public BudgetView createBudget(BudgetCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.budgetNo() == null || request.budgetNo().isBlank() || request.budgetAmount() == null
                || request.budgetAmount().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        int year = request.periodYear() == null ? LocalDate.now().getYear() : request.periodYear();
        expenseMapper.insertBudget(id, tenantId, request.budgetNo(), request.budgetName(), year,
                request.dimension() == null || request.dimension().isBlank()
                        ? "DEPARTMENT" : request.dimension(),
                request.dimensionId(), request.subjectCode(),
                request.currencyCode() == null || request.currencyCode().isBlank()
                        ? "CNY" : request.currencyCode(),
                FinanceContext.amount(request.budgetAmount()),
                request.controlMode() == null || request.controlMode().isBlank()
                        ? "STRICT" : request.controlMode(),
                FinanceContext.userId());
        audit.publish("fin_budget.created", "CREATE", "fin_budget", id, "LOW");
        return budgetView(expenseMapper.findBudget(tenantId, id));
    }

    @Transactional
    public BudgetView releaseBudget(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> budget = expenseMapper.findBudget(tenantId, id);
        if (budget == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (expenseMapper.releaseBudget(tenantId, id) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_budget.released", "RELEASE", "fin_budget", id, "MEDIUM");
        return budgetView(expenseMapper.findBudget(tenantId, id));
    }

    public List<BudgetView> listBudgets(Integer periodYear, String dimension, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<BudgetView> views = new ArrayList<>();
        for (Map<String, Object> row : expenseMapper.listBudgets(tenantId, periodYear, dimension, limit, offset)) {
            views.add(budgetView(row));
        }
        return views;
    }

    public BudgetControlResult checkBudget(BudgetControlCheckRequest request) {
        long tenantId = FinanceContext.tenantId();
        int year = request.periodYear() == null ? LocalDate.now().getYear() : request.periodYear();
        Map<String, Object> budget = expenseMapper.findBudgetForUse(tenantId, year, request.dimension(),
                request.dimensionId(), request.subjectCode());
        if (budget == null) {
            return new BudgetControlResult(null, null, null, null, null, null, true, "无匹配预算，放行");
        }
        BigDecimal available = Rows.dec(budget, "available");
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        boolean allowed = available.compareTo(amount) >= 0;
        String mode = Rows.str(budget, "controlMode");
        if (!allowed && "WARN".equals(mode)) {
            return new BudgetControlResult(Rows.lngVal(budget, "id"), mode, Rows.dec(budget, "budgetAmount"),
                    Rows.dec(budget, "occupiedAmount"), Rows.dec(budget, "executedAmount"), available,
                    true, "预算不足，按提示放行");
        }
        return new BudgetControlResult(Rows.lngVal(budget, "id"), mode, Rows.dec(budget, "budgetAmount"),
                Rows.dec(budget, "occupiedAmount"), Rows.dec(budget, "executedAmount"), available,
                allowed, allowed ? "预算充足" : "预算不足");
    }

    // ---------- 发票 ----------

    @Transactional
    public InvoiceView createInvoice(InvoiceCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.invoiceNumber() == null || request.invoiceNumber().isBlank() || request.amount() == null
                || request.amount().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        BigDecimal rate = request.taxRate() == null ? BigDecimal.ZERO : request.taxRate();
        BigDecimal tax = request.amount().multiply(rate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal total = request.amount().add(tax);
        long id = IdWorker.getId();
        expenseMapper.insertInvoice(id, tenantId, request.invoiceCode(), request.invoiceNumber(),
                request.invoiceType() == null || request.invoiceType().isBlank()
                        ? "VAT_SPECIAL" : request.invoiceType(),
                request.buyerName(), request.sellerName(), FinanceContext.amount(request.amount()), rate,
                FinanceContext.amount(tax), FinanceContext.amount(total),
                request.invoiceDate() == null ? LocalDate.now() : request.invoiceDate(), "VALID",
                request.sourceBizId(), request.fileId(), FinanceContext.userId());
        audit.publish("fin_invoice.created", "CREATE", "fin_invoice", id, "LOW");
        return invoiceView(expenseMapper.findInvoice(tenantId, id));
    }

    public List<InvoiceView> listInvoices(String invoiceType, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<InvoiceView> views = new ArrayList<>();
        for (Map<String, Object> row : expenseMapper.listInvoices(tenantId, invoiceType, limit, offset)) {
            views.add(invoiceView(row));
        }
        return views;
    }

    private Map<String, Object> requireClaim(long tenantId, long id) {
        Map<String, Object> claim = expenseMapper.findClaim(tenantId, id);
        if (claim == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return claim;
    }

    private long defaultLedgerId() {
        long tenantId = FinanceContext.tenantId();
        List<Map<String, Object>> ledgers = ledgerMapper.listLedgers(tenantId);
        if (ledgers.isEmpty()) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return Rows.lngVal(ledgers.get(0), "id");
    }

    private static BudgetView budgetView(Map<String, Object> row) {
        return new BudgetView(Rows.lngVal(row, "id"), Rows.str(row, "budgetNo"), Rows.str(row, "budgetName"),
                Rows.intVal(row, "periodYear"), Rows.str(row, "dimension"), Rows.lng(row, "dimensionId"),
                Rows.str(row, "subjectCode"), Rows.dec(row, "budgetAmount"),
                Rows.dec(row, "occupiedAmount"), Rows.dec(row, "executedAmount"),
                Rows.dec(row, "available"), Rows.str(row, "controlMode"), Rows.str(row, "status"));
    }

    private static InvoiceView invoiceView(Map<String, Object> row) {
        return new InvoiceView(Rows.lngVal(row, "id"), Rows.str(row, "invoiceCode"),
                Rows.str(row, "invoiceNumber"), Rows.str(row, "invoiceType"), Rows.str(row, "buyerName"),
                Rows.str(row, "sellerName"), Rows.dec(row, "amount"), Rows.dec(row, "taxRate"),
                Rows.dec(row, "taxAmount"), Rows.dec(row, "totalAmount"), Rows.str(row, "invoiceDate"),
                Rows.str(row, "verifyStatus"), Rows.str(row, "status"), Rows.lng(row, "sourceBizId"));
    }
}
