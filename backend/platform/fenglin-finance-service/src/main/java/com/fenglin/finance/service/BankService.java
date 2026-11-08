package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.BankMapper;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.FinanceCrypto;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.BankAccountCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BankAccountView;
import com.fenglin.finance.web.dto.FinanceDtos.BankTransactionItem;
import com.fenglin.finance.web.dto.FinanceDtos.BankTransactionView;
import com.fenglin.finance.web.dto.FinanceDtos.BankTxnImportRequest;
import com.fenglin.finance.web.dto.FinanceDtos.BankTxnImportResult;
import com.fenglin.finance.web.dto.FinanceDtos.ReconcileRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ReconciliationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D1 银行账户、流水导入与银企对账。
 *
 * <p>偏差标注：账号使用 AES-256-GCM 加密 + 租户盐哈希，接口仅返回脱敏账号；
 * 对账自动匹配规则为“同账户、同方向、同金额、日期差 ≤ 3 天”（与文档 F-D1-07 一致）。</p>
 */
@Service
@Transactional(readOnly = true)
public class BankService {

    private static final String TABLE = "fin_bank_account";
    private static final String FIELD = "account_no";

    private final BankMapper bankMapper;
    private final CommonsMapper commonsMapper;
    private final FinanceCrypto crypto;
    private final FinanceAudit audit;

