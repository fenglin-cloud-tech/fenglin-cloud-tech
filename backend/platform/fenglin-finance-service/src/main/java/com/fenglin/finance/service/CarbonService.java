package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.CarbonMapper;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonActivityCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonActivityView;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonCalculateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonCalculateResult;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonSourceItem;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonSourceView;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonTrendPoint;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonTrendView;
import com.fenglin.finance.web.dto.FinanceDtos.EsgIndicatorCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.EsgIndicatorView;
import com.fenglin.finance.web.dto.FinanceDtos.EsgReportCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.EsgReportDetailView;
import com.fenglin.finance.web.dto.FinanceDtos.EsgReportView;
import com.fenglin.finance.web.dto.FinanceDtos.FactorCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.FactorView;
import com.fenglin.finance.web.dto.FinanceDtos.TargetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TargetView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D4 碳排放核算与 ESG 披露。
 *
 * <p>偏差标注：排放量按“活动数据 × 排放因子”直接计算（因子单位与活动单位一致），
 * 未做单位换算系数；核算结果刷新同年度双碳目标进度；ESG 报告导出为 CSV 文本。</p>
 */
@Service
@Transactional(readOnly = true)
public class CarbonService {

    private final CarbonMapper carbonMapper;
    private final CommonsMapper commonsMapper;
    private final FinanceAudit audit;
    private final ObjectMapper objectMapper;

    public CarbonService(CarbonMapper carbonMapper, CommonsMapper commonsMapper, FinanceAudit audit,
                         ObjectMapper objectMapper) {
        this.carbonMapper = carbonMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FactorView createFactor(FactorCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.factorCode() == null || request.factorCode().isBlank()
                || request.factorValue() == null || request.factorValue().signum() < 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        carbonMapper.insertFactor(id, tenantId, request.factorCode(), request.factorName(),
                request.scope() == null || request.scope().isBlank() ? "SCOPE_1" : request.scope(),
                request.activityType(), request.activityUnit(), FinanceContext.amount(request.factorValue()),
                request.factorUnit(), request.factorVersion() == null ? "V1" : request.factorVersion(),
                request.source(), request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom(),
                request.effectiveTo(), FinanceContext.userId());
        audit.publish("fin_emission_factor.created", "CREATE", "fin_emission_factor", id, "LOW");
        return factorView(carbonMapper.findFactor(tenantId, id));
    }

    public List<FactorView> listFactors(String scope, String activityType) {
        long tenantId = FinanceContext.tenantId();
        List<FactorView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listFactors(tenantId, scope, activityType)) {
            views.add(factorView(row));
        }
        return views;
    }

    @Transactional
    public CarbonActivityView createActivity(CarbonActivityCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.activityType() == null || request.activityType().isBlank()
                || request.activityDate() == null || request.amount() == null
                || request.amount().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        String activityNo = "CA-" + String.format("%06d", commonsMapper.nextActivityNo());
        carbonMapper.insertActivity(id, tenantId, activityNo, request.orgId(), request.deptId(),
                request.activityType(), request.scope() == null || request.scope().isBlank()
                        ? "SCOPE_1" : request.scope(),
                request.activityDate(), VoucherService.periodCode(request.activityDate()),
                FinanceContext.amount(request.amount()), request.unit(), request.factorId(),
                request.dataSource(), request.evidenceFileId(), FinanceContext.userId());
        audit.publish("fin_carbon_activity.created", "CREATE", "fin_carbon_activity", id, "LOW");
        return activityView(carbonMapper.findActivity(tenantId, id));
    }

