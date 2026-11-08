package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.ArapMapper;
import com.fenglin.finance.mapper.BankMapper;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fenglin.finance.web.dto.FinanceDtos.AgingLine;
import com.fenglin.finance.web.dto.FinanceDtos.AgingReport;
import com.fenglin.finance.web.dto.FinanceDtos.PayableView;
import com.fenglin.finance.web.dto.FinanceDtos.PaymentCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ReceiptCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ReceivableView;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementDocResult;
import com.fenglin.finance.web.dto.FinanceDtos.WriteOffResult;

/**
 * D1 应收应付、收付款与核销。
 *
 * <p>偏差标注：收付款默认取租户第一个账套生成凭证（文档要求按账户绑定账套，主链先兜底）；
 * 外币折算同凭证固定 1:1。</p>
 */
@Service
@Transactional(readOnly = true)
public class ArapService {

    private final ArapMapper arapMapper;
    private final BankMapper bankMapper;
    private final LedgerMapper ledgerMapper;
    private final CommonsMapper commonsMapper;
    private final VoucherService voucherService;
    private final FinanceAudit audit;

    public ArapService(ArapMapper arapMapper, BankMapper bankMapper, LedgerMapper ledgerMapper,
                       CommonsMapper commonsMapper, VoucherService voucherService, FinanceAudit audit) {
        this.arapMapper = arapMapper;
        this.bankMapper = bankMapper;
        this.ledgerMapper = ledgerMapper;
        this.commonsMapper = commonsMapper;
        this.voucherService = voucherService;
        this.audit = audit;
    }

    public List<ReceivableView> listReceivables(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<ReceivableView> views = new ArrayList<>();
        for (Map<String, Object> row : arapMapper.listReceivables(tenantId, status, limit, offset)) {
            views.add(new ReceivableView(Rows.lngVal(row, "id"), Rows.str(row, "arNo"),
                    Rows.lng(row, "customerId"), Rows.str(row, "customerName"), Rows.str(row, "sourceType"),
                    Rows.lng(row, "projectId"), Rows.str(row, "currencyCode"), Rows.dec(row, "amount"),
                    Rows.dec(row, "settledAmount"), Rows.dec(row, "outstanding"), Rows.str(row, "billDate"),
                    Rows.str(row, "dueDate"), Rows.str(row, "agingBucket"), Rows.str(row, "status")));
        }
        return views;
    }

    public List<PayableView> listPayables(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<PayableView> views = new ArrayList<>();
        for (Map<String, Object> row : arapMapper.listPayables(tenantId, status, limit, offset)) {
            views.add(new PayableView(Rows.lngVal(row, "id"), Rows.str(row, "apNo"),
                    Rows.lng(row, "supplierId"), Rows.str(row, "supplierName"), Rows.str(row, "sourceType"),
                    Rows.lng(row, "projectId"), Rows.str(row, "currencyCode"), Rows.dec(row, "amount"),
                    Rows.dec(row, "settledAmount"), Rows.dec(row, "outstanding"), Rows.str(row, "billDate"),
                    Rows.str(row, "dueDate"), Rows.str(row, "status")));
        }
        return views;
    }

    /** 项目结算/收入确认生成应收。 */
    @Transactional
    public long createReceivable(String sourceType, Long sourceBizId, Long projectId, String contractNo,
                                 Long customerId, String customerName, String currencyCode, BigDecimal amount,
                                 LocalDate billDate, LocalDate dueDate) {
        long tenantId = FinanceContext.tenantId();
        long id = IdWorker.getId();
        String arNo = "AR-" + String.format("%06d", commonsMapper.nextArNo());
        arapMapper.insertReceivable(id, tenantId, arNo, customerId, customerName, sourceType, sourceBizId,
                projectId, contractNo, currencyCode, FinanceContext.amount(amount), billDate, dueDate,
                FinanceContext.userId());
        return id;
    }