    public BankService(BankMapper bankMapper, CommonsMapper commonsMapper, FinanceCrypto crypto,
                       FinanceAudit audit) {
        this.bankMapper = bankMapper;
        this.commonsMapper = commonsMapper;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public BankAccountView createAccount(BankAccountCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.accountNo() == null || request.accountNo().isBlank()
                || request.accountName() == null || request.accountName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        bankMapper.insertAccount(id, tenantId,
                crypto.encrypt(tenantId, TABLE, FIELD, request.accountNo()),
                crypto.hash(tenantId, request.accountNo()), request.accountName(), request.bankName(),
                request.bankCode(), request.currencyCode() == null || request.currencyCode().isBlank()
                        ? "CNY" : request.currencyCode(),
                request.accountType() == null || request.accountType().isBlank()
                        ? "BASIC" : request.accountType(),
                request.subjectCode() == null || request.subjectCode().isBlank()
                        ? "1002" : request.subjectCode(),
                request.balance() == null ? FinanceContext.zero() : FinanceContext.amount(request.balance()),
                FinanceContext.userId());
        audit.publish("fin_bank_account.created", "CREATE", "fin_bank_account", id, "MEDIUM");
        return accountView(tenantId, bankMapper.findAccount(tenantId, id));
    }

    public List<BankAccountView> listAccounts() {
        long tenantId = FinanceContext.tenantId();
        List<BankAccountView> views = new ArrayList<>();
        for (Map<String, Object> row : bankMapper.listAccounts(tenantId)) {
            views.add(accountView(tenantId, row));
        }
        return views;
    }

    @Transactional
    public BankTxnImportResult importTransactions(BankTxnImportRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.accountId() == null || request.transactions() == null || request.transactions().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (bankMapper.findAccount(tenantId, request.accountId()) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        int imported = 0;
        for (BankTransactionItem item : request.transactions()) {
            if (item.txnNo() == null || item.txnNo().isBlank() || item.amount() == null
                    || (!"IN".equals(item.direction()) && !"OUT".equals(item.direction()))) {
                throw new BizException(FinanceErrorCode.PARAM_INVALID);
            }
            imported += bankMapper.insertTransaction(IdWorker.getId(), tenantId, request.accountId(),
                    item.txnNo(), item.txnDate() == null ? LocalDate.now() : item.txnDate(), item.direction(),
                    FinanceContext.amount(item.amount()), item.balance(), item.counterparty(), item.summary(),
                    FinanceContext.userId());
        }
        audit.publish("fin_bank_transaction.imported", "IMPORT", "fin_bank_account", request.accountId(),
                "LOW");
        return new BankTxnImportResult(request.accountId(), imported, request.transactions().size() - imported);
    }

    public List<BankTransactionView> listTransactions(long accountId, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        if (bankMapper.findAccount(tenantId, accountId) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        List<BankTransactionView> views = new ArrayList<>();
        for (Map<String, Object> row : bankMapper.listTransactions(tenantId, accountId, limit, offset)) {
            views.add(new BankTransactionView(Rows.lngVal(row, "id"), Rows.lngVal(row, "accountId"),
                    Rows.str(row, "txnNo"), Rows.str(row, "txnDate"), Rows.str(row, "direction"),
                    Rows.dec(row, "amount"), Rows.dec(row, "balance"), Rows.str(row, "counterparty"),
                    Rows.str(row, "summary"), Rows.str(row, "reconcileStatus"), Rows.lng(row, "reconId")));
        }
        return views;
    }

    /** 一键对账：先按规则自动匹配，再生成余额调节表。 */
    @Transactional
    public ReconciliationView reconcile(ReconcileRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.accountId() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        Map<String, Object> account = bankMapper.findAccount(tenantId, request.accountId());
        if (account == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        long reconId = IdWorker.getId();
        String reconNo = "BR-" + String.format("%06d", commonsMapper.nextReconNo());
        String periodCode = request.periodCode() == null || request.periodCode().isBlank()
                ? VoucherService.periodCode(LocalDate.now()) : request.periodCode();
        bankMapper.markMatched(tenantId, request.accountId(), reconId);
        BigDecimal bookBalance = Rows.dec(account, "balance");
        BigDecimal unsettledIn = nullSafe(bankMapper.sumUnmatched(tenantId, request.accountId(), "IN"));
        BigDecimal unsettledOut = nullSafe(bankMapper.sumUnmatched(tenantId, request.accountId(), "OUT"));
        BigDecimal adjusted = bookBalance.subtract(unsettledOut).add(unsettledIn);
        BigDecimal bankBalance = adjusted;
        for (Map<String, Object> txn : bankMapper.listTransactions(tenantId, request.accountId(), 1, 0)) {
            if (Rows.dec(txn, "balance") != null) {
                bankBalance = Rows.dec(txn, "balance");
                break;
            }
        }
        String status = adjusted.compareTo(bankBalance) == 0 ? "BALANCED" : "UNBALANCED";
        bankMapper.insertRecon(reconId, tenantId, reconNo, request.accountId(), periodCode,
                FinanceContext.amount(bookBalance), FinanceContext.amount(bankBalance),
                FinanceContext.amount(unsettledIn), FinanceContext.amount(unsettledOut),
                FinanceContext.amount(adjusted), status, FinanceContext.userId());
        audit.publish("fin_reconciliation.created", "RECONCILE", "fin_reconciliation", reconId, "MEDIUM");
        return new ReconciliationView(reconId, reconNo, request.accountId(), periodCode,
                FinanceContext.amount(bookBalance), FinanceContext.amount(bankBalance),
                FinanceContext.amount(unsettledIn), FinanceContext.amount(unsettledOut),
                FinanceContext.amount(adjusted), status,
                bankMapper.countUnmatched(tenantId, request.accountId()));
    }

    public List<ReconciliationView> listReconciliations(int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<ReconciliationView> views = new ArrayList<>();
        for (Map<String, Object> row : bankMapper.listRecons(tenantId, limit, offset)) {
            long accountId = Rows.lngVal(row, "accountId");
            views.add(new ReconciliationView(Rows.lngVal(row, "id"), Rows.str(row, "reconNo"), accountId,
                    Rows.str(row, "periodCode"), Rows.dec(row, "bookBalance"),
                    Rows.dec(row, "bankBalance"), Rows.dec(row, "unsettledIn"),
                    Rows.dec(row, "unsettledOut"), Rows.dec(row, "adjustedBalance"),
                    Rows.str(row, "status"), bankMapper.countUnmatched(tenantId, accountId)));
        }
        return views;
    }

    private BankAccountView accountView(long tenantId, Map<String, Object> row) {
        String accountNo = crypto.decrypt(tenantId, TABLE, FIELD, Rows.str(row, "accountNoEnc"));
        return new BankAccountView(Rows.lngVal(row, "id"), crypto.maskAccountNo(accountNo),
                Rows.str(row, "accountName"), Rows.str(row, "bankName"), Rows.str(row, "currencyCode"),
                Rows.str(row, "accountType"), Rows.str(row, "subjectCode"), Rows.dec(row, "balance"),
                Rows.str(row, "status"));
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? FinanceContext.zero() : value;
    }
}
