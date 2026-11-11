package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.TenantInitView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 租户开通时的财务域初始化（FIN-15）。
 *
 * <p>账套、默认科目、当年会计期间与币种一次建齐，全部落在同一个事务里：
 * 任一步失败整体回滚，不会留下半初始化数据。方法按"缺什么补什么"实现幂等，
 * 重复调用（新租户开通重试、存量租户补齐）不会重复建。</p>
 */
@Service
public class TenantFinanceInitService {

    private static final Logger log = LoggerFactory.getLogger(TenantFinanceInitService.class);

    private static final String DEFAULT_LEDGER_CODE = "DEFAULT";
    private static final String DEFAULT_LEDGER_NAME = "默认账套";
    private static final String DEFAULT_COMPANY_NAME = "默认公司";
    private static final long SYSTEM_USER_ID = 0L;

    /** 与总纲/建账接口保持一致的 24 个常用科目。 */
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

    private static final String[][] DEFAULT_CURRENCIES = {
            {"CNY", "人民币", "¥", "2"},
            {"USD", "美元", "$", "2"},
            {"HKD", "港币", "HK$", "2"},
            {"EUR", "欧元", "€", "2"}};

    private final LedgerMapper ledgerMapper;
    private final FinanceAudit audit;

    public TenantFinanceInitService(LedgerMapper ledgerMapper, FinanceAudit audit) {
        this.ledgerMapper = ledgerMapper;
        this.audit = audit;
    }

    @Transactional
    public TenantInitView initialize(long tenantId, String tenantName) {
        TenantContext.Snapshot previous = TenantContext.get();
        TenantContext.set(tenantId, SYSTEM_USER_ID, "SYSTEM", null, true);
        try {
            long ledgerId;
            int ledgersCreated = 0;
            Long existing = ledgerMapper.findLedgerIdByCode(tenantId, DEFAULT_LEDGER_CODE);
            if (existing != null) {
                ledgerId = existing;
            } else {
                List<Map<String, Object>> ledgers = ledgerMapper.listLedgers(tenantId);
                if (!ledgers.isEmpty()) {
                    ledgerId = Rows.lngVal(ledgers.get(0), "id");
                } else {
                    ledgerId = IdWorker.getId();
                    String companyName = tenantName == null || tenantName.isBlank()
                            ? DEFAULT_COMPANY_NAME : tenantName;
                    ledgerMapper.insertLedger(ledgerId, tenantId, DEFAULT_LEDGER_CODE, DEFAULT_LEDGER_NAME,
                            companyName, "CNY", 1, "CAS", null, null, SYSTEM_USER_ID);
                    ledgersCreated = 1;
                }
            }

            int subjectsCreated = 0;
            for (String[] subject : DEFAULT_SUBJECTS) {
                if (ledgerMapper.findSubject(tenantId, ledgerId, subject[0]) != null) {
                    continue;
                }
                ledgerMapper.insertSubject(IdWorker.getId(), tenantId, ledgerId, subject[0], subject[1], null, 1,
                        subject[2], subject[3], true, null, SYSTEM_USER_ID);
                subjectsCreated++;
            }

            int periodsCreated = 0;
            int year = LocalDate.now().getYear();
            for (int month = 1; month <= 12; month++) {
                String periodCode = String.format("%d-%02d", year, month);
                if (ledgerMapper.findPeriod(tenantId, ledgerId, periodCode) != null) {
                    continue;
                }
                LocalDate start = LocalDate.of(year, month, 1);
                ledgerMapper.insertPeriod(IdWorker.getId(), tenantId, ledgerId, year, month, periodCode, start,
                        start.plusMonths(1).minusDays(1), "OPEN", SYSTEM_USER_ID);
                periodsCreated++;
            }

            int currenciesCreated = 0;
            for (String[] currency : DEFAULT_CURRENCIES) {
                if (ledgerMapper.findCurrencyId(tenantId, currency[0]) != null) {
                    continue;
                }
                ledgerMapper.insertCurrency(IdWorker.getId(), tenantId, currency[0], currency[1], currency[2],
                        Integer.parseInt(currency[3]), SYSTEM_USER_ID);
                currenciesCreated++;
            }

            long subjectsTotal = ledgerMapper.countSubjects(tenantId, ledgerId);
            long periodsTotal = ledgerMapper.countPeriods(tenantId, ledgerId);
            long currenciesTotal = ledgerMapper.countCurrencies(tenantId);
            audit.publish("finance.tenant.initialized", "INIT", "fin_ledger", ledgerId, "MEDIUM");
            log.info("财务域初始化完成 tenant={} ledger={} 新建(账套={}, 科目={}, 期间={}, 币种={}) 合计(科目={}, 期间={}, 币种={})",
                    tenantId, ledgerId, ledgersCreated, subjectsCreated, periodsCreated, currenciesCreated,
                    subjectsTotal, periodsTotal, currenciesTotal);
            return new TenantInitView(tenantId, ledgerId, ledgersCreated, subjectsCreated, periodsCreated,
                    currenciesCreated, subjectsTotal, periodsTotal, currenciesTotal);
        } finally {
            if (previous != null) {
                TenantContext.set(previous.tenantId(), previous.userId(), previous.clientType(),
                        previous.requestId(), previous.crossTenant());
            } else {
                TenantContext.clear();
            }
        }
    }
}