    /** 报销审批生成应付。 */
    @Transactional
    public long createPayable(String sourceType, Long sourceBizId, Long projectId, Long supplierId,
                              String supplierName, String currencyCode, BigDecimal amount,
                              LocalDate billDate, LocalDate dueDate) {
        long tenantId = FinanceContext.tenantId();
        long id = IdWorker.getId();
        String apNo = "AP-" + String.format("%06d", commonsMapper.nextApNo());
        arapMapper.insertPayable(id, tenantId, apNo, supplierId, supplierName, sourceType, sourceBizId,
                projectId, currencyCode, FinanceContext.amount(amount), billDate, dueDate, null,
                FinanceContext.userId());
        return id;
    }

    @Transactional
    public SettlementDocResult receive(ReceiptCreateRequest request, String idempotencyKey) {
        long tenantId = FinanceContext.tenantId();
        String key = FinanceContext.idempotencyKey(idempotencyKey);
        if (key != null) {
            Long existing = commonsMapper.findIdempotentBizId(tenantId, key);
            if (existing != null) {
                return receiptResult(tenantId, existing);
            }
        }
        if (request.amount() == null || request.amount().signum() <= 0
                || request.receivableIds() == null || request.receivableIds().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        LocalDate date = request.receiptDate() == null ? LocalDate.now() : request.receiptDate();
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? "CNY" : request.currencyCode();
        long receiptId = IdWorker.getId();
        String receiptNo = "RC-" + String.format("%06d", commonsMapper.nextReceiptNo());
        arapMapper.insertReceipt(receiptId, tenantId, receiptNo, request.customerId(), currency,
                FinanceContext.amount(request.amount()), request.payMethod() == null ? "BANK" : request.payMethod(),
                request.bankAccountId(), date, key, FinanceContext.userId());
        List<WriteOffResult> writeOffs = allocateReceivables(tenantId, request.receivableIds(),
                FinanceContext.amount(request.amount()), date, "RECEIPT", receiptId, currency);
        long ledgerId = defaultLedgerId(tenantId);
        String bankSubject = bankSubject(tenantId, request.bankAccountId());
        long voucherId = voucherService.createSystem(ledgerId, date, "收款 " + receiptNo, "RECEIPT", receiptId,
                "POSTED", currency, List.of(
                        new VoucherService.EntryDraft("收到货款", bankSubject, "DEBIT",
                                FinanceContext.amount(request.amount())),
                        new VoucherService.EntryDraft("核销应收", "1122", "CREDIT",
                                FinanceContext.amount(request.amount()))));
        arapMapper.confirmReceipt(tenantId, receiptId, voucherId);
        if (key != null) {
            commonsMapper.insertIdempotent(IdWorker.getId(), tenantId, key, "POST /api/finance/v1/receipts",
                    receiptId, FinanceContext.userId());
        }
        audit.publish("fin_receipt.confirmed", "CREATE", "fin_receipt", receiptId, "MEDIUM");
        return new SettlementDocResult(receiptId, receiptNo, "CONFIRMED",
                FinanceContext.amount(request.amount()), FinanceContext.amount(request.amount()), voucherId,
                writeOffs);
    }

    @Transactional
    public SettlementDocResult pay(PaymentCreateRequest request, String idempotencyKey) {
        long tenantId = FinanceContext.tenantId();
        String key = FinanceContext.idempotencyKey(idempotencyKey);
        if (key != null) {
            Long existing = commonsMapper.findIdempotentBizId(tenantId, key);
            if (existing != null) {
                return paymentResult(tenantId, existing);
            }
        }
        if (request.amount() == null || request.amount().signum() <= 0
                || request.payableIds() == null || request.payableIds().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        LocalDate date = request.paymentDate() == null ? LocalDate.now() : request.paymentDate();
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? "CNY" : request.currencyCode();
        long paymentId = IdWorker.getId();
        String paymentNo = "PM-" + String.format("%06d", commonsMapper.nextPaymentNo());
        arapMapper.insertPayment(paymentId, tenantId, paymentNo, request.supplierId(), currency,
                FinanceContext.amount(request.amount()), request.payMethod() == null ? "BANK" : request.payMethod(),
                request.bankAccountId(), date, key, FinanceContext.userId());
        List<WriteOffResult> writeOffs = allocatePayables(tenantId, request.payableIds(),
                FinanceContext.amount(request.amount()), date, "PAYMENT", paymentId, currency);
        long ledgerId = defaultLedgerId(tenantId);
        String bankSubject = bankSubject(tenantId, request.bankAccountId());
        long voucherId = voucherService.createSystem(ledgerId, date, "付款 " + paymentNo, "PAYMENT", paymentId,
                "POSTED", currency, List.of(
                        new VoucherService.EntryDraft("核销应付", "2202", "DEBIT",
                                FinanceContext.amount(request.amount())),
                        new VoucherService.EntryDraft("支付货款", bankSubject, "CREDIT",
                                FinanceContext.amount(request.amount()))));
        arapMapper.confirmPayment(tenantId, paymentId, voucherId);
        if (key != null) {
            commonsMapper.insertIdempotent(IdWorker.getId(), tenantId, key, "POST /api/finance/v1/payments",
                    paymentId, FinanceContext.userId());
        }
        audit.publish("fin_payment.confirmed", "CREATE", "fin_payment", paymentId, "MEDIUM");
        return new SettlementDocResult(paymentId, paymentNo, "CONFIRMED",
                FinanceContext.amount(request.amount()), FinanceContext.amount(request.amount()), voucherId,
                writeOffs);
    }

    /** 手工核销（无收付款单据），用于预收预付冲抵。 */
    @Transactional
    public WriteOffResult writeOff(String bizType, long sourceId, BigDecimal amount, LocalDate date) {
        long tenantId = FinanceContext.tenantId();
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        LocalDate writeOffDate = date == null ? LocalDate.now() : date;
        if ("AR".equals(bizType)) {
            Map<String, Object> receivable = arapMapper.lockReceivable(tenantId, sourceId);
            if (receivable == null) {
                throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
            }
            BigDecimal outstanding = Rows.dec(receivable, "outstanding");
            if (outstanding.compareTo(amount) < 0) {
                throw new BizException(FinanceErrorCode.PARAM_INVALID);
            }
            return recordWriteOff(tenantId, "AR", sourceId, amount, writeOffDate, "MANUAL", 0L,
                    Rows.str(receivable, "currencyCode"), outstanding);
        }
        Map<String, Object> payable = arapMapper.lockPayable(tenantId, sourceId);
        if (payable == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        BigDecimal outstanding = Rows.dec(payable, "outstanding");
        if (outstanding.compareTo(amount) < 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        return recordWriteOff(tenantId, "AP", sourceId, amount, writeOffDate, "MANUAL", 0L,
                Rows.str(payable, "currencyCode"), outstanding);
    }

    public AgingReport aging(String type) {
        long tenantId = FinanceContext.tenantId();
        boolean receivable = !"AP".equalsIgnoreCase(type);
        List<Map<String, Object>> rows = receivable ? arapMapper.receivableAging(tenantId)
                : arapMapper.payableAging(tenantId);
        List<AgingLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal amount = Rows.dec(row, "amount");
            total = total.add(amount);
            lines.add(new AgingLine(Rows.str(row, "partyName"), Rows.str(row, "bucket"), amount));
        }
        return new AgingReport(receivable ? "AR" : "AP", lines, FinanceContext.amount(total));
    }

    private List<WriteOffResult> allocateReceivables(long tenantId, List<Long> ids, BigDecimal amount,
                                                     LocalDate date, String docType, long docId, String currency) {
        List<WriteOffResult> results = new ArrayList<>();
        BigDecimal remaining = amount;
        for (Long id : ids) {
            Map<String, Object> receivable = arapMapper.lockReceivable(tenantId, id);
            if (receivable == null) {
                throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
            }
            BigDecimal outstanding = Rows.dec(receivable, "outstanding");
            BigDecimal allocation = remaining.min(outstanding);
            if (allocation.signum() <= 0) {
                continue;
            }
            results.add(recordWriteOff(tenantId, "AR", id, allocation, date, docType, docId, currency,
                    outstanding));
            remaining = remaining.subtract(allocation);
        }
        if (remaining.signum() > 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        return results;
    }

    private List<WriteOffResult> allocatePayables(long tenantId, List<Long> ids, BigDecimal amount,
                                                  LocalDate date, String docType, long docId, String currency) {
        List<WriteOffResult> results = new ArrayList<>();
        BigDecimal remaining = amount;
        for (Long id : ids) {
            Map<String, Object> payable = arapMapper.lockPayable(tenantId, id);
            if (payable == null) {
                throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
            }
            BigDecimal outstanding = Rows.dec(payable, "outstanding");
            BigDecimal allocation = remaining.min(outstanding);
            if (allocation.signum() <= 0) {
                continue;
            }
            results.add(recordWriteOff(tenantId, "AP", id, allocation, date, docType, docId, currency,
                    outstanding));
            remaining = remaining.subtract(allocation);
        }
        if (remaining.signum() > 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        return results;
    }

    private WriteOffResult recordWriteOff(long tenantId, String bizType, long sourceId, BigDecimal amount,
                                          LocalDate date, String docType, long docId, String currency,
                                          BigDecimal outstanding) {
        long writeOffId = IdWorker.getId();
        String writeOffNo = "WO-" + String.format("%06d", commonsMapper.nextWriteOffNo());
        arapMapper.insertWriteOff(writeOffId, tenantId, writeOffNo, bizType, sourceId, docType, docId,
                currency == null ? "CNY" : currency, FinanceContext.amount(amount), date,
                FinanceContext.userId());
        BigDecimal settled = amount;
        if ("AR".equals(bizType)) {
            BigDecimal newOutstanding = outstanding.subtract(amount);
            arapMapper.updateReceivableSettled(tenantId, sourceId, settled,
                    newOutstanding.signum() <= 0 ? "SETTLED" : "PARTIAL");
        } else {
            BigDecimal newOutstanding = outstanding.subtract(amount);
            arapMapper.updatePayableSettled(tenantId, sourceId, settled,
                    newOutstanding.signum() <= 0 ? "SETTLED" : "PARTIAL");
        }
        return new WriteOffResult(writeOffId, writeOffNo, sourceId, FinanceContext.amount(amount));
    }

    private SettlementDocResult receiptResult(long tenantId, long receiptId) {
        Map<String, Object> receipt = arapMapper.findReceipt(tenantId, receiptId);
        if (receipt == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return new SettlementDocResult(receiptId, Rows.str(receipt, "receiptNo"), Rows.str(receipt, "status"),
                Rows.dec(receipt, "amount"), Rows.dec(receipt, "amount"), Rows.lng(receipt, "voucherId"),
                List.of());
    }

    private SettlementDocResult paymentResult(long tenantId, long paymentId) {
        Map<String, Object> payment = arapMapper.findPayment(tenantId, paymentId);
        if (payment == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return new SettlementDocResult(paymentId, Rows.str(payment, "paymentNo"), Rows.str(payment, "status"),
                Rows.dec(payment, "amount"), Rows.dec(payment, "amount"), Rows.lng(payment, "voucherId"),
                List.of());
    }

    private String bankSubject(long tenantId, Long bankAccountId) {
        if (bankAccountId == null) {
            return "1002";
        }
        Map<String, Object> account = bankMapper.findAccount(tenantId, bankAccountId);
        String subject = account == null ? null : Rows.str(account, "subjectCode");
        return subject == null || subject.isBlank() ? "1002" : subject;
    }

    private long defaultLedgerId(long tenantId) {
        List<Map<String, Object>> ledgers = ledgerMapper.listLedgers(tenantId);
        if (ledgers.isEmpty()) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return Rows.lngVal(ledgers.get(0), "id");
    }
}
