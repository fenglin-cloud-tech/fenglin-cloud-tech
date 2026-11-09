package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.ArapMapper;
import com.fenglin.finance.mapper.AssetMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.mapper.OpsMapper;
import com.fenglin.finance.mapper.VoucherMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.FinanceCrypto;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.LedgerCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.LedgerView;
import com.fenglin.finance.web.dto.FinanceDtos.PeriodCloseResult;
import com.fenglin.finance.web.dto.FinanceDtos.PeriodView;
import com.fenglin.finance.web.dto.FinanceDtos.SubjectCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.SubjectUpdateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.SubjectView;
import com.fenglin.finance.web.dto.FinanceDtos.TrialBalanceLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D1 账套、科目、期间与结账。
 *
 * <p>账套创建时按总纲兜底预置《企业会计准则》常用科目与当年 12 个期间，避免账套创建后无法制单；
 * 文档 F-D1-02 的科目模板导入为企业版能力，此处仅保证主链可用（偏差已标注）。</p>
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

    private static final String[][] DEFAULT_SUBJECTS = {
            {"1001", "库存现金", "ASSET", "DEBIT"},
            {"1002", "银行存款", "ASSET", "DEBIT"},
            {"1122", "应收账款", "ASSET", "DEBIT"},
            {"1221", "其他应收款", "ASSET", "DEBIT"},
            {"1601", "固定资产", "ASSET", "DEBIT"},
            {"1602", "累计折旧", "ASSET", "CREDIT"},
            {"1701", "无形资产", "ASSET", "DEBIT"},
            {"1901", "待处理财产损溢", "ASSET", "DEBIT"},
            {"2202", "应付账款", "LIABILITY", "CREDIT"},
            {"2211", "应付职工薪酬", "LIABILITY", "CREDIT"},
            {"2221", "应交税费", "LIABILITY", "CREDIT"},
            {"2241", "其他应付款", "LIABILITY", "CREDIT"},
            {"4001", "实收资本", "EQUITY", "CREDIT"},
            {"4103", "本年利润", "EQUITY", "CREDIT"},
            {"4104", "利润分配", "EQUITY", "CREDIT"},
            {"5001", "生产成本", "COST", "DEBIT"},
            {"5101", "制造费用", "COST", "DEBIT"},
            {"6001", "主营业务收入", "PROFIT_LOSS", "CREDIT"},
            {"6051", "其他业务收入", "PROFIT_LOSS", "CREDIT"},
            {"6115", "资产处置损益", "PROFIT_LOSS", "CREDIT"},
            {"6401", "主营业务成本", "PROFIT_LOSS", "DEBIT"},
            {"6601", "销售费用", "PROFIT_LOSS", "DEBIT"},
            {"6602", "管理费用", "PROFIT_LOSS", "DEBIT"},
            {"6603", "财务费用", "PROFIT_LOSS", "DEBIT"}};

    private final LedgerMapper ledgerMapper;
    private final VoucherMapper voucherMapper;
    private final ArapMapper arapMapper;
    private final AssetMapper assetMapper;
    private final OpsMapper opsMapper;
    private final FinanceCrypto crypto;
    private final FinanceAudit audit;
    private final ObjectMapper objectMapper;

    public LedgerService(LedgerMapper ledgerMapper, VoucherMapper voucherMapper, ArapMapper arapMapper,
                         AssetMapper assetMapper, OpsMapper opsMapper, FinanceCrypto crypto, FinanceAudit audit,
                         ObjectMapper objectMapper) {
        this.ledgerMapper = ledgerMapper;
        this.voucherMapper = voucherMapper;
        this.arapMapper = arapMapper;
        this.assetMapper = assetMapper;
        this.opsMapper = opsMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LedgerView create(LedgerCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.ledgerCode() == null || request.ledgerCode().isBlank()
                || request.ledgerName() == null || request.ledgerName().isBlank()
                || request.companyName() == null || request.companyName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (ledgerMapper.findLedgerIdByCode(tenantId, request.ledgerCode()) != null) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        long id = IdWorker.getId();
        int startMonth = request.fiscalYearStartMonth() == null ? 1 : request.fiscalYearStartMonth();
        if (startMonth < 1 || startMonth > 12) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        String currency = request.baseCurrency() == null || request.baseCurrency().isBlank()
                ? "CNY" : request.baseCurrency();
        String standard = request.accountingStandard() == null || request.accountingStandard().isBlank()
                ? "CAS" : request.accountingStandard();
        String taxNoEnc = crypto.encrypt(tenantId, "fin_ledger", "tax_no", request.taxNo());
        String taxNoHash = crypto.hash(tenantId, request.taxNo());
        ledgerMapper.insertLedger(id, tenantId, request.ledgerCode(), request.ledgerName(),
                request.companyName(), currency, startMonth, standard, taxNoEnc, taxNoHash,
                FinanceContext.userId());

        for (String[] subject : DEFAULT_SUBJECTS) {
            ledgerMapper.insertSubject(IdWorker.getId(), tenantId, id, subject[0], subject[1], null, 1,
                    subject[2], subject[3], true, null, FinanceContext.userId());
        }
        int year = LocalDate.now().getYear();
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(year, month, 1);
            ledgerMapper.insertPeriod(IdWorker.getId(), tenantId, id, year, month,
                    String.format("%d-%02d", year, month), start, start.plusMonths(1).minusDays(1),
                    "OPEN", FinanceContext.userId());
        }
        audit.publish("finance.ledger.created", "CREATE", "fin_ledger", id, "MEDIUM");
        return toView(ledgerMapper.findLedger(tenantId, id));
    }

    public List<LedgerView> list() {
        long tenantId = FinanceContext.tenantId();
        return ledgerMapper.listLedgers(tenantId).stream().map(LedgerService::toView).toList();
    }

    @Transactional
    public SubjectView createSubject(long ledgerId, SubjectCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        if (request.subjectCode() == null || request.subjectCode().isBlank()
                || request.subjectName() == null || request.subjectName().isBlank()
                || request.subjectCategory() == null || request.balanceDirection() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (ledgerMapper.findSubject(tenantId, ledgerId, request.subjectCode()) != null) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        int level = 1;
        if (request.parentId() != null) {
            Map<String, Object> parent = ledgerMapper.findSubjectById(tenantId, request.parentId());
            if (parent == null) {
                throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
            }
            level = Rows.intVal(parent, "subjectLevel") + 1;
            if (level > 10) {
                throw new BizException(FinanceErrorCode.PARAM_INVALID);
            }
        }
        long id = IdWorker.getId();
        boolean leaf = request.isLeaf() == null || request.isLeaf();
        ledgerMapper.insertSubject(id, tenantId, ledgerId, request.subjectCode(), request.subjectName(),
                request.parentId(), level, request.subjectCategory(), request.balanceDirection(), leaf,
                request.auxiliaryTypes(), FinanceContext.userId());
        audit.publish("finance.subject.created", "CREATE", "fin_accounting_subject", id, "LOW");
        return toSubject(ledgerMapper.findSubject(tenantId, ledgerId, request.subjectCode()));
    }

    public List<SubjectView> subjects(long ledgerId) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        return ledgerMapper.listSubjects(tenantId, ledgerId).stream().map(LedgerService::toSubject).toList();
    }

    @Transactional
    public SubjectView updateSubject(long ledgerId, long id, SubjectUpdateRequest request) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> subject = requireSubject(tenantId, ledgerId, id);
        String name = request.subjectName() == null || request.subjectName().isBlank()
                ? Rows.str(subject, "subjectName") : request.subjectName();
        String category = request.subjectCategory() == null || request.subjectCategory().isBlank()
                ? Rows.str(subject, "subjectCategory") : request.subjectCategory();
        String direction = request.balanceDirection() == null || request.balanceDirection().isBlank()
                ? Rows.str(subject, "balanceDirection") : request.balanceDirection();
        boolean leaf = request.isLeaf() == null ? Rows.bool(subject, "isLeaf") : request.isLeaf();
        String auxiliary = request.auxiliaryTypes() == null
                ? Rows.str(subject, "auxiliaryTypes") : request.auxiliaryTypes();
        ledgerMapper.updateSubject(tenantId, ledgerId, id, name, category, direction, leaf, auxiliary);
        audit.publish("finance.subject.updated", "UPDATE", "fin_accounting_subject", id, "LOW");
        return toSubject(ledgerMapper.findSubjectById(tenantId, id));
    }

    @Transactional
    public SubjectView disableSubject(long ledgerId, long id) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> subject = requireSubject(tenantId, ledgerId, id);
        String subjectCode = Rows.str(subject, "subjectCode");
        if (opsMapper.countUnpostedBySubject(tenantId, ledgerId, subjectCode) > 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        BigDecimal closing = opsMapper.subjectClosing(tenantId, ledgerId, subjectCode, "9999-12");
        if (closing != null && closing.signum() != 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        ledgerMapper.disableSubject(tenantId, ledgerId, id);
        audit.publish("finance.subject.disabled", "DISABLE", "fin_accounting_subject", id, "MEDIUM");
        return toSubject(ledgerMapper.findSubjectById(tenantId, id));
    }

    public Map<String, Object> generalLedger(long ledgerId, String subjectCode, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> subject = ledgerMapper.findSubject(tenantId, ledgerId, subjectCode);
        if (subject == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        BigDecimal opening = opsMapper.subjectOpening(tenantId, ledgerId, subjectCode, periodCode);
        BigDecimal running = opening;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> row : opsMapper.listSubjectEntries(tenantId, ledgerId, subjectCode, periodCode)) {
            BigDecimal amount = Rows.dec(row, "amount");
            BigDecimal delta = "DEBIT".equals(Rows.str(row, "direction")) ? amount : amount.negate();
            running = running.add(delta);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("voucher_no", Rows.str(row, "voucherNo"));
            line.put("voucher_date", Rows.str(row, "voucherDate"));
            line.put("line_no", Rows.intVal(row, "lineNo"));
            line.put("summary", Rows.str(row, "summary"));
            line.put("direction", Rows.str(row, "direction"));
            line.put("amount", amount);
            line.put("balance", running);
            lines.add(line);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject_code", subjectCode);
        result.put("subject_name", Rows.str(subject, "subjectName"));
        result.put("period_code", periodCode);
        result.put("opening", opening);
        result.put("lines", lines);
        result.put("closing", running);
        return result;
    }

    private Map<String, Object> requireSubject(long tenantId, long ledgerId, long id) {
        Map<String, Object> subject = ledgerMapper.findSubjectById(tenantId, id);
        if (subject == null || Rows.lngVal(subject, "ledgerId") != ledgerId) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return subject;
    }

    public List<PeriodView> periods(long ledgerId) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        return ledgerMapper.listPeriods(tenantId, ledgerId).stream()
                .map(row -> new PeriodView(Rows.lngVal(row, "id"), Rows.str(row, "periodCode"),
                        Rows.intVal(row, "periodYear"), Rows.intVal(row, "periodMonth"),
                        Rows.str(row, "startDate"), Rows.str(row, "endDate"), Rows.str(row, "status")))
                .toList();
    }

    @Transactional
    public PeriodCloseResult close(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> period = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        if (period == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"OPEN".equals(Rows.str(period, "status"))) {
            throw new BizException(FinanceErrorCode.PERIOD_CLOSED);
        }
        if (voucherMapper.countUnposted(tenantId, ledgerId, periodCode) > 0) {
            throw new BizException(FinanceErrorCode.UNPOSTED_VOUCHER_EXISTS);
        }
        LocalDate endDate = LocalDate.parse(Rows.str(period, "endDate"));
        LocalDate startDate = LocalDate.parse(Rows.str(period, "startDate"));
        if (!assetMapper.listDepreciableCards(tenantId, startDate).isEmpty()) {
            long missing = assetMapper.listDepreciableCards(tenantId, startDate).stream()
                    .filter(card -> assetMapper.countSchedule(tenantId, Rows.lngVal(card, "id"), periodCode) == 0)
                    .count();
            if (missing > 0) {
                throw new BizException(FinanceErrorCode.CLOSE_CHECK_FAILED);
            }
        }
        if (arapMapper.countOpenReceivables(tenantId) > 0 || arapMapper.countOpenPayables(tenantId) > 0) {
            throw new BizException(FinanceErrorCode.CLOSE_CHECK_FAILED);
        }
        List<Map<String, Object>> trial = voucherMapper.trialBalance(tenantId, ledgerId, periodCode);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> row : trial) {
            BigDecimal opening = Rows.dec(row, "opening");
            BigDecimal periodDebit = Rows.dec(row, "periodDebit");
            BigDecimal periodCredit = Rows.dec(row, "periodCredit");
            BigDecimal closing = opening.add(periodDebit).subtract(periodCredit);
            debit = debit.add(periodDebit);
            credit = credit.add(periodCredit);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("subject_code", Rows.str(row, "subjectCode"));
            line.put("opening", opening);
            line.put("period_debit", periodDebit);
            line.put("period_credit", periodCredit);
            line.put("closing", closing);
            lines.add(line);
        }
        if (debit.compareTo(credit) != 0) {
            throw new BizException(FinanceErrorCode.TRIAL_BALANCE_UNBALANCED);
        }
        String prevHash = ledgerMapper.findLastSnapshotHash(tenantId, ledgerId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("period_code", periodCode);
        snapshot.put("trial_debit", debit);
        snapshot.put("trial_credit", credit);
        snapshot.put("lines", lines);
        snapshot.put("closed_at", OffsetDateTime.now().toString());
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("结账快照序列化失败", e);
        }
        String prev = prevHash == null ? "GENESIS" : prevHash;
        String recordHash = sha256(prev + "|" + json);
        ledgerMapper.insertSnapshot(IdWorker.getId(), tenantId, ledgerId, periodCode,
                debit.setScale(4, RoundingMode.HALF_UP), credit.setScale(4, RoundingMode.HALF_UP),
                json, recordHash, prev, FinanceContext.userId());
        ledgerMapper.updatePeriodStatus(tenantId, Rows.lngVal(period, "id"), "CLOSED", FinanceContext.userId());
        audit.publish("finance.period.closed", "CLOSE", "fin_accounting_period", periodCode, "HIGH");
        return new PeriodCloseResult(periodCode, "CLOSED", debit, credit, recordHash, OffsetDateTime.now().toInstant());
    }

    @Transactional
    public PeriodView reopen(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> period = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        if (period == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"CLOSED".equals(Rows.str(period, "status")) && !"SEALED".equals(Rows.str(period, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        ledgerMapper.updatePeriodStatus(tenantId, Rows.lngVal(period, "id"), "OPEN", FinanceContext.userId());
        audit.publish("finance.period.reopened", "REOPEN", "fin_accounting_period", periodCode, "HIGH");
        Map<String, Object> updated = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        return new PeriodView(Rows.lngVal(updated, "id"), Rows.str(updated, "periodCode"),
                Rows.intVal(updated, "periodYear"), Rows.intVal(updated, "periodMonth"),
                Rows.str(updated, "startDate"), Rows.str(updated, "endDate"), Rows.str(updated, "status"));
    }

    @Transactional
    public PeriodView seal(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        Map<String, Object> period = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        if (period == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"CLOSED".equals(Rows.str(period, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        ledgerMapper.updatePeriodStatus(tenantId, Rows.lngVal(period, "id"), "SEALED", FinanceContext.userId());
        audit.publish("finance.period.sealed", "SEAL", "fin_accounting_period", periodCode, "HIGH");
        Map<String, Object> updated = ledgerMapper.findPeriod(tenantId, ledgerId, periodCode);
        return new PeriodView(Rows.lngVal(updated, "id"), Rows.str(updated, "periodCode"),
                Rows.intVal(updated, "periodYear"), Rows.intVal(updated, "periodMonth"),
                Rows.str(updated, "startDate"), Rows.str(updated, "endDate"), Rows.str(updated, "status"));
    }

    public List<TrialBalanceLine> trialBalance(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        List<TrialBalanceLine> lines = new ArrayList<>();
        for (Map<String, Object> row : voucherMapper.trialBalance(tenantId, ledgerId, periodCode)) {
            BigDecimal opening = Rows.dec(row, "opening");
            BigDecimal periodDebit = Rows.dec(row, "periodDebit");
            BigDecimal periodCredit = Rows.dec(row, "periodCredit");
            BigDecimal closing = opening.add(periodDebit).subtract(periodCredit);
            BigDecimal closingDebit = closing.signum() > 0 ? closing : BigDecimal.ZERO;
            BigDecimal closingCredit = closing.signum() < 0 ? closing.negate() : BigDecimal.ZERO;
            lines.add(new TrialBalanceLine(Rows.str(row, "subjectCode"), Rows.str(row, "subjectName"),
                    Rows.str(row, "subjectCategory"), Rows.str(row, "balanceDirection"),
                    opening.signum() > 0 ? opening : BigDecimal.ZERO,
                    opening.signum() < 0 ? opening.negate() : BigDecimal.ZERO,
                    periodDebit, periodCredit, closingDebit, closingCredit));
        }
        return lines;
    }

    public Map<String, Object> requireLedger(long tenantId, long ledgerId) {
        Map<String, Object> ledger = ledgerMapper.findLedger(tenantId, ledgerId);
        if (ledger == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ledger;
    }

    private static LedgerView toView(Map<String, Object> row) {
        return new LedgerView(Rows.lngVal(row, "id"), Rows.str(row, "ledgerCode"),
                Rows.str(row, "ledgerName"), Rows.str(row, "companyName"),
                Rows.str(row, "baseCurrency"), Rows.str(row, "accountingStandard"), Rows.str(row, "status"));
    }

    private static SubjectView toSubject(Map<String, Object> row) {
        return new SubjectView(Rows.lngVal(row, "id"), Rows.str(row, "subjectCode"),
                Rows.str(row, "subjectName"), Rows.lng(row, "parentId"), Rows.intVal(row, "subjectLevel"),
                Rows.str(row, "subjectCategory"), Rows.str(row, "balanceDirection"),
                Rows.bool(row, "isLeaf"), Rows.str(row, "auxiliaryTypes"), Rows.str(row, "status"));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