    public List<CarbonActivityView> listActivities(String periodCode, String scope, String status,
                                                   int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<CarbonActivityView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listActivities(tenantId, periodCode, scope, status,
                limit, offset)) {
            views.add(activityView(row));
        }
        return views;
    }

    /**
     * 员工自助：强制按当前登录用户收敛，只返回本人填报的活动数据。
     */
    public List<CarbonActivityView> listMine(String periodCode, String scope, String status,
                                             int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        long userId = FinanceContext.userId();
        List<CarbonActivityView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listActivitiesByCreator(tenantId, userId,
                periodCode, scope, status, limit, offset)) {
            views.add(activityView(row));
        }
        return views;
    }

    @Transactional
    public CarbonActivityView verify(long id) {
        long tenantId = FinanceContext.tenantId();
        if (carbonMapper.verifyActivity(tenantId, id, FinanceContext.userId()) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_carbon_activity.verified", "VERIFY", "fin_carbon_activity", id, "MEDIUM");
        return activityView(carbonMapper.findActivity(tenantId, id));
    }

    @Transactional
    public CarbonActivityView lock(long id) {
        long tenantId = FinanceContext.tenantId();
        if (carbonMapper.lockActivity(tenantId, id, FinanceContext.userId()) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        audit.publish("fin_carbon_activity.locked", "LOCK", "fin_carbon_activity", id, "MEDIUM");
        return activityView(carbonMapper.findActivity(tenantId, id));
    }

    @Transactional
    public CarbonCalculateResult calculate(CarbonCalculateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.periodCode() == null || request.periodCode().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        String periodCode = request.periodCode();
        List<Map<String, Object>> activities = carbonMapper.listCalculableActivities(tenantId, periodCode,
                request.scope(), request.orgId(), Boolean.TRUE.equals(request.recalc()));
        if (activities.isEmpty()) {
            return new CarbonCalculateResult(null, periodCode, 0, FinanceContext.zero(), Map.of());
        }
        String batchNo = "CB-" + periodCode.replace("-", "") + "-"
                + String.format("%04d", commonsMapper.nextEmissionNo());
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byScope = new LinkedHashMap<>();
        int calculated = 0;
        for (Map<String, Object> activity : activities) {
            String scope = Rows.str(activity, "scope");
            Long factorId = Rows.lng(activity, "factorId");
            Map<String, Object> factor = factorId == null
                    ? carbonMapper.findApplicableFactor(tenantId, scope, Rows.str(activity, "activityType"),
                    Rows.str(activity, "unit"), LocalDate.parse(Rows.str(activity, "activityDate")))
                    : carbonMapper.findFactor(tenantId, factorId);
            if (factor == null || Rows.dec(factor, "factorValue") == null) {
                continue;
            }
            BigDecimal amount = Rows.dec(activity, "amount");
            BigDecimal emission = amount.multiply(Rows.dec(factor, "factorValue"))
                    .setScale(4, RoundingMode.HALF_UP);
            long activityId = Rows.lngVal(activity, "id");
            carbonMapper.markNotLatest(tenantId, activityId);
            carbonMapper.insertEmission(IdWorker.getId(), tenantId,
                    "CE-" + String.format("%08d", commonsMapper.nextEmissionNo()), batchNo, activityId,
                    Rows.lngVal(factor, "id"), Rows.str(factor, "factorVersion"), amount,
                    Rows.dec(factor, "factorValue"), emission, periodCode, scope, true,
                    FinanceContext.userId());
            total = total.add(emission);
            byScope.merge(scope, emission, BigDecimal::add);
            calculated++;
        }
        refreshTargets(tenantId, periodCode);
        audit.publish("fin_carbon_emission.calculated", "CALCULATE", "fin_carbon_emission", 0L, "MEDIUM");
        Map<String, BigDecimal> rounded = new LinkedHashMap<>();
        byScope.forEach((key, value) -> rounded.put(key, FinanceContext.amount(value)));
        return new CarbonCalculateResult(batchNo, periodCode, calculated, FinanceContext.amount(total),
                rounded);
    }

    /** 排放趋势：按期间汇总最新核算结果，同时给出各范围拆分。 */
    public CarbonTrendView trend(String fromPeriod, String toPeriod, String scope) {
        long tenantId = FinanceContext.tenantId();
        String from = blankToNull(fromPeriod);
        String to = blankToNull(toPeriod);
        Map<String, Map<String, BigDecimal>> byPeriod = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Map<String, Object> row : carbonMapper.emissionTrend(tenantId, from, to, blankToNull(scope))) {
            String periodCode = Rows.str(row, "periodCode");
            byPeriod.computeIfAbsent(periodCode, key -> new LinkedHashMap<>())
                    .merge(Rows.str(row, "scope"), Rows.dec(row, "emission"), BigDecimal::add);
            totals.merge(periodCode, Rows.dec(row, "emission"), BigDecimal::add);
        }
        List<CarbonTrendPoint> points = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            Map<String, BigDecimal> scopeAmounts = new LinkedHashMap<>();
            byPeriod.get(entry.getKey()).forEach((key, value) ->
                    scopeAmounts.put(key, FinanceContext.amount(value)));
            points.add(new CarbonTrendPoint(entry.getKey(), FinanceContext.amount(entry.getValue()),
                    scopeAmounts));
            total = total.add(entry.getValue());
        }
        return new CarbonTrendView(from, to, FinanceContext.amount(total), points);
    }

    /** 排放源分析：按碳活动类型汇总最新核算结果，并给出占比。 */
    public CarbonSourceView sources(String periodCode) {
        long tenantId = FinanceContext.tenantId();
        String period = blankToNull(periodCode);
        List<Map<String, Object>> rows = carbonMapper.emissionByActivityType(tenantId, period);
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(Rows.dec(row, "emission"));
        }
        List<CarbonSourceItem> sources = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal emission = Rows.dec(row, "emission");
            BigDecimal percentage = total.signum() == 0 ? BigDecimal.ZERO
                    : emission.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            sources.add(new CarbonSourceItem(Rows.str(row, "activityType"), FinanceContext.amount(emission),
                    percentage));
        }
        return new CarbonSourceView(period, FinanceContext.amount(total), sources);
    }

    @Transactional
    public TargetView createTarget(TargetCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.targetName() == null || request.targetName().isBlank()
                || request.baseEmission() == null || request.targetEmission() == null
                || request.baseEmission().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        BigDecimal reduction = request.baseEmission().subtract(request.targetEmission())
                .multiply(BigDecimal.valueOf(100))
                .divide(request.baseEmission(), 2, RoundingMode.HALF_UP);
        long id = IdWorker.getId();
        int targetYear = request.targetYear() == null ? LocalDate.now().getYear() : request.targetYear();
        int baseYear = request.baseYear() == null ? targetYear - 1 : request.baseYear();
        carbonMapper.insertTarget(id, tenantId, "CT-" + String.format("%06d", commonsMapper.nextTargetNo()),
                request.targetName(), targetYear, baseYear,
                FinanceContext.amount(request.baseEmission()),
                FinanceContext.amount(request.targetEmission()), reduction, request.ownerId(),
                FinanceContext.userId());
        audit.publish("fin_carbon_target.created", "CREATE", "fin_carbon_target", id, "LOW");
        refreshTargets(tenantId, targetYear + "-01");
        return targetView(carbonMapper.findTarget(tenantId, id));
    }

    public List<TargetView> listTargets() {
        long tenantId = FinanceContext.tenantId();
        List<TargetView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listTargets(tenantId)) {
            views.add(targetView(row));
        }
        return views;
    }

    @Transactional
    public EsgIndicatorView createIndicator(EsgIndicatorCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.indicatorCode() == null || request.indicatorCode().isBlank()
                || request.indicatorName() == null || request.indicatorName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        int year = request.periodYear() == null ? LocalDate.now().getYear() : request.periodYear();
        carbonMapper.insertIndicator(IdWorker.getId(), tenantId, request.indicatorCode(),
                request.indicatorName(), request.category() == null ? "ENVIRONMENT" : request.category(),
                request.unit(), year, request.targetValue(), request.actualValue(), request.dataSource(),
                FinanceContext.userId());
        for (Map<String, Object> row : carbonMapper.listIndicators(tenantId, year)) {
            if (request.indicatorCode().equals(Rows.str(row, "indicatorCode"))) {
                return indicatorView(row);
            }
        }
        throw new BizException(FinanceErrorCode.STATE_CONFLICT);
    }

    public List<EsgIndicatorView> listIndicators(Integer periodYear) {
        long tenantId = FinanceContext.tenantId();
        List<EsgIndicatorView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listIndicators(tenantId, periodYear)) {
            views.add(indicatorView(row));
        }
        return views;
    }

    @Transactional
    public EsgReportView createReport(EsgReportCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.reportName() == null || request.reportName().isBlank()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        int year = request.periodYear() == null ? LocalDate.now().getYear() : request.periodYear();
        long id = IdWorker.getId();
        carbonMapper.insertEsgReport(id, tenantId,
                "ER-" + String.format("%06d", commonsMapper.nextEsgReportNo()), request.reportName(), year,
                request.reportType() == null ? "ANNUAL" : request.reportType(), request.reportBoundary(),
                summary(tenantId, year), FinanceContext.userId());
        audit.publish("fin_esg_report.created", "CREATE", "fin_esg_report", id, "LOW");
        return reportView(carbonMapper.findEsgReport(tenantId, id));
    }

    public List<EsgReportView> listReports(int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<EsgReportView> views = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listEsgReports(tenantId, limit, offset)) {
            views.add(new EsgReportView(Rows.lngVal(row, "id"), Rows.str(row, "reportNo"),
                    Rows.str(row, "reportName"), Rows.intVal(row, "periodYear"),
                    Rows.str(row, "reportType"), null, Rows.str(row, "status"), null,
                    Rows.instant(row, "publishedAt")));
        }
        return views;
    }

    @Transactional
    public EsgReportView publish(long id) {
        long tenantId = FinanceContext.tenantId();
        if (carbonMapper.findEsgReport(tenantId, id) == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        carbonMapper.updateEsgReportStatus(tenantId, id, "PUBLISHED", null);
        audit.publish("fin_esg_report.published", "PUBLISH", "fin_esg_report", id, "HIGH");
        return reportView(carbonMapper.findEsgReport(tenantId, id));
    }

    /** ESG 报告导出：CSV 文本（报告汇总 + 当年指标）。 */
    public String exportReport(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> report = carbonMapper.findEsgReport(tenantId, id);
        if (report == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        StringBuilder csv = new StringBuilder("report_no,report_name,period_year,status\n");
        csv.append(Rows.str(report, "reportNo")).append(',').append(Rows.str(report, "reportName"))
                .append(',').append(Rows.intVal(report, "periodYear")).append(',')
                .append(Rows.str(report, "status")).append('\n');
        csv.append("\nindicator_code,indicator_name,category,unit,target_value,actual_value\n");
        for (Map<String, Object> row : carbonMapper.listIndicators(tenantId,
                Rows.intVal(report, "periodYear"))) {
            csv.append(Rows.str(row, "indicatorCode")).append(',')
                    .append(Rows.str(row, "indicatorName")).append(',')
                    .append(Rows.str(row, "category")).append(',')
                    .append(Rows.str(row, "unit")).append(',')
                    .append(Rows.dec(row, "targetValue")).append(',')
                    .append(Rows.dec(row, "actualValue")).append('\n');
        }
        return csv.toString();
    }

    private void refreshTargets(long tenantId, String periodCode) {
        int year = Integer.parseInt(periodCode.substring(0, 4));
        BigDecimal current = carbonMapper.sumLatestEmission(tenantId, year + "-%");
        for (Map<String, Object> target : carbonMapper.listTargets(tenantId)) {
            if (Rows.intVal(target, "targetYear") != year) {
                continue;
            }
            BigDecimal base = Rows.dec(target, "baseEmission");
            BigDecimal targetEmission = Rows.dec(target, "targetEmission");
            BigDecimal required = base.subtract(targetEmission);
            BigDecimal progress = required.signum() <= 0 ? BigDecimal.valueOf(100)
                    : base.subtract(current).multiply(BigDecimal.valueOf(100))
                    .divide(required, 2, RoundingMode.HALF_UP);
            String status = current.compareTo(targetEmission) <= 0 ? "ON_TRACK" : "AT_RISK";
            carbonMapper.updateTargetProgress(tenantId, Rows.lngVal(target, "id"),
                    FinanceContext.amount(current), progress, status);
        }
    }

    public EsgReportDetailView reportDetail(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> row = carbonMapper.findEsgReport(tenantId, id);
        if (row == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return new EsgReportDetailView(Rows.lngVal(row, "id"), Rows.str(row, "reportNo"),
                Rows.str(row, "reportName"), Rows.intVal(row, "periodYear"),
                Rows.str(row, "reportType"), Rows.str(row, "reportBoundary"), Rows.str(row, "status"),
                parseSummary(Rows.str(row, "summary")), Rows.instant(row, "publishedAt"));
    }

    /** 刷新 ESG 报告快照：按当前年度指标与碳排放重新生成章节，写入 summary。 */
    @Transactional
    public EsgReportDetailView refreshReport(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> row = carbonMapper.findEsgReport(tenantId, id);
        if (row == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        carbonMapper.updateEsgReportSummary(tenantId, id, summary(tenantId, Rows.intVal(row, "periodYear")),
                FinanceContext.userId());
        audit.publish("fin_esg_report.refreshed", "REFRESH", "fin_esg_report", id, "MEDIUM");
        return reportDetail(id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(summary, Map.class);
        } catch (Exception e) {
            return Map.of("raw", summary);
        }
    }

    private String summary(long tenantId, int year) {
        BigDecimal total = carbonMapper.sumLatestEmission(tenantId, year + "-%");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_emission", FinanceContext.amount(total));
        for (Map<String, Object> row : carbonMapper.sumLatestEmissionByScope(tenantId, year + "-%")) {
            metrics.put(Rows.str(row, "scope").toLowerCase(), FinanceContext.amount(Rows.dec(row, "emission")));
        }
        List<Map<String, Object>> sectionList = new ArrayList<>();
        Map<String, Object> carbonSection = new LinkedHashMap<>();
        carbonSection.put("code", "CARBON_OVERVIEW");
        carbonSection.put("title", "碳排放概况");
        carbonSection.put("metrics", metrics);
        sectionList.add(carbonSection);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : carbonMapper.listIndicators(tenantId, year)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", Rows.str(row, "indicatorCode"));
            item.put("name", Rows.str(row, "indicatorName"));
            item.put("category", Rows.str(row, "category"));
            item.put("unit", Rows.str(row, "unit"));
            item.put("target", Rows.dec(row, "targetValue"));
            item.put("actual", Rows.dec(row, "actualValue"));
            items.add(item);
        }
        Map<String, Object> indicatorSection = new LinkedHashMap<>();
        indicatorSection.put("code", "INDICATORS");
        indicatorSection.put("title", "ESG 指标快照");
        indicatorSection.put("items", items);
        sectionList.add(indicatorSection);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("period_year", year);
        root.put("generated_at", OffsetDateTime.now().toString());
        root.put("sections", sectionList);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("ESG 报告快照序列化失败", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private FactorView factorView(Map<String, Object> row) {
        return new FactorView(Rows.lngVal(row, "id"), Rows.str(row, "factorCode"),
                Rows.str(row, "factorName"), Rows.str(row, "scope"), Rows.str(row, "activityType"),
                Rows.str(row, "activityUnit"), Rows.dec(row, "factorValue"), Rows.str(row, "factorUnit"),
                Rows.str(row, "factorVersion"), Rows.str(row, "source"), Rows.str(row, "effectiveFrom"),
                Rows.str(row, "effectiveTo"), Rows.str(row, "status"));
    }

    private CarbonActivityView activityView(Map<String, Object> row) {
        return new CarbonActivityView(Rows.lngVal(row, "id"), Rows.str(row, "activityNo"),
                Rows.lng(row, "orgId"), Rows.lng(row, "deptId"), Rows.str(row, "activityType"),
                Rows.str(row, "scope"), Rows.str(row, "activityDate"), Rows.str(row, "periodCode"),
                Rows.dec(row, "amount"), Rows.str(row, "unit"), Rows.lng(row, "factorId"),
                Rows.str(row, "dataSource"), Rows.str(row, "status"), Rows.lng(row, "verifiedBy"),
                Rows.lng(row, "lockedBy"));
    }

    private TargetView targetView(Map<String, Object> row) {
        return new TargetView(Rows.lngVal(row, "id"), Rows.str(row, "targetNo"),
                Rows.str(row, "targetName"), Rows.intVal(row, "targetYear"),
                Rows.intVal(row, "baseYear"), Rows.dec(row, "baseEmission"),
                Rows.dec(row, "targetEmission"), Rows.dec(row, "reductionRate"),
                Rows.dec(row, "currentEmission"), Rows.dec(row, "progress"), Rows.str(row, "status"));
    }

    private EsgIndicatorView indicatorView(Map<String, Object> row) {
        return new EsgIndicatorView(Rows.lngVal(row, "id"), Rows.str(row, "indicatorCode"),
                Rows.str(row, "indicatorName"), Rows.str(row, "category"), Rows.str(row, "unit"),
                Rows.intVal(row, "periodYear"), Rows.dec(row, "targetValue"), Rows.dec(row, "actualValue"),
                Rows.str(row, "dataSource"), Rows.str(row, "status"));
    }

    private EsgReportView reportView(Map<String, Object> row) {
        return new EsgReportView(Rows.lngVal(row, "id"), Rows.str(row, "reportNo"),
                Rows.str(row, "reportName"), Rows.intVal(row, "periodYear"), Rows.str(row, "reportType"),
                Rows.str(row, "reportBoundary"), Rows.str(row, "status"), Rows.str(row, "summary"),
                Rows.instant(row, "publishedAt"));
    }
}
