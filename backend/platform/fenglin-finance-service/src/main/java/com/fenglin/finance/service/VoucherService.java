package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.client.WorkflowClient;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.mapper.VoucherMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.support.VoucherHashChain;
import com.fenglin.finance.web.dto.FinanceDtos.AuditTrailView;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherActionResult;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherAuditLogView;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherEntryRequest;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherEntryView;
import com.fenglin.finance.web.dto.FinanceDtos.VoucherView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D1 凭证制单、审核、过账、冲销与哈希链审计。
 *
 * <p>偏差标注：文档外币凭证需按 fin_exchange_rate 记账汇率折算，主链先固定 1:1（本位币），
 * 汇率表与折算逻辑留待收口补齐；凭证号采用“字-期间-序列”格式，序列为全局 nextval。</p>
 */
@Service
@Transactional(readOnly = true)
public class VoucherService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final VoucherMapper voucherMapper;
    private final LedgerMapper ledgerMapper;
    private final CommonsMapper commonsMapper;
    private final VoucherHashChain chain;
    private final FinanceAudit audit;
    private final WorkflowClient workflowClient;

    public VoucherService(VoucherMapper voucherMapper, LedgerMapper ledgerMapper, CommonsMapper commonsMapper,
                          VoucherHashChain chain, FinanceAudit audit, WorkflowClient workflowClient) {
        this.voucherMapper = voucherMapper;
        this.ledgerMapper = ledgerMapper;
        this.commonsMapper = commonsMapper;
        this.chain = chain;
        this.audit = audit;
        this.workflowClient = workflowClient;
    }

    /** 系统自动凭证分录草稿（ARAP/报销/结算/折旧/处置使用）。 */
    public record EntryDraft(String summary, String subjectCode, String direction, BigDecimal amount) {
    }

    @Transactional
    public VoucherView create(VoucherCreateRequest request, String idempotencyKey) {
        long tenantId = FinanceContext.tenantId();
        String key = FinanceContext.idempotencyKey(idempotencyKey);
        Long existing = key == null ? null : commonsMapper.findIdempotentBizId(tenantId, key);
        if (existing != null) {
            return detail(existing);
        }
        if (request.ledgerId() == null || request.voucherDate() == null
                || request.entries() == null || request.entries().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long ledgerId = request.ledgerId();
        requireLedger(tenantId, ledgerId);
        String periodCode = periodCode(request.voucherDate());
        requireOpenPeriod(tenantId, ledgerId, periodCode);
        String word = request.voucherWord() == null || request.voucherWord().isBlank()
                ? "记" : request.voucherWord();
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? "CNY" : request.currencyCode();

        long id = IdWorker.getId();
        String voucherNo = nextVoucherNo(word, periodCode);
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<ResolvedEntry> resolved = new ArrayList<>();
        int lineNo = 1;
        for (VoucherEntryRequest entry : request.entries()) {
            ResolvedEntry item = resolve(tenantId, ledgerId, lineNo++, entry.summary(), entry.subjectCode(),
                    entry.direction(), entry.amount(), entry.auxiliary() == null ? null : toJson(entry.auxiliary()),
                    entry.quantity(), entry.price());
            if ("DEBIT".equals(item.direction())) {
                totalDebit = totalDebit.add(item.amount());
            } else {
                totalCredit = totalCredit.add(item.amount());
            }
            resolved.add(item);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BizException(FinanceErrorCode.VOUCHER_UNBALANCED);
        }
        voucherMapper.insertVoucher(id, tenantId, ledgerId, word, voucherNo, periodCode, request.voucherDate(),
                request.summary(), currency, FinanceContext.amount(totalDebit), FinanceContext.amount(totalCredit),
                request.sourceType() == null ? "MANUAL" : request.sourceType(), request.sourceBizId(),
                "DRAFT", FinanceContext.userId());
        for (ResolvedEntry item : resolved) {
            insertEntry(tenantId, id, periodCode, item, currency);
        }
        chain.append(tenantId, id, "CREATED", FinanceContext.userId(),
                voucherNo + "|" + totalDebit + "|" + totalCredit);
        audit.publish("fin_voucher.created", "CREATE", "fin_voucher", id, "MEDIUM");
        if (key != null) {
            commonsMapper.insertIdempotent(IdWorker.getId(), tenantId, key, "POST /api/finance/v1/vouchers",
                    id, FinanceContext.userId());
        }
        return detail(id);
    }

    @Transactional
    public VoucherActionResult submit(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> voucher = requireVoucher(tenantId, id);
        requireOpenPeriod(tenantId, Rows.lngVal(voucher, "ledgerId"), Rows.str(voucher, "periodCode"));
        if (voucherMapper.markSubmitted(tenantId, id) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        String instance = workflowClient.startApproval("biz:finance:voucher:submit", null,
                "financeVoucherApproval", Rows.str(voucher, "voucherNo"), "FIN_VOUCHER",
                "凭证审核 " + Rows.str(voucher, "voucherNo"), Map.of("voucher_id", id));
        chain.append(tenantId, id, "SUBMITTED", FinanceContext.userId(), instance);
        audit.publish("fin_voucher.submitted", "SUBMIT", "fin_voucher", id, "MEDIUM");
        return new VoucherActionResult(id, Rows.str(voucher, "voucherNo"), "SUBMITTED", null, instance);
    }

    @Transactional
    public VoucherActionResult audit(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> voucher = requireVoucher(tenantId, id);
        if (Rows.lngVal(voucher, "createdBy") == FinanceContext.userId()) {
            throw new BizException(FinanceErrorCode.AUDITOR_SAME_AS_MAKER);
        }
        if (voucherMapper.markAudited(tenantId, id, FinanceContext.userId()) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        chain.append(tenantId, id, "AUDITED", FinanceContext.userId(), "audited");
        audit.publish("fin_voucher.audited", "AUDIT", "fin_voucher", id, "MEDIUM");
        return new VoucherActionResult(id, Rows.str(voucher, "voucherNo"), "AUDITED", null, null);
    }

    @Transactional
    public VoucherActionResult post(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> voucher = requireVoucher(tenantId, id);
        if ("POSTED".equals(Rows.str(voucher, "status"))) {
            throw new BizException(FinanceErrorCode.VOUCHER_ALREADY_POSTED);
        }
        if (voucherMapper.markPosted(tenantId, id, FinanceContext.userId(), null) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        chain.append(tenantId, id, "POSTED", FinanceContext.userId(), Rows.str(voucher, "voucherNo"));
        String chainHash = chain.verify(tenantId, id);
        voucherMapper.updateChainHash(tenantId, id, chainHash);
        audit.publish("fin_voucher.posted", "POST", "fin_voucher", id, "HIGH");
        return new VoucherActionResult(id, Rows.str(voucher, "voucherNo"), "POSTED", chainHash, null);
    }

    /** 红字冲销：生成借贷方向相反的新凭证，原凭证置为 REVERSED。 */
    @Transactional
    public VoucherActionResult reverse(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> voucher = requireVoucher(tenantId, id);
        if (!"POSTED".equals(Rows.str(voucher, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        long ledgerId = Rows.lngVal(voucher, "ledgerId");
        String periodCode = Rows.str(voucher, "periodCode");
        LocalDate date = LocalDate.parse(Rows.str(voucher, "voucherDate"));
        String word = Rows.str(voucher, "voucherWord");
        String currency = Rows.str(voucher, "currencyCode");
        long reverseId = IdWorker.getId();
        String reverseNo = nextVoucherNo(word, periodCode);
        BigDecimal totalDebit = Rows.dec(voucher, "totalDebit");
        BigDecimal totalCredit = Rows.dec(voucher, "totalCredit");
        voucherMapper.insertVoucher(reverseId, tenantId, ledgerId, word, reverseNo, periodCode, date,
                "冲销 " + Rows.str(voucher, "voucherNo"), currency, totalCredit, totalDebit,
                "REVERSAL", id, "POSTED", FinanceContext.userId());
        int lineNo = 1;
        for (Map<String, Object> entry : voucherMapper.listEntries(tenantId, id)) {
            String direction = "DEBIT".equals(Rows.str(entry, "direction")) ? "CREDIT" : "DEBIT";
            ResolvedEntry item = new ResolvedEntry(lineNo++, Rows.str(entry, "summary"),
                    Rows.str(entry, "subjectCode"), Rows.str(entry, "subjectName"), direction,
                    Rows.dec(entry, "amount"), Rows.str(entry, "auxiliary"), Rows.dec(entry, "quantity"),
                    Rows.dec(entry, "price"));
            insertEntry(tenantId, reverseId, periodCode, item, currency);
        }
        chain.append(tenantId, reverseId, "CREATED", FinanceContext.userId(), reverseNo);
        chain.append(tenantId, reverseId, "POSTED", FinanceContext.userId(), reverseNo);
        voucherMapper.updateChainHash(tenantId, reverseId, chain.verify(tenantId, reverseId));
        voucherMapper.markReversed(tenantId, id, reverseId);
        audit.publish("fin_voucher.reversed", "REVERSE", "fin_voucher", id, "HIGH");
        return new VoucherActionResult(reverseId, reverseNo, "POSTED", null, null);
    }

    /** 业务单据自动生成凭证：status 为 DRAFT 或 POSTED，POSTED 时直接写入哈希链。 */
    @Transactional
    public long createSystem(long ledgerId, LocalDate voucherDate, String summary, String sourceType,
                             Long sourceBizId, String status, String currencyCode, List<EntryDraft> drafts) {
        long tenantId = FinanceContext.tenantId();
        if (ledgerId == 0 || voucherDate == null || drafts == null || drafts.isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        requireLedger(tenantId, ledgerId);
        String periodCode = periodCode(voucherDate);
        requireOpenPeriod(tenantId, ledgerId, periodCode);
        String currency = currencyCode == null || currencyCode.isBlank() ? "CNY" : currencyCode;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<ResolvedEntry> resolved = new ArrayList<>();
        int lineNo = 1;
        for (EntryDraft draft : drafts) {
            ResolvedEntry item = resolve(tenantId, ledgerId, lineNo++, draft.summary(), draft.subjectCode(),
                    draft.direction(), draft.amount(), null, null, null);
            resolved.add(item);
            if ("DEBIT".equals(item.direction())) {
                totalDebit = totalDebit.add(item.amount());
            } else {
                totalCredit = totalCredit.add(item.amount());
            }
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BizException(FinanceErrorCode.VOUCHER_UNBALANCED);
        }
        long id = IdWorker.getId();
        String voucherNo = nextVoucherNo("记", periodCode);
        voucherMapper.insertVoucher(id, tenantId, ledgerId, "记", voucherNo, periodCode, voucherDate, summary,
                currency, FinanceContext.amount(totalDebit), FinanceContext.amount(totalCredit),
                sourceType, sourceBizId, status, FinanceContext.userId());
        for (ResolvedEntry item : resolved) {
            insertEntry(tenantId, id, periodCode, item, currency);
        }
        chain.append(tenantId, id, "CREATED", FinanceContext.userId(), voucherNo + "|" + sourceType);
        if ("POSTED".equals(status)) {
            chain.append(tenantId, id, "POSTED", FinanceContext.userId(), voucherNo);
            voucherMapper.updateChainHash(tenantId, id, chain.verify(tenantId, id));
        }
        audit.publish("fin_voucher.system_created", "CREATE", "fin_voucher", id, "LOW");
        return id;
    }

    public VoucherView detail(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> row = requireVoucher(tenantId, id);
        List<VoucherEntryView> entries = new ArrayList<>();
        for (Map<String, Object> entry : voucherMapper.listEntries(tenantId, id)) {
            entries.add(new VoucherEntryView(Rows.intVal(entry, "lineNo"), Rows.str(entry, "summary"),
                    Rows.str(entry, "subjectCode"), Rows.str(entry, "subjectName"),
                    Rows.str(entry, "direction"), Rows.dec(entry, "amount"), Rows.str(entry, "currencyCode"),
                    Rows.dec(entry, "exchangeRate"), Rows.dec(entry, "amountBase"),
                    Rows.str(entry, "auxiliary"), Rows.dec(entry, "quantity"), Rows.dec(entry, "price")));
        }
        return new VoucherView(Rows.lngVal(row, "id"), Rows.lngVal(row, "ledgerId"),
                Rows.str(row, "voucherWord"), Rows.str(row, "voucherNo"), Rows.str(row, "periodCode"),
                Rows.str(row, "voucherDate"), Rows.str(row, "summary"), Rows.str(row, "currencyCode"),
                Rows.dec(row, "totalDebit"), Rows.dec(row, "totalCredit"), Rows.str(row, "sourceType"),
                Rows.lng(row, "sourceBizId"), Rows.str(row, "status"), Rows.lng(row, "auditedBy"),
                Rows.lng(row, "postedBy"), Rows.str(row, "chainHash"), entries, auditLogs(tenantId, id));
    }

    public PageResult<VoucherView> list(long ledgerId, String periodCode, String status, Integer page,
                                        Integer size) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        int pageNo = FinanceContext.page(page);
        int pageSize = FinanceContext.size(size);
        List<VoucherView> views = new ArrayList<>();
        for (Map<String, Object> row : voucherMapper.listVouchers(tenantId, ledgerId, periodCode, status,
                pageSize, (pageNo - 1) * pageSize)) {
            views.add(new VoucherView(Rows.lngVal(row, "id"), ledgerId, Rows.str(row, "voucherWord"),
                    Rows.str(row, "voucherNo"), Rows.str(row, "periodCode"), Rows.str(row, "voucherDate"),
                    Rows.str(row, "summary"), null, Rows.dec(row, "totalDebit"), Rows.dec(row, "totalCredit"),
                    Rows.str(row, "sourceType"), null, Rows.str(row, "status"), null, null,
                    Rows.str(row, "chainHash"), List.of(), List.of()));
        }
        long total = voucherMapper.countVouchers(tenantId, ledgerId, periodCode, status);
        return PageResult.of(views, total, pageNo, pageSize);
    }

    public AuditTrailView auditTrail(long id) {
        long tenantId = FinanceContext.tenantId();
        requireVoucher(tenantId, id);
        boolean valid = true;
        String lastHash = null;
        try {
            lastHash = chain.verify(tenantId, id);
        } catch (BizException ex) {
            valid = false;
        }
        return new AuditTrailView(id, valid, lastHash, auditLogs(tenantId, id));
    }

    private List<VoucherAuditLogView> auditLogs(long tenantId, long id) {
        List<VoucherAuditLogView> logs = new ArrayList<>();
        for (Map<String, Object> log : voucherMapper.listAuditLogs(tenantId, id)) {
            logs.add(new VoucherAuditLogView(Rows.lngVal(log, "chainSeq"), Rows.str(log, "action"),
                    Rows.lngVal(log, "operatorId"), Rows.str(log, "operatorName"),
                    Rows.instant(log, "occurredAt"), Rows.str(log, "recordHash"),
                    Rows.str(log, "prevHash"), Rows.str(log, "signature")));
        }
        return logs;
    }

    private record ResolvedEntry(int lineNo, String summary, String subjectCode, String subjectName,
                                 String direction, BigDecimal amount, String auxiliary,
                                 BigDecimal quantity, BigDecimal price) {
    }

    private ResolvedEntry resolve(long tenantId, long ledgerId, int lineNo, String summary, String subjectCode,
                                  String direction, BigDecimal amount, String auxiliary,
                                  BigDecimal quantity, BigDecimal price) {
        if (subjectCode == null || subjectCode.isBlank() || amount == null
                || amount.signum() <= 0 || (!"DEBIT".equals(direction) && !"CREDIT".equals(direction))) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        Map<String, Object> subject = ledgerMapper.findSubject(tenantId, ledgerId, subjectCode);
        if (subject == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Rows.bool(subject, "isLeaf")) {
            throw new BizException(FinanceErrorCode.SUBJECT_NOT_LEAF);
        }
        return new ResolvedEntry(lineNo, summary, subjectCode, Rows.str(subject, "subjectName"), direction,
                FinanceContext.amount(amount), auxiliary, quantity, price);
    }

    private void insertEntry(long tenantId, long voucherId, String periodCode, ResolvedEntry item,
                             String currency) {
        voucherMapper.insertEntry(IdWorker.getId(), tenantId, voucherId, periodCode, item.lineNo(),
                item.summary(), item.subjectCode(), item.subjectName(), item.direction(), item.amount(),
                currency, BigDecimal.ONE, item.amount(), item.auxiliary(), item.quantity(), item.price(),
                FinanceContext.userId());
    }

    private Map<String, Object> requireVoucher(long tenantId, long id) {
        Map<String, Object> voucher = voucherMapper.findVoucher(tenantId, id);
        if (voucher == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return voucher;
    }

    private void requireLedger(long tenantId, long ledgerId) {
        if (ledgerMapper.findLedger(tenantId, ledgerId) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireOpenPeriod(long tenantId, long ledgerId, String periodCode) {
        Map<String, Object> period = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        if (period == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"OPEN".equals(Rows.str(period, "status"))) {
            throw new BizException(FinanceErrorCode.PERIOD_CLOSED);
        }
    }

    private String nextVoucherNo(String word, String periodCode) {
        return word + "-" + periodCode.replace("-", "") + "-"
                + String.format("%05d", commonsMapper.nextVoucherNo());
    }

    public static String periodCode(LocalDate date) {
        return date.format(PERIOD_FORMAT);
    }

    private String toJson(Map<String, Object> value) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(value).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(entry.getKey()).append("\":\"")
                    .append(entry.getValue() == null ? "" : entry.getValue()).append('"');
            first = false;
        }
        return builder.append('}').toString();
    }
}
