package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CodeMapper;
import com.fenglin.rnd.mapper.DefectMapper;
import com.fenglin.rnd.mapper.MetricMapper;
import com.fenglin.rnd.mapper.PipelineMapper;
import com.fenglin.rnd.mapper.ReleaseMapper;
import com.fenglin.rnd.mapper.RequirementMapper;
import com.fenglin.rnd.mapper.SprintMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.AlertCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.AlertUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.AlertView;
import com.fenglin.rnd.web.dto.RndDtos.ContributionView;
import com.fenglin.rnd.web.dto.RndDtos.DoraView;
import com.fenglin.rnd.web.dto.RndDtos.InsightView;
import com.fenglin.rnd.web.dto.RndDtos.MetricCaliberCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricCaliberView;
import com.fenglin.rnd.web.dto.RndDtos.MetricComputeRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricComputeResult;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigView;
import com.fenglin.rnd.web.dto.RndDtos.MetricDataQualityView;
import com.fenglin.rnd.web.dto.RndDtos.MetricValueView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * E2 效能度量：按周期归集代码贡献、交付、质量与 DORA 指标，落快照并支持预警评估。
 *
 * <p>口径版本统一 v1；分母为 0 的指标标记 data_quality=missing，不返回 0 冒充有效值。</p>
 */
