package com.fenglin.finance.service;

import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.mapper.VoucherMapper;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.BalanceSheetLine;
import com.fenglin.finance.web.dto.FinanceDtos.BalanceSheetView;
import com.fenglin.finance.web.dto.FinanceDtos.IncomeStatementLine;
import com.fenglin.finance.web.dto.FinanceDtos.IncomeStatementView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D1 财务报表：资产负债表与利润表由已过账凭证科目余额实时汇总。
 *
 * <p>偏差标注：文档 F-D1-09 要求支持报表模板与取数公式配置，主链先按科目类别固定取数，
 * 模板化与跨期比较留待收口。</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final VoucherMapper voucherMapper;
    private final LedgerMapper ledgerMapper;

    public ReportService(VoucherMapper voucherMapper, LedgerMapper ledgerMapper) {
        this.voucherMapper = voucherMapper;
        this.ledgerMapper = ledgerMapper;
    }

    public BalanceSheetView balanceSheet(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        List<BalanceSheetLine> lines = new ArrayList<>();
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal profitLoss = BigDecimal.ZERO;
        for (Map<String, Object> row : voucherMapper.subjectBalances(tenantId, ledgerId, periodCode)) {
            String category = Rows.str(row, "subjectCategory");
            BigDecimal balance = FinanceContext.amount(Rows.dec(row, "balance"));
            lines.add(new BalanceSheetLine(Rows.str(row, "subjectCode"), Rows.str(row, "subjectName"),
                    category, balance));
            if ("ASSET".equals(category) || "COST".equals(category)) {
                assets = assets.add(balance);
            } else if ("LIABILITY".equals(category)) {
                liabilities = liabilities.add(balance.negate());
            } else if ("EQUITY".equals(category)) {
                equity = equity.add(balance.negate());
            } else if ("PROFIT_LOSS".equals(category)) {
                profitLoss = profitLoss.add(balance.negate());
            }
        }
        BigDecimal totalAssets = FinanceContext.amount(assets);
        BigDecimal totalLiabilitiesEquity = FinanceContext.amount(liabilities.add(equity).add(profitLoss));
        return new BalanceSheetView(periodCode, FinanceContext.amount(assets), FinanceContext.amount(liabilities),
                FinanceContext.amount(equity), FinanceContext.amount(profitLoss), totalAssets,
                totalLiabilitiesEquity, totalAssets.compareTo(totalLiabilitiesEquity) == 0, lines);
    }

    public IncomeStatementView incomeStatement(long ledgerId, String periodCode) {
        long tenantId = FinanceContext.tenantId();
        requireLedger(tenantId, ledgerId);
        List<IncomeStatementLine> lines = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Map<String, Object> row : voucherMapper.periodSubjectSums(tenantId, ledgerId, periodCode)) {
            if (!"PROFIT_LOSS".equals(Rows.str(row, "subjectCategory"))) {
                continue;
            }
            BigDecimal periodDebit = Rows.dec(row, "periodDebit");
            BigDecimal periodCredit = Rows.dec(row, "periodCredit");
            BigDecimal net = FinanceContext.amount(periodCredit.subtract(periodDebit));
            lines.add(new IncomeStatementLine(Rows.str(row, "subjectCode"), Rows.str(row, "subjectName"),
                    "PROFIT_LOSS", net));
            if ("CREDIT".equals(Rows.str(row, "balanceDirection"))) {
                revenue = revenue.add(net);
            } else {
                cost = cost.add(net.negate());
            }
        }
        return new IncomeStatementView(periodCode, FinanceContext.amount(revenue), FinanceContext.amount(cost),
                FinanceContext.amount(revenue.subtract(cost)), lines);
    }

    private void requireLedger(long tenantId, long ledgerId) {
        if (ledgerMapper.findLedger(tenantId, ledgerId) == null) {
            throw new com.fenglin.common.core.BizException(
                    com.fenglin.finance.FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
