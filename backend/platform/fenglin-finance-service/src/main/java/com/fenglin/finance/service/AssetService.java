package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.AssetMapper;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCardCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCardView;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCategoryCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCategoryView;
import com.fenglin.finance.web.dto.FinanceDtos.DepreciationRunRequest;
import com.fenglin.finance.web.dto.FinanceDtos.DepreciationRunResult;
import com.fenglin.finance.web.dto.FinanceDtos.DisposeRequest;
import com.fenglin.finance.web.dto.FinanceDtos.DisposeResult;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetBorrowRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetSyncRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetView;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingDetail;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingItemRequest;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingItemView;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D3 固定资产与 IT 资产：卡片、三种折旧、处置、盘点、借用归还与 CMDB 同步。
 *
 * <p>偏差标注：文档要求盘点盈亏自动生成调整凭证，主链先记录盘点结果并计数，
 * 调整凭证与审批流留待收口；年数总和法按月剩余可折旧额均摊。</p>
 */
@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetMapper assetMapper;
    private final CommonsMapper commonsMapper;
    private final LedgerMapper ledgerMapper;
    private final VoucherService voucherService;
    private final FinanceAudit audit;

    public AssetService(AssetMapper assetMapper, CommonsMapper commonsMapper, LedgerMapper ledgerMapper,
                        VoucherService voucherService, FinanceAudit audit) {
        this.assetMapper = assetMapper;
        this.commonsMapper = commonsMapper;
        this.ledgerMapper = ledgerMapper;
        this.voucherService = voucherService;
        this.audit = audit;
    }

    @Transactional
    public AssetCategoryView createCategory(AssetCategoryCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.categoryCode() == null || request.categoryCode().isBlank()
                || request.categoryName() == null || request.categoryName().isBlank()
                || request.usefulLifeMonths() == null || request.usefulLifeMonths() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        assetMapper.insertCategory(id, tenantId, request.categoryCode(), request.categoryName(),
                request.usefulLifeMonths(),
                request.residualRate() == null ? BigDecimal.ZERO : request.residualRate(),
                request.depreciationMethod() == null || request.depreciationMethod().isBlank()
                        ? "STRAIGHT_LINE" : request.depreciationMethod(),
                request.assetAccountCode() == null ? "1601" : request.assetAccountCode(),
                request.depreciationAccountCode() == null ? "1602" : request.depreciationAccountCode(),
                request.expenseAccountCode() == null ? "6602" : request.expenseAccountCode(),
                FinanceContext.userId());
        audit.publish("fin_asset_category.created", "CREATE", "fin_asset_category", id, "LOW");
        return categoryView(assetMapper.findCategory(tenantId, id));
    }

    public List<AssetCategoryView> listCategories() {
        long tenantId = FinanceContext.tenantId();
        List<AssetCategoryView> views = new ArrayList<>();
        for (Map<String, Object> row : assetMapper.listCategories(tenantId)) {
            views.add(categoryView(row));
        }
        return views;
    }

    @Transactional
    public AssetCardView createCard(AssetCardCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.assetName() == null || request.assetName().isBlank() || request.categoryId() == null
                || request.originalValue() == null || request.originalValue().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        Map<String, Object> category = assetMapper.findCategory(tenantId, request.categoryId());
        if (category == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        long id = IdWorker.getId();
        String assetNo = "FA" + LocalDate.now().getYear() + String.format("%06d", commonsMapper.nextAssetNo());
        boolean itAsset = Boolean.TRUE.equals(request.itAssetFlag());
        assetMapper.insertCard(id, tenantId, assetNo, request.assetName(), request.categoryId(),
                request.specification(), request.location(), request.deptId(), request.custodianId(),
                request.enableDate() == null ? LocalDate.now() : request.enableDate(),
                FinanceContext.amount(request.originalValue()), Rows.dec(category, "residualRate"),
                Rows.intVal(category, "usefulLifeMonths"), Rows.str(category, "depreciationMethod"),
                itAsset, request.source() == null || request.source().isBlank()
                        ? "PURCHASE" : request.source(), FinanceContext.userId());
        if (itAsset) {
            assetMapper.insertItAsset(IdWorker.getId(), tenantId, id,
                    request.deviceType() == null || request.deviceType().isBlank()
                            ? "LAPTOP" : request.deviceType(),
                    request.brandModel(), request.serialNo(), request.ipAddress(), request.macAddress(),
                    request.osVersion(), softwareJson(request.softwareList()), null, FinanceContext.userId());
        }
        audit.publish("fin_asset_card.created", "CREATE", "fin_asset_card", id, "LOW");
        return cardView(assetMapper.findCard(tenantId, id));
    }

    public List<AssetCardView> listCards(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<AssetCardView> views = new ArrayList<>();
        for (Map<String, Object> row : assetMapper.listCards(tenantId, status, limit, offset)) {
            views.add(cardView(row));
        }
        return views;
    }

    @Transactional
    public DepreciationRunResult runDepreciation(DepreciationRunRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.ledgerId() == null || request.periodCode() == null || request.periodCode().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (ledgerMapper.findLedger(tenantId, request.ledgerId()) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        String periodCode = request.periodCode();
        LocalDate periodStart = LocalDate.parse(periodCode + "-01");
        List<Map<String, Object>> cards = assetMapper.listDepreciableCards(tenantId, periodStart);
        if (cards.isEmpty()) {
            return new DepreciationRunResult(null, periodCode, 0, FinanceContext.zero(), null);
        }
        for (Map<String, Object> card : cards) {
            if (assetMapper.countSchedule(tenantId, Rows.lngVal(card, "id"), periodCode) > 0) {
                throw new BizException(FinanceErrorCode.DEPRECIATION_DUPLICATED);
            }
        }
        String batchNo = "DP-" + periodCode.replace("-", "") + "-"
                + String.format("%04d", commonsMapper.nextDepBatchNo());
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> debitByAccount = new LinkedHashMap<>();
        Map<String, BigDecimal> creditByAccount = new LinkedHashMap<>();
        int count = 0;
        for (Map<String, Object> card : cards) {
            BigDecimal original = nullSafe(Rows.dec(card, "originalValue"));
            BigDecimal accumulated = nullSafe(Rows.dec(card, "accumulatedDepreciation"));
            BigDecimal netValue = nullSafe(Rows.dec(card, "netValue"));
            BigDecimal residualRate = nullSafe(Rows.dec(card, "residualRate"));
            int life = Rows.intVal(card, "usefulLifeMonths");
            BigDecimal residual = original.multiply(residualRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal depreciable = original.subtract(residual);
            if (depreciable.signum() <= 0 || life <= 0) {
                continue;
            }
            BigDecimal amount = monthlyDepreciation(Rows.str(card, "depreciationMethod"), depreciable,
                    netValue, residual, life, accumulated);
            amount = amount.min(netValue.subtract(residual)).setScale(4, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                continue;
            }
            BigDecimal accumulatedAfter = accumulated.add(amount);
            BigDecimal netValueAfter = netValue.subtract(amount);
            assetMapper.insertSchedule(IdWorker.getId(), tenantId, batchNo, Rows.lngVal(card, "id"),
                    periodCode, Rows.str(card, "depreciationMethod"), original, amount, accumulatedAfter,
                    netValueAfter, null, FinanceContext.userId());
            assetMapper.updateCardDepreciation(tenantId, Rows.lngVal(card, "id"), accumulatedAfter,
                    netValueAfter);
            Map<String, Object> category = assetMapper.findCategory(tenantId, Rows.lngVal(card, "categoryId"));
            String expenseAccount = category == null || Rows.str(category, "expenseAccountCode") == null
                    ? "6602" : Rows.str(category, "expenseAccountCode");
            String accumAccount = category == null || Rows.str(category, "depreciationAccountCode") == null
                    ? "1602" : Rows.str(category, "depreciationAccountCode");
            debitByAccount.merge(expenseAccount, amount, BigDecimal::add);
            creditByAccount.merge(accumAccount, amount, BigDecimal::add);
            total = total.add(amount);
            count++;
        }
        Long voucherId = null;
        if (count > 0) {
            List<VoucherService.EntryDraft> drafts = new ArrayList<>();
            debitByAccount.forEach((account, amount) -> drafts.add(new VoucherService.EntryDraft(
                    "计提折旧 " + periodCode, account, "DEBIT", FinanceContext.amount(amount))));
            creditByAccount.forEach((account, amount) -> drafts.add(new VoucherService.EntryDraft(
                    "计提折旧 " + periodCode, account, "CREDIT", FinanceContext.amount(amount))));
            voucherId = voucherService.createSystem(request.ledgerId(), periodStart, "计提折旧 " + periodCode,
                    "DEPRECIATION", null, "POSTED", "CNY", drafts);
        }
        audit.publish("fin_depreciation.generated", "GENERATE", "fin_depreciation_schedule", 0L, "MEDIUM");
        return new DepreciationRunResult(batchNo, periodCode, count, FinanceContext.amount(total), voucherId);
    }

    @Transactional
    public DisposeResult dispose(long assetId, DisposeRequest request) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> card = requireCard(tenantId, assetId);
        if ("DISPOSED".equals(Rows.str(card, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        Map<String, Object> category = assetMapper.findCategory(tenantId, Rows.lngVal(card, "categoryId"));
        BigDecimal original = nullSafe(Rows.dec(card, "originalValue"));
        BigDecimal accumulated = nullSafe(Rows.dec(card, "accumulatedDepreciation"));
        BigDecimal netValue = nullSafe(Rows.dec(card, "netValue"));
        BigDecimal income = request == null || request.disposalIncome() == null
                ? BigDecimal.ZERO : request.disposalIncome();
        LocalDate date = request == null || request.disposalDate() == null
                ? LocalDate.now() : request.disposalDate();
        BigDecimal gainLoss = income.subtract(netValue).setScale(4, RoundingMode.HALF_UP);
        String assetAccount = category == null ? "1601" : Rows.str(category, "assetAccountCode");
        String accumAccount = category == null ? "1602" : Rows.str(category, "depreciationAccountCode");
        List<VoucherService.EntryDraft> drafts = new ArrayList<>();
        if (accumulated.signum() > 0) {
            drafts.add(new VoucherService.EntryDraft("转销累计折旧", accumAccount, "DEBIT",
                    FinanceContext.amount(accumulated)));
        }
        if (income.signum() > 0) {
            drafts.add(new VoucherService.EntryDraft("处置收款", "1002", "DEBIT",
                    FinanceContext.amount(income)));
        }
        if (gainLoss.signum() < 0) {
            drafts.add(new VoucherService.EntryDraft("处置损失", "6115", "DEBIT",
                    FinanceContext.amount(gainLoss.negate())));
        }
        drafts.add(new VoucherService.EntryDraft("转销固定资产", assetAccount == null ? "1601" : assetAccount,
                "CREDIT", FinanceContext.amount(original)));
        if (gainLoss.signum() > 0) {
            drafts.add(new VoucherService.EntryDraft("处置收益", "6115", "CREDIT",
                    FinanceContext.amount(gainLoss)));
        }
        long ledgerId = defaultLedgerId(tenantId);
        long voucherId = voucherService.createSystem(ledgerId, date, "资产处置 " + Rows.str(card, "assetNo"),
                "ASSET_DISPOSAL", assetId, "POSTED", "CNY", drafts);
        long changeId = IdWorker.getId();
        String changeNo = "AC-" + String.format("%06d", commonsMapper.nextCostNo());
        assetMapper.insertChange(changeId, tenantId, changeNo, assetId, "DISPOSAL",
                json("{\"status\":\"" + Rows.str(card, "status") + "\",\"netValue\":" + netValue + "}"),
                json("{\"status\":\"DISPOSED\",\"disposalIncome\":" + income + "}"), gainLoss, voucherId,
                FinanceContext.userId());
        assetMapper.updateCardStatus(tenantId, assetId, "DISPOSED", null, null, null);
        audit.publish("fin_asset.disposed", "DISPOSE", "fin_asset_card", assetId, "HIGH");
        return new DisposeResult(assetId, changeNo, FinanceContext.amount(gainLoss), voucherId, "DISPOSED");
    }

    @Transactional
    public StocktakingView createStocktaking(StocktakingCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.stocktakingName() == null || request.stocktakingName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        String no = "STK" + LocalDate.now().getYear()
                + String.format("%05d", commonsMapper.nextStocktakingNo());
        String scopeType = request.scopeType() == null || request.scopeType().isBlank()
                ? "ALL" : request.scopeType();
        assetMapper.insertStocktaking(id, tenantId, no, request.stocktakingName(), scopeType,
                request.deptId(), request.planStartDate() == null ? LocalDate.now() : request.planStartDate(),
                request.planEndDate() == null ? LocalDate.now().plusDays(7) : request.planEndDate(),
                FinanceContext.userId());
        for (Map<String, Object> card : assetMapper.listCardsForScope(tenantId,
                "DEPARTMENT".equals(scopeType) ? request.deptId() : null)) {
            assetMapper.insertStocktakingItemSnapshot(IdWorker.getId(), tenantId, id,
                    Rows.lngVal(card, "id"), Rows.str(card, "assetNo"), Rows.str(card, "assetName"),
                    Rows.str(card, "location"), Rows.lng(card, "custodianId"), FinanceContext.userId());
        }
        assetMapper.recountStocktaking(tenantId, id);
        audit.publish("fin_asset_stocktaking.created", "CREATE", "fin_asset_stocktaking", id, "LOW");
        return stocktakingView(assetMapper.findStocktaking(tenantId, id));
    }

    public List<StocktakingView> listStocktakings(int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<StocktakingView> views = new ArrayList<>();
        for (Map<String, Object> row : assetMapper.listStocktakings(tenantId, limit, offset)) {
            views.add(stocktakingView(row));
        }
        return views;
    }

    public StocktakingDetail stocktakingDetail(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> stocktaking = assetMapper.findStocktaking(tenantId, id);
        if (stocktaking == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        List<StocktakingItemView> items = new ArrayList<>();
        for (Map<String, Object> row : assetMapper.listStocktakingItems(tenantId, id)) {
            items.add(new StocktakingItemView(Rows.lngVal(row, "id"), Rows.lng(row, "assetId"),
                    Rows.str(row, "assetNo"), Rows.str(row, "assetName"), Rows.str(row, "expectedLocation"),
                    Rows.str(row, "actualLocation"), Rows.str(row, "result"), Rows.str(row, "remark"),
                    Rows.instant(row, "countedAt")));
        }
        return new StocktakingDetail(stocktakingView(stocktaking), items);
    }

    @Transactional
    public StocktakingDetail countItem(long stocktakingId, StocktakingItemRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (assetMapper.findStocktaking(tenantId, stocktakingId) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (request == null || request.assetId() == null || request.result() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (assetMapper.countStocktakingItem(tenantId, stocktakingId, request.assetId(),
                request.actualLocation(), request.actualCustodianId(), request.result(), request.remark(),
                FinanceContext.userId()) == 0) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        assetMapper.recountStocktaking(tenantId, stocktakingId);
        return stocktakingDetail(stocktakingId);
    }

    @Transactional
    public StocktakingView completeStocktaking(long id) {
        long tenantId = FinanceContext.tenantId();
        if (assetMapper.findStocktaking(tenantId, id) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (assetMapper.completeStocktaking(tenantId, id, LocalDate.now()) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_asset_stocktaking.completed", "COMPLETE", "fin_asset_stocktaking", id, "MEDIUM");
        return stocktakingView(assetMapper.findStocktaking(tenantId, id));
    }

    @Transactional
    public ItAssetView borrowItAsset(long itAssetId, ItAssetBorrowRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request == null || request.borrowerId() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        if (assetMapper.borrowItAsset(tenantId, itAssetId, request.borrowerId(), LocalDate.now(),
                request.dueDate()) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        return itAssetView(assetMapper.findItAsset(tenantId, itAssetId));
    }

    @Transactional
    public ItAssetView returnItAsset(long itAssetId) {
        long tenantId = FinanceContext.tenantId();
        if (assetMapper.returnItAsset(tenantId, itAssetId) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        return itAssetView(assetMapper.findItAsset(tenantId, itAssetId));
    }

    public ItAssetView syncItAsset(long itAssetId, ItAssetSyncRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request == null || request.cmdbCiId() == null || request.cmdbCiId().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        String status = Boolean.FALSE.equals(request.success()) ? "FAILED" : "SYNCED";
        if (assetMapper.syncItAsset(tenantId, itAssetId, request.cmdbCiId(), status) == 0) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return itAssetView(assetMapper.findItAsset(tenantId, itAssetId));
    }

    public List<ItAssetView> listItAssets(int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<ItAssetView> views = new ArrayList<>();
        for (Map<String, Object> row : assetMapper.listItAssets(tenantId, limit, offset)) {
            views.add(itAssetView(row));
        }
        return views;
    }

    private BigDecimal monthlyDepreciation(String method, BigDecimal depreciable, BigDecimal netValue,
                                           BigDecimal residual, int life, BigDecimal accumulated) {
        String mode = method == null ? "STRAIGHT_LINE" : method;
        if ("DOUBLE_DECLINING".equals(mode)) {
            return netValue.multiply(BigDecimal.valueOf(2))
                    .divide(BigDecimal.valueOf(life), 4, RoundingMode.HALF_UP);
        }
        if ("SUM_OF_YEARS".equals(mode)) {
            BigDecimal elapsed = depreciable.signum() == 0 ? BigDecimal.ZERO
                    : accumulated.multiply(BigDecimal.valueOf(life)).divide(depreciable, 0, RoundingMode.DOWN);
            int remaining = Math.max(1, life - elapsed.intValue());
            return netValue.subtract(residual).divide(BigDecimal.valueOf(remaining), 4, RoundingMode.HALF_UP);
        }
        return depreciable.divide(BigDecimal.valueOf(life), 4, RoundingMode.HALF_UP);
    }

    private AssetCategoryView categoryView(Map<String, Object> row) {
        return new AssetCategoryView(Rows.lngVal(row, "id"), Rows.str(row, "categoryCode"),
                Rows.str(row, "categoryName"), Rows.intVal(row, "usefulLifeMonths"),
                Rows.dec(row, "residualRate"), Rows.str(row, "depreciationMethod"),
                Rows.str(row, "assetAccountCode"), Rows.str(row, "depreciationAccountCode"),
                Rows.str(row, "expenseAccountCode"), Rows.str(row, "status"));
    }

    private AssetCardView cardView(Map<String, Object> row) {
        return new AssetCardView(Rows.lngVal(row, "id"), Rows.str(row, "assetNo"),
                Rows.str(row, "assetName"), Rows.lngVal(row, "categoryId"), Rows.str(row, "specification"),
                Rows.str(row, "location"), Rows.lng(row, "deptId"), Rows.lng(row, "custodianId"),
                Rows.str(row, "enableDate"), Rows.dec(row, "originalValue"),
                Rows.dec(row, "accumulatedDepreciation"), Rows.dec(row, "netValue"),
                Rows.dec(row, "residualRate"), Rows.intVal(row, "usefulLifeMonths"),
                Rows.str(row, "depreciationMethod"), Rows.str(row, "status"),
                Rows.bool(row, "itAssetFlag"));
    }

    private StocktakingView stocktakingView(Map<String, Object> row) {
        return new StocktakingView(Rows.lngVal(row, "id"), Rows.str(row, "stocktakingNo"),
                Rows.str(row, "stocktakingName"), Rows.str(row, "scopeType"), Rows.lng(row, "deptId"),
                Rows.str(row, "planStartDate"), Rows.str(row, "planEndDate"), Rows.str(row, "actualEndDate"),
                Rows.intVal(row, "totalCount"), Rows.intVal(row, "normalCount"),
                Rows.intVal(row, "surplusCount"), Rows.intVal(row, "deficitCount"), Rows.str(row, "status"));
    }

    private ItAssetView itAssetView(Map<String, Object> row) {
        return new ItAssetView(Rows.lngVal(row, "id"), Rows.lngVal(row, "assetId"),
                Rows.str(row, "deviceType"), Rows.str(row, "brandModel"), Rows.str(row, "serialNo"),
                Rows.str(row, "ipAddress"), Rows.str(row, "borrowStatus"), Rows.lng(row, "borrowerId"),
                Rows.str(row, "borrowDate"), Rows.str(row, "dueDate"), Rows.str(row, "cmdbCiId"),
                Rows.str(row, "syncStatus"));
    }

    private Map<String, Object> requireCard(long tenantId, long id) {
        Map<String, Object> card = assetMapper.findCard(tenantId, id);
        if (card == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return card;
    }

    private long defaultLedgerId(long tenantId) {
        List<Map<String, Object>> ledgers = ledgerMapper.listLedgers(tenantId);
        if (ledgers.isEmpty()) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return Rows.lngVal(ledgers.get(0), "id");
    }

    private String softwareJson(List<String> softwareList) {
        if (softwareList == null || softwareList.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < softwareList.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(softwareList.get(i)).append('"');
        }
        return builder.append(']').toString();
    }

    private String json(String value) {
        return value;
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? FinanceContext.zero() : value;
    }
}