@Service
@Transactional(readOnly = true)
public class MetricService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern WEEK = Pattern.compile("\\d{4}-W\\d{2}");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final MetricMapper metricMapper;
    private final RequirementMapper requirementMapper;
    private final DefectMapper defectMapper;
    private final ReleaseMapper releaseMapper;
    private final PipelineMapper pipelineMapper;
    private final CodeMapper codeMapper;
    private final SprintMapper sprintMapper;
    private final ProductService productService;
    private final RndAudit audit;

    public MetricService(MetricMapper metricMapper, RequirementMapper requirementMapper, DefectMapper defectMapper,
                         ReleaseMapper releaseMapper, PipelineMapper pipelineMapper, CodeMapper codeMapper,
                         SprintMapper sprintMapper, ProductService productService, RndAudit audit) {
        this.metricMapper = metricMapper;
        this.requirementMapper = requirementMapper;
        this.defectMapper = defectMapper;
        this.releaseMapper = releaseMapper;
        this.pipelineMapper = pipelineMapper;
        this.codeMapper = codeMapper;
        this.sprintMapper = sprintMapper;
        this.productService = productService;
        this.audit = audit;
    }

    @Transactional
    public MetricComputeResult compute(MetricComputeRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long productId = request.productId();
        String periodType = request.periodType() == null ? "month" : request.periodType();
        String periodKey = request.periodKey() == null ? YearMonth.now(ZONE).toString() : request.periodKey();
        Range range = range(tenantId, periodType, periodKey);

        List<Map<String, Object>> contributions = metricMapper.aggregateCommitContribution(
                tenantId, productId, range.fromText(), range.toText());
        long totalCommits = 0;
        long totalAdd = 0;
        long totalDel = 0;
        long linkedCommits = 0;
        for (Map<String, Object> row : contributions) {
            int commits = Rows.intVal(row, "commitCount");
            int add = Rows.intVal(row, "addLines");
            int del = Rows.intVal(row, "delLines");
            int files = Rows.intVal(row, "filesChanged");
            int linked = Rows.intVal(row, "linkedCommits");
            totalCommits += commits;
            totalAdd += add;
            totalDel += del;
            linkedCommits += linked;
            BigDecimal complexity = BigDecimal.valueOf((long) add + del)
                    .divide(BigDecimal.valueOf(Math.max(commits, 1)), 4, RoundingMode.HALF_UP);
            metricMapper.upsertContribution(IdWorker.getId(), tenantId, Rows.lngVal(row, "userId"), productId,
                    periodType, periodKey, commits, add, del, files, linked, complexity, userId);
        }

        long delivered = requirementMapper.countDelivered(tenantId, productId);
        BigDecimal leadTimeHours = averageRequirementLeadTime(tenantId, productId);
        long totalDefects = defectMapper.countByProduct(tenantId, productId);
        long prodDefects = defectMapper.countProdByProduct(tenantId, productId);
        long prodDeployments = releaseMapper.countProdDeployments(tenantId, productId,
                range.fromText(), range.toText());
        long failedDeployments = releaseMapper.countFailedProdDeployments(tenantId, productId,
                range.fromText(), range.toText());
        BigDecimal releaseLeadTime = releaseMapper.avgLeadTimeHours(tenantId, productId,
                range.fromText(), range.toText());
        BigDecimal mttr = defectMapper.avgMttrHours(tenantId, productId, range.fromText(), range.toText());

        List<MetricValueView> metrics = new ArrayList<>();
        metrics.add(record(tenantId, productId, periodType, periodKey, "contribution.code_lines",
                "代码贡献行数", "contribution", BigDecimal.valueOf(totalAdd - totalDel), "行",
                totalCommits > 0 ? "normal" : "missing", BigDecimal.valueOf(totalAdd - totalDel), null,
                (int) totalCommits, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "contribution.effective_commit_rate",
                "有效提交率", "contribution",
                ratio(linkedCommits, totalCommits), "比率", totalCommits > 0 ? "normal" : "missing",
                BigDecimal.valueOf(linkedCommits), BigDecimal.valueOf(totalCommits), (int) totalCommits, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "delivery.throughput",
                "需求交付吞吐", "delivery", BigDecimal.valueOf(delivered), "个",
                delivered > 0 ? "normal" : "missing", BigDecimal.valueOf(delivered), null,
                (int) delivered, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "delivery.lead_time_hours",
                "需求交付周期", "delivery", leadTimeHours, "小时",
                delivered > 0 ? "normal" : "missing", leadTimeHours, BigDecimal.valueOf(delivered),
                (int) delivered, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "quality.defect_escape_rate",
                "缺陷逃逸率", "quality", ratio(prodDefects, totalDefects), "比率",
                totalDefects > 0 ? "normal" : "missing", BigDecimal.valueOf(prodDefects),
                BigDecimal.valueOf(totalDefects), (int) totalDefects, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "quality.defect_density",
                "缺陷密度", "quality", ratio(totalDefects, delivered), "个/需求",
                delivered > 0 ? "normal" : "missing", BigDecimal.valueOf(totalDefects),
                BigDecimal.valueOf(delivered), (int) totalDefects, userId));
        BigDecimal deployFreq = BigDecimal.valueOf(prodDeployments)
                .divide(BigDecimal.valueOf(Math.max(range.days(), 1)), 4, RoundingMode.HALF_UP);
        metrics.add(record(tenantId, productId, periodType, periodKey, "dora.deploy_freq",
                "部署频率", "dora", deployFreq, "次/天", "normal", BigDecimal.valueOf(prodDeployments),
                BigDecimal.valueOf(range.days()), (int) prodDeployments, userId));
        metrics.add(record(tenantId, productId, periodType, periodKey, "dora.lead_time_hours",
                "变更前置时间", "dora", releaseLeadTime, "小时",
                prodDeployments > 0 ? "normal" : "missing", releaseLeadTime,
                BigDecimal.valueOf(prodDeployments), (int) prodDeployments, userId));
        BigDecimal changeFailureRate = ratio(failedDeployments, prodDeployments);
        metrics.add(record(tenantId, productId, periodType, periodKey, "dora.change_failure_rate",
                "变更失败率", "dora", changeFailureRate, "比率",
                prodDeployments > 0 ? "normal" : "missing", BigDecimal.valueOf(failedDeployments),
                BigDecimal.valueOf(prodDeployments), (int) prodDeployments, userId));
        // 文档 DORA 含 MTTR；当前域无独立故障工单表，以 blocker/critical 缺陷修复时长近似，口径标注 missing 不冒充
        metrics.add(record(tenantId, productId, periodType, periodKey, "dora.mttr_hours",
                "故障恢复时间", "dora", mttr, "小时",
                mttr != null && mttr.compareTo(BigDecimal.ZERO) > 0 ? "normal" : "missing",
                mttr, BigDecimal.valueOf(totalDefects), (int) totalDefects, userId));

        String level = doraLevel(deployFreq, releaseLeadTime, changeFailureRate, mttr);
        metricMapper.upsertDora(IdWorker.getId(), tenantId, productId, periodType, periodKey, deployFreq,
                releaseLeadTime, changeFailureRate, mttr, level, userId);
        audit.publish("METRIC_COMPUTED", "compute", "devops_metric_snapshot", productId, "low");
        return new MetricComputeResult(productId, periodType, periodKey, metrics, level);
    }

    public List<MetricValueView> snapshots(long productId, String periodType, String periodKey) {
        return metricMapper.listSnapshots(RndContext.tenantId(), productId,
                        periodType == null ? "month" : periodType,
                        periodKey == null ? YearMonth.now(ZONE).toString() : periodKey)
                .stream()
                .map(row -> new MetricValueView(Rows.str(row, "metricCode"), Rows.str(row, "metricName"),
                        Rows.str(row, "metricGroup"), Rows.dec(row, "metricValue"), Rows.str(row, "unit"),
                        Rows.str(row, "dataQuality"), Rows.str(row, "caliberVersion")))
                .toList();
    }

    public List<ContributionView> contributions(long productId, String periodType, String periodKey) {
        return metricMapper.listContributions(RndContext.tenantId(), productId,
                        RndContext.text(periodType), RndContext.text(periodKey))
                .stream()
                .map(MetricService::toContributionView)
                .toList();
    }

    /**
     * 员工自助：只返回当前登录账号的贡献度，跨产品，tenant_id + user_id 双重收敛。
     */
    public List<ContributionView> myContributions(Long productId, String periodType, String periodKey,
                                                  Integer page, Integer size) {
        return metricMapper.listContributionsByUser(RndContext.tenantId(), RndContext.userId(), productId,
                        RndContext.text(periodType), RndContext.text(periodKey),
                        RndContext.size(size), RndContext.offset(page, size))
                .stream()
                .map(MetricService::toContributionView)
                .toList();
    }

    private static ContributionView toContributionView(Map<String, Object> row) {
        int commits = Rows.intVal(row, "commitCount");
        int effective = Rows.intVal(row, "effectiveCommits");
        return new ContributionView(Rows.lng(row, "userId"), Rows.lng(row, "productId"),
                Rows.str(row, "periodType"), Rows.str(row, "periodKey"), commits,
                Rows.intVal(row, "addLines"), Rows.intVal(row, "delLines"), effective,
                ratio(effective, commits), Rows.dec(row, "complexityScore"));
    }

    public DoraView dora(long productId, String periodType, String periodKey) {
        Map<String, Object> row = metricMapper.findDora(RndContext.tenantId(), productId,
                periodType == null ? "month" : periodType,
                periodKey == null ? YearMonth.now(ZONE).toString() : periodKey);
        if (row == null) {
            throw new BizException(RndErrorCode.METRIC_DATA_INSUFFICIENT);
        }
        return new DoraView(Rows.lng(row, "productId"), Rows.str(row, "periodType"), Rows.str(row, "periodKey"),
                Rows.dec(row, "deployFreq"), Rows.dec(row, "leadTimeHours"),
                Rows.dec(row, "changeFailureRate"), Rows.dec(row, "mttrHours"), Rows.str(row, "level"),
                Rows.str(row, "computedAt"));
    }

    public InsightView insight(long productId, String periodType, String periodKey) {
        long tenantId = RndContext.tenantId();
        String type = periodType == null ? "month" : periodType;
        String key = periodKey == null ? YearMonth.now(ZONE).toString() : periodKey;
        List<MetricValueView> metrics = snapshots(productId, type, key);
        DoraView dora = null;
        try {
            dora = dora(productId, type, key);
        } catch (BizException ignored) {
            // 尚未计算 DORA 时不阻断大盘
        }
        Range range = range(tenantId, type, key);
        long commits = metricMapper.countCommits(tenantId, productId, range.fromText(), range.toText());
        String dataQuality = commits == 0 && metrics.isEmpty() ? "missing" : "normal";
        return new InsightView(productId, key, metrics, dora,
                (int) requirementMapper.countByProduct(tenantId, productId),
                (int) defectMapper.countByProduct(tenantId, productId),
                (int) commits,
                (int) releaseMapper.countDeployed(tenantId, productId), dataQuality);
    }

    public List<MetricConfigView> configs() {
        return metricMapper.listConfigs(RndContext.tenantId()).stream()
                .map(row -> new MetricConfigView(Rows.lng(row, "id"), Rows.lng(row, "tenantId"),
                        Rows.str(row, "metricCode"), Rows.str(row, "metricName"), Rows.str(row, "metricGroup"),
                        Rows.str(row, "formula"), Rows.str(row, "caliberVersion"), Rows.dec(row, "weight"),
                        Rows.dec(row, "targetValue"), Rows.str(row, "unit"), Rows.str(row, "status")))
                .toList();
    }

    @Transactional
    public MetricConfigView createConfig(MetricConfigCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String code = RndContext.requireText(request.metricCode(), "metricCode");
        metricMapper.upsertConfig(IdWorker.getId(), tenantId, code,
                RndContext.requireText(request.metricName(), "metricName"),
                request.metricGroup() == null ? "delivery" : request.metricGroup(),
                RndContext.requireText(request.formula(), "formula"), "v1", request.weight(), request.targetValue(),
                RndContext.text(request.unit()), "enabled", userId);
        audit.publish("METRIC_CONFIG_UPSERTED", "upsert", "devops_metric_config", code, "medium");
        return configs().stream().filter(c -> c.metricCode().equals(code)).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.METRIC_CONFIG_MISSING));
    }

    @Transactional
    public MetricConfigView updateConfig(String metricCode, MetricConfigUpdateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String code = RndContext.requireText(metricCode, "metricCode");
        Map<String, Object> tenantRow = metricMapper.findTenantConfig(tenantId, code);
        Map<String, Object> row = tenantRow == null ? metricMapper.findPlatformConfig(code) : tenantRow;
        if (row == null) {
            throw new BizException(RndErrorCode.METRIC_CONFIG_MISSING);
        }
        String currentFormula = Rows.str(row, "formula");
        String formula = request.formula() == null ? currentFormula : request.formula().strip();
        // 文档 API-71：口径变更必须走 API-72 发布版本，普通更新不允许直接改公式
        if (!formula.equals(currentFormula)) {
            throw new BizException(RndErrorCode.CALIBER_REQUIRED);
        }
        String name = request.metricName() == null ? Rows.str(row, "metricName") : request.metricName().strip();
        String group = request.metricGroup() == null ? Rows.str(row, "metricGroup") : request.metricGroup().strip();
        BigDecimal weight = request.weight() == null ? Rows.dec(row, "weight") : request.weight();
        BigDecimal target = request.targetValue() == null ? Rows.dec(row, "targetValue") : request.targetValue();
        String unit = request.unit() == null ? Rows.str(row, "unit") : request.unit().strip();
        String status = request.status() == null ? Rows.str(row, "status") : request.status().strip();
        String caliberVersion = Rows.str(row, "caliberVersion");
        if (tenantRow == null) {
            metricMapper.upsertConfig(IdWorker.getId(), tenantId, code, name, group, formula, caliberVersion,
                    weight, target, unit, status, userId);
        } else {
            metricMapper.updateConfig(Rows.lng(tenantRow, "id"), tenantId, name, group, formula, caliberVersion,
                    weight, target, unit, status, userId);
        }
        audit.publish("METRIC_CONFIG_UPDATED", "update", "devops_metric_config", code, "medium");
        return configs().stream().filter(c -> c.metricCode().equals(code)).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.METRIC_CONFIG_MISSING));
    }

    public List<MetricCaliberView> calibers(String metricCode) {
        return metricMapper.listCalibers(RndContext.tenantId(), RndContext.text(metricCode)).stream()
                .map(row -> new MetricCaliberView(Rows.lng(row, "id"), Rows.str(row, "metricCode"),
                        Rows.str(row, "caliberVersion"), Rows.str(row, "formula"),
                        Rows.str(row, "effectiveFrom"), Rows.str(row, "createdAt")))
                .toList();
    }

    @Transactional
    public MetricCaliberView publishCaliber(MetricCaliberCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String code = RndContext.requireText(request.metricCode(), "metricCode");
        String version = RndContext.requireText(request.caliberVersion(), "caliberVersion");
        String formula = RndContext.requireText(request.formula(), "formula");
        String effectiveFrom = request.effectiveFrom() == null
                ? OffsetDateTime.now().toString() : request.effectiveFrom();
        Map<String, Object> tenantRow = metricMapper.findTenantConfig(tenantId, code);
        Map<String, Object> row = tenantRow == null ? metricMapper.findPlatformConfig(code) : tenantRow;
        if (row == null) {
            throw new BizException(RndErrorCode.METRIC_CONFIG_MISSING);
        }
        metricMapper.upsertCaliber(IdWorker.getId(), tenantId, code, version, formula, effectiveFrom, userId);
        // 历史快照保留原版本标记，仅切换配置的当前口径与生效版本
        if (tenantRow == null) {
            metricMapper.upsertConfig(IdWorker.getId(), tenantId, code, Rows.str(row, "metricName"),
                    Rows.str(row, "metricGroup"), formula, version, Rows.dec(row, "weight"),
                    Rows.dec(row, "targetValue"), Rows.str(row, "unit"), Rows.str(row, "status"), userId);
        } else {
            metricMapper.updateConfig(Rows.lng(tenantRow, "id"), tenantId, Rows.str(tenantRow, "metricName"),
                    Rows.str(tenantRow, "metricGroup"), formula, version, Rows.dec(tenantRow, "weight"),
                    Rows.dec(tenantRow, "targetValue"), Rows.str(tenantRow, "unit"),
                    Rows.str(tenantRow, "status"), userId);
        }
        audit.publish("METRIC_CALIBER_PUBLISHED", "publish", "devops_metric_caliber",
                code + ":" + version, "high");
        return calibers(code).stream().filter(c -> c.caliberVersion().equals(version)).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.RESOURCE_NOT_FOUND));
    }

    public MetricDataQualityView dataQuality(String metricCode, String periodType, String periodKey) {
        String type = periodType == null ? "month" : periodType;
        String key = periodKey == null ? YearMonth.now(ZONE).toString() : periodKey;
        String code = RndContext.text(metricCode);
        Map<String, Object> row = metricMapper.aggregateDataQuality(RndContext.tenantId(), code, type, key);
        int sample = row == null ? 0 : Rows.intVal(row, "sampleCount");
        int missing = row == null ? 0 : Rows.intVal(row, "missingCount");
        int outlier = row == null ? 0 : Rows.intVal(row, "outlierCount");
        BigDecimal missingRate = sample == 0
                ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(missing).divide(BigDecimal.valueOf(sample), 6, RoundingMode.HALF_UP);
        String flag = sample == 0 ? "missing" : (missing > 0 ? "missing" : (outlier > 0 ? "outlier" : "normal"));
        return new MetricDataQualityView(code, type, key, sample, missing, outlier, missingRate, flag);
    }

    public List<AlertView> alerts(String status, String metricCode) {
        return metricMapper.listAlerts(RndContext.tenantId(), RndContext.text(status),
                        RndContext.text(metricCode)).stream()
                .map(MetricService::toAlertView)
                .toList();
    }

    @Transactional
    public AlertView createAlert(AlertCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long id = IdWorker.getId();
        metricMapper.insertAlert(id, tenantId, RndContext.requireText(request.metricCode(), "metricCode"),
                request.scopeType() == null ? "product" : request.scopeType(),
                request.scopeId() == null ? 0L : request.scopeId(), request.threshold(),
                request.compareOp() == null ? "gt" : request.compareOp(), RndContext.text(request.notifyChannel()),
                RndContext.userId());
        audit.publish("METRIC_ALERT_CREATED", "create", "devops_metric_alert", id, "low");
        return toAlertView(metricMapper.findAlert(tenantId, id));
    }

    @Transactional
    public AlertView updateAlert(long id, AlertUpdateRequest request) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> row = metricMapper.findAlert(tenantId, id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        BigDecimal threshold = request.threshold() == null ? Rows.dec(row, "threshold") : request.threshold();
        String compareOp = request.compareOp() == null ? Rows.str(row, "compareOp") : request.compareOp();
        String notifyChannel = request.notifyChannel() == null
                ? Rows.str(row, "notifyChannel") : request.notifyChannel();
        String status = request.status() == null ? Rows.str(row, "status") : request.status();
        metricMapper.updateAlert(tenantId, id, threshold, compareOp, notifyChannel, status, RndContext.userId());
        audit.publish("METRIC_ALERT_UPDATED", "update", "devops_metric_alert", id, "low");
        return toAlertView(metricMapper.findAlert(tenantId, id));
    }

    @Transactional
    public AlertView resolveAlert(long id) {
        long tenantId = RndContext.tenantId();
        if (metricMapper.findAlert(tenantId, id) == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        metricMapper.resolveAlert(tenantId, id, RndContext.userId());
        audit.publish("METRIC_ALERT_RESOLVED", "resolve", "devops_metric_alert", id, "low");
        return toAlertView(metricMapper.findAlert(tenantId, id));
    }

    private static AlertView toAlertView(Map<String, Object> row) {
        return new AlertView(Rows.lng(row, "id"), Rows.str(row, "metricCode"),
                Rows.str(row, "scopeType"), Rows.lng(row, "scopeId"), Rows.dec(row, "threshold"),
                Rows.str(row, "compareOp"), Rows.dec(row, "triggeredValue"), Rows.str(row, "status"),
                Rows.str(row, "notifyChannel"), Rows.str(row, "triggeredAt"));
    }

    @Transactional
    public List<AlertView> evaluateAlerts(long productId, String periodType, String periodKey) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String type = periodType == null ? "month" : periodType;
        String key = periodKey == null ? YearMonth.now(ZONE).toString() : periodKey;
        for (AlertView alert : alerts("active", null)) {
            if (!"product".equals(alert.scopeType()) || (alert.scopeId() != null && alert.scopeId() != productId)) {
                continue;
            }
            BigDecimal value = metricMapper.findSnapshotValue(tenantId, alert.metricCode(), productId, type, key);
            if (value == null) {
                continue;
            }
            boolean hit = switch (alert.compareOp()) {
                case "gt" -> value.compareTo(alert.threshold()) > 0;
                case "gte" -> value.compareTo(alert.threshold()) >= 0;
                case "lt" -> value.compareTo(alert.threshold()) < 0;
                case "lte" -> value.compareTo(alert.threshold()) <= 0;
                default -> false;
            };
            if (hit) {
                metricMapper.triggerAlert(tenantId, alert.id(), value, userId);
            }
        }
        return alerts(null, null);
    }

    private MetricValueView record(long tenantId, long productId, String periodType, String periodKey,
                                   String code, String name, String group, BigDecimal value, String unit,
                                   String quality, BigDecimal numerator, BigDecimal denominator, int sample,
                                   long userId) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.setScale(6, RoundingMode.HALF_UP);
        metricMapper.upsertSnapshot(IdWorker.getId(), tenantId, code, productId, periodType, periodKey,
                normalized, numerator, denominator, sample, quality, userId);
        return new MetricValueView(code, name, group, normalized, unit, quality, "v1");
    }

    private BigDecimal averageRequirementLeadTime(long tenantId, long productId) {
        List<Map<String, Object>> rows = requirementMapper.listDeliveredTimestamps(tenantId, productId);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long totalMillis = 0;
        for (Map<String, Object> row : rows) {
            long created = Rows.lngVal(row, "createdAtMillis");
            long delivered = Rows.lngVal(row, "deliveredAtMillis");
            totalMillis += Math.max(delivered - created, 0);
        }
        return BigDecimal.valueOf(totalMillis)
                .divide(BigDecimal.valueOf(rows.size() * 3600_000L), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private static String doraLevel(BigDecimal deployFreq, BigDecimal leadTime, BigDecimal cfr, BigDecimal mttr) {
        if (deployFreq.compareTo(BigDecimal.ONE) >= 0 && leadTime.compareTo(BigDecimal.valueOf(24)) <= 0
                && cfr.compareTo(BigDecimal.valueOf(0.15)) <= 0 && mttr.compareTo(BigDecimal.valueOf(4)) <= 0) {
            return "elite";
        }
        if (deployFreq.compareTo(BigDecimal.valueOf(0.1)) >= 0 && leadTime.compareTo(BigDecimal.valueOf(168)) <= 0
                && cfr.compareTo(BigDecimal.valueOf(0.3)) <= 0) {
            return "high";
        }
        if (cfr.compareTo(BigDecimal.valueOf(0.5)) <= 0) {
            return "medium";
        }
        return "low";
    }

    private Range range(long tenantId, String periodType, String periodKey) {
        if (periodKey.startsWith("sprint:")) {
            long sprintId = Long.parseLong(periodKey.substring("sprint:".length()));
            Map<String, Object> sprint = sprintMapper.findSprint(tenantId, sprintId);
            if (sprint == null) {
                throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
            }
            LocalDate start = Rows.date(sprint, "startDate");
            LocalDate end = Rows.date(sprint, "endDate").plusDays(1);
            return new Range(atStart(start), atStart(end), ChronoUnit.DAYS.between(start, end));
        }
        if ("week".equals(periodType) || WEEK.matcher(periodKey).matches()) {
            String key = WEEK.matcher(periodKey).matches()
                    ? periodKey : YearMonth.now(ZONE).toString();
            LocalDate monday = WEEK.matcher(key).matches()
                    ? LocalDate.parse(key + "-1", DateTimeFormatter.ISO_WEEK_DATE)
                    : LocalDate.now(ZONE).with(DayOfWeek.MONDAY);
            LocalDate next = monday.plusWeeks(1);
            return new Range(atStart(monday), atStart(next), 7);
        }
        if ("day".equals(periodType) || DAY.matcher(periodKey).matches()) {
            LocalDate day = DAY.matcher(periodKey).matches() ? LocalDate.parse(periodKey) : LocalDate.now(ZONE);
            return new Range(atStart(day), atStart(day.plusDays(1)), 1);
        }
        YearMonth month = MONTH.matcher(periodKey).matches() ? YearMonth.parse(periodKey) : YearMonth.now(ZONE);
        LocalDate first = month.atDay(1);
        LocalDate next = first.plusMonths(1);
        return new Range(atStart(first), atStart(next), month.lengthOfMonth());
    }

    private static OffsetDateTime atStart(LocalDate date) {
        return OffsetDateTime.of(LocalDateTime.of(date, java.time.LocalTime.MIN), ZoneOffset.ofHours(8));
    }

    private record Range(OffsetDateTime from, OffsetDateTime to, long days) {
        String fromText() {
            return from.toString();
        }

        String toText() {
            return to.toString();
        }
    }
}
