package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.OkrMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.AlignRequest;
import com.fenglin.collab.web.dto.CollabDtos.AlignResult;
import com.fenglin.collab.web.dto.CollabDtos.AlignmentGraph;
import com.fenglin.collab.web.dto.CollabDtos.GraphEdge;
import com.fenglin.collab.web.dto.CollabDtos.GraphNode;
import com.fenglin.collab.web.dto.CollabDtos.KrCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.KrUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.KrView;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveView;
import com.fenglin.collab.web.dto.CollabDtos.PeriodUpsertRequest;
import com.fenglin.collab.web.dto.CollabDtos.PeriodView;
import com.fenglin.collab.web.dto.CollabDtos.ProgressRequest;
import com.fenglin.collab.web.dto.CollabDtos.ProgressResult;
import com.fenglin.common.core.BizException;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OKR 主链：周期、目标、KR、对齐（递归防环）、进展更新、对齐图。
 */
@Service
public class OkrService {

    private static final Set<String> OBJ_TYPES = Set.of("COMPANY", "DEPT", "TEAM", "PERSONAL");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "DEPT", "ALL");
    private static final Set<String> METRIC_TYPES = Set.of("PERCENT", "NUMBER", "BOOLEAN");
    private static final Set<String> CONFIDENCE = Set.of("HIGH", "MID", "LOW");

    private final OkrMapper okrMapper;
    private final CollabCrypto crypto;
    private final CollabAudit audit;

    public OkrService(OkrMapper okrMapper, CollabCrypto crypto, CollabAudit audit) {
        this.okrMapper = okrMapper;
        this.crypto = crypto;
        this.audit = audit;
    }

    /**
     * 缺口补齐 API-OKR-050：创建/更新周期。
     */
    @Transactional
    public PeriodView createPeriod(PeriodUpsertRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.name() == null || request.startDate() == null || request.endDate() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期名称与起止日期不能为空");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期结束日期不能早于开始日期");
        }
        String periodId = UUID.randomUUID().toString();
        String status = request.status() == null ? "OPEN" : request.status();
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期状态必须为 OPEN/CLOSED");
        }
        okrMapper.insertPeriod(periodId, tenantId, request.name(), request.startDate(), request.endDate(),
                request.scorePolicy() == null ? "AVG_KR" : request.scorePolicy(), status, userId);
        audit.publish("COLLAB_OKR_PERIOD_CREATED", "CREATE", "collab_okr_period", periodId, "LOW");
        return new PeriodView(periodId, request.name(), request.startDate(), request.endDate(), status);
    }

    /**
     * 管理端周期配置（API-OKR-050）：按名称幂等 upsert。
     */
    @Transactional
    public PeriodView upsertPeriod(PeriodUpsertRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.name() == null || request.name().isBlank()
                || request.startDate() == null || request.endDate() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期名称与起止日期不能为空");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期结束日期不能早于开始日期");
        }
        String status = request.status() == null ? "OPEN" : request.status();
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期状态必须为 OPEN/CLOSED");
        }
        String scorePolicy = request.scorePolicy() == null ? "AVG_KR" : request.scorePolicy();
        Map<String, Object> existing = okrMapper.findPeriodByName(tenantId, request.name());
        String periodId;
        if (existing == null) {
            periodId = UUID.randomUUID().toString();
            okrMapper.insertPeriod(periodId, tenantId, request.name(), request.startDate(), request.endDate(),
                    scorePolicy, status, userId);
        } else {
            periodId = Rows.str(existing, "periodId");
            okrMapper.updatePeriod(tenantId, periodId, request.startDate(), request.endDate(), scorePolicy,
                    status, userId);
        }
        audit.publish("COLLAB_OKR_PERIOD_UPSERTED", "UPDATE", "collab_okr_period", periodId, "LOW");
        return new PeriodView(periodId, request.name(), request.startDate(), request.endDate(), status);
    }

    @Transactional(readOnly = true)
    public List<PeriodView> listPeriods() {
        long tenantId = CollabContext.tenantId();
        return okrMapper.listPeriods(tenantId).stream()
                .map(row -> new PeriodView(Rows.str(row, "periodId"), Rows.str(row, "name"),
                        Rows.date(row, "startDate"), Rows.date(row, "endDate"), Rows.str(row, "status")))
                .toList();
    }

    @Transactional
    public void closePeriod(String periodId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (okrMapper.findPeriod(tenantId, periodId) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "OKR 周期不存在");
        }
        okrMapper.updatePeriodStatus(tenantId, periodId, "CLOSED", userId);
        audit.publish("COLLAB_OKR_PERIOD_CLOSED", "UPDATE", "collab_okr_period", periodId, "MEDIUM");
    }

    /**
     * 重开已关闭周期。关闭只是写状态，不锁定历史数据；重开同样只改状态，便于跨季度复盘。
     */
    @Transactional
    public PeriodView reopenPeriod(String periodId) {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> period = okrMapper.findPeriod(tenantId, periodId);
        if (period == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "OKR 周期不存在");
        }
        okrMapper.reopenPeriod(tenantId, periodId, CollabContext.userId());
        audit.publish("COLLAB_OKR_PERIOD_REOPENED", "UPDATE", "collab_okr_period", periodId, "MEDIUM");
        return new PeriodView(periodId, Rows.str(period, "name"), Rows.date(period, "startDate"),
                Rows.date(period, "endDate"), "OPEN");
    }

    @Transactional
    public ObjectiveView updateObjective(String objectiveId, ObjectiveUpdateRequest request) {
        long tenantId = CollabContext.tenantId();
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目标标题不能为空");
        }
        if (okrMapper.findObjective(tenantId, objectiveId) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        String objType = request.objType() == null ? "PERSONAL" : request.objType();
        if (!OBJ_TYPES.contains(objType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目标类型必须为 COMPANY/DEPT/TEAM/PERSONAL");
        }
        String visibility = request.visibility() == null ? "DEPT" : request.visibility();
        if (!VISIBILITIES.contains(visibility)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "可见范围必须为 PRIVATE/DEPT/ALL");
        }
        okrMapper.updateObjective(tenantId, objectiveId, objType, request.deptId(), request.title(),
                crypto.encrypt(tenantId, "collab_okr_objective", "description", request.description()),
                visibility, CollabContext.userId());
        audit.publish("COLLAB_OKR_OBJECTIVE_UPDATED", "UPDATE", "collab_okr_objective", objectiveId, "LOW");
        return objectiveView(tenantId, okrMapper.findObjective(tenantId, objectiveId));
    }

    /**
     * 删除目标（软删）：同时软删其下 KR 与对齐关系，避免对齐图出现指向已删目标的悬挂边。
     */
    @Transactional
    public void deleteObjective(String objectiveId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (okrMapper.findObjective(tenantId, objectiveId) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        okrMapper.deleteKrsByObjective(tenantId, objectiveId, userId);
        okrMapper.deleteAlignmentsByObjective(tenantId, objectiveId, userId);
        okrMapper.deleteObjective(tenantId, objectiveId, userId);
        audit.publish("COLLAB_OKR_OBJECTIVE_DELETED", "DELETE", "collab_okr_objective", objectiveId, "MEDIUM");
    }

    @Transactional
    public KrView updateKr(String krId, KrUpdateRequest request) {
        long tenantId = CollabContext.tenantId();
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "KR 标题不能为空");
        }
        if (okrMapper.findKr(tenantId, krId) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "关键结果不存在");
        }
        String metricType = request.metricType() == null ? "PERCENT" : request.metricType();
        if (!METRIC_TYPES.contains(metricType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "指标类型必须为 PERCENT/NUMBER/BOOLEAN");
        }
        String confidence = request.confidence() == null ? "MID" : request.confidence();
        if (!CONFIDENCE.contains(confidence)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "信心指数必须为 HIGH/MID/LOW");
        }
        okrMapper.updateKr(tenantId, krId, request.title(), metricType, request.targetValue(),
                request.currentValue(), confidence, CollabContext.userId());
        audit.publish("COLLAB_OKR_KR_UPDATED", "UPDATE", "collab_okr_key_result", krId, "LOW");
        return krView(okrMapper.findKr(tenantId, krId));
    }

    @Transactional
    public void deleteKr(String krId) {
        long tenantId = CollabContext.tenantId();
        if (okrMapper.findKr(tenantId, krId) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "关键结果不存在");
        }
        okrMapper.deleteKr(tenantId, krId, CollabContext.userId());
        audit.publish("COLLAB_OKR_KR_DELETED", "DELETE", "collab_okr_key_result", krId, "MEDIUM");
    }

    /**
     * 跨周期汇总：每周期目标数、KR 数与平均进度；ownerId 为空表示全租户。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> periodSummary(Long ownerId) {
        return okrMapper.periodSummary(CollabContext.tenantId(), ownerId);
    }

    @Transactional
    public ObjectiveView createObjective(ObjectiveCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.periodId() == null || request.title() == null
                || request.title().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "周期与目标标题不能为空");
        }
        Map<String, Object> period = okrMapper.findPeriod(tenantId, request.periodId());
        if (period == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "OKR 周期不存在");
        }
        if ("CLOSED".equals(Rows.str(period, "status"))) {
            throw new BizException(CollabErrorCode.OKR_PERIOD_CLOSED);
        }
        String objType = request.objType() == null ? "PERSONAL" : request.objType();
        if (!OBJ_TYPES.contains(objType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目标类型必须为 COMPANY/DEPT/TEAM/PERSONAL");
        }
        String visibility = request.visibility() == null ? "DEPT" : request.visibility();
        if (!VISIBILITIES.contains(visibility)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "可见范围必须为 PRIVATE/DEPT/ALL");
        }
        String objectiveId = UUID.randomUUID().toString();
        okrMapper.insertObjective(objectiveId, tenantId, request.periodId(), userId, objType, request.deptId(),
                request.title(), crypto.encrypt(tenantId, "collab_okr_objective", "description",
                        request.description()), visibility);
        audit.publish("COLLAB_OKR_OBJECTIVE_CREATED", "CREATE", "collab_okr_objective", objectiveId, "LOW");
        return objectiveView(tenantId, okrMapper.findObjective(tenantId, objectiveId));
    }

    @Transactional(readOnly = true)
    public List<ObjectiveView> listObjectives(String periodId, Long ownerId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        return okrMapper.listObjectives(tenantId, userId, periodId, ownerId).stream()
                .map(row -> objectiveView(tenantId, row)).toList();
    }

    /**
     * 员工自助：只返回 owner_id 等于当前登录用户的目标。
     */
    @Transactional(readOnly = true)
    public List<ObjectiveView> myObjectives(String periodId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        return okrMapper.listObjectives(tenantId, userId, periodId, userId).stream()
                .map(row -> objectiveView(tenantId, row)).toList();
    }

    /**
     * 缺口补齐 API-OKR-006：为对齐后的目标创建关键结果。
     */
    /**
     * 员工自助：在本人名下目标下创建 KR（self 作用域）。
     * 目标归属只从服务端上下文取，不接受请求体传入，非本人目标一律 403。
     */
    @Transactional
    public KrView createKrSelf(String objectiveId, KrCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> objective = okrMapper.findObjective(tenantId, objectiveId);
        if (objective == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (Rows.lngVal(objective, "ownerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "只能在自己的目标下创建 KR");
        }
        return createKr(objectiveId, request);
    }

    @Transactional
    public KrView createKr(String objectiveId, KrCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> objective = okrMapper.findObjective(tenantId, objectiveId);
        if (objective == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (Rows.lngVal(objective, "ownerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅目标负责人可创建 KR");
        }
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "KR 标题不能为空");
        }
        String metricType = request.metricType() == null ? "PERCENT" : request.metricType();
        if (!METRIC_TYPES.contains(metricType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "指标类型必须为 PERCENT/NUMBER/BOOLEAN");
        }
        String confidence = request.confidence() == null ? "MID" : request.confidence();
        if (!CONFIDENCE.contains(confidence)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "信心指数必须为 HIGH/MID/LOW");
        }
        BigDecimal target = request.targetValue();
        BigDecimal current = request.currentValue() == null ? BigDecimal.ZERO : request.currentValue();
        BigDecimal progress = BigDecimal.ZERO;
        if ("PERCENT".equals(metricType) && target != null && target.signum() > 0) {
            progress = current.multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
        }
        if (progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            progress = BigDecimal.valueOf(100);
        }
        String krId = UUID.randomUUID().toString();
        okrMapper.insertKr(krId, tenantId, objectiveId, userId, request.title(), metricType, target, current,
                progress, confidence, userId);
        audit.publish("COLLAB_OKR_KR_CREATED", "CREATE", "collab_okr_key_result", krId, "LOW");
        return krView(okrMapper.findKr(tenantId, krId));
    }

    @Transactional
    public AlignResult align(String objectiveId, AlignRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.parentObjectiveId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "对齐父目标不能为空");
        }
        if (objectiveId.equals(request.parentObjectiveId())) {
            throw new BizException(CollabErrorCode.OKR_ALIGN_CYCLE);
        }
        Map<String, Object> child = okrMapper.findObjective(tenantId, objectiveId);
        Map<String, Object> parent = okrMapper.findObjective(tenantId, request.parentObjectiveId());
        if (child == null || parent == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (Rows.lngVal(child, "ownerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅目标负责人可设置对齐");
        }
        String alignType = request.alignType() == null ? "VERTICAL" : request.alignType();
        if (!Set.of("VERTICAL", "HORIZONTAL").contains(alignType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "对齐类型必须为 VERTICAL/HORIZONTAL");
        }
        if (okrMapper.countCycle(tenantId, objectiveId, request.parentObjectiveId()) > 0) {
            throw new BizException(CollabErrorCode.OKR_ALIGN_CYCLE);
        }
        Map<String, Object> existing = okrMapper.findAlignment(tenantId, objectiveId, request.parentObjectiveId());
        if (existing != null) {
            return new AlignResult(Rows.lngVal(existing, "alignmentId"));
        }
        long alignmentId = IdWorker.getId();
        okrMapper.insertAlignment(alignmentId, tenantId, objectiveId, request.parentObjectiveId(), alignType,
                request.weight(), userId);
        audit.publish("COLLAB_OKR_ALIGNED", "CREATE", "collab_okr_alignment", alignmentId, "LOW");
        return new AlignResult(alignmentId);
    }

    @Transactional
    public ProgressResult updateProgress(String krId, ProgressRequest request, String permissions) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> kr = okrMapper.findKr(tenantId, krId);
        if (kr == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "KR 不存在");
        }
        if (request == null || request.progress() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "进度不能为空");
        }
        BigDecimal progress = request.progress();
        if (progress.signum() < 0 || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException(CollabErrorCode.KR_PROGRESS_OUT_OF_RANGE);
        }
        String confidence = request.confidence() == null ? Rows.str(kr, "confidence") : request.confidence();
        if (!CONFIDENCE.contains(confidence)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "信心指数必须为 HIGH/MID/LOW");
        }
        Map<String, Object> objective = okrMapper.findObjective(tenantId, Rows.str(kr, "objectiveId"));
        if (objective == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        // 员工只可更新自己目标的进度；管理端（okr:manage）保留跨人维护能力
        if (Rows.lngVal(objective, "ownerId") != userId
                && !PermissionGuard.has(permissions, "biz:collab:okr:manage")) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅目标负责人可更新进度");
        }
        Map<String, Object> period = okrMapper.findPeriod(tenantId, Rows.str(objective, "periodId"));
        if (period != null && "CLOSED".equals(Rows.str(period, "status"))) {
            throw new BizException(CollabErrorCode.OKR_PERIOD_CLOSED);
        }
        BigDecimal target = Rows.dec(kr, "targetValue");
        BigDecimal current = target == null ? null
                : target.multiply(progress).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        okrMapper.updateKrProgress(tenantId, krId, progress, current, confidence, userId);
        okrMapper.insertProgress(IdWorker.getId(), tenantId, krId, progress, confidence,
                crypto.encrypt(tenantId, "collab_okr_progress", "note", request.note()), userId);
        audit.publish("COLLAB_OKR_PROGRESS_UPDATED", "UPDATE", "collab_okr_key_result", krId, "LOW");
        return new ProgressResult(progress, confidence, Instant.now());
    }

    @Transactional(readOnly = true)
    public AlignmentGraph alignmentGraph(String periodId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        List<Map<String, Object>> objectives = okrMapper.listObjectives(tenantId, userId, periodId, null);
        List<GraphNode> nodes = objectives.stream()
                .map(row -> new GraphNode(Rows.str(row, "objectiveId"), Rows.str(row, "title"),
                        Rows.str(row, "objType"), Rows.lng(row, "ownerId")))
                .toList();
        List<GraphEdge> edges = okrMapper.listAlignments(tenantId, periodId).stream()
                .map(row -> new GraphEdge(Rows.str(row, "childId"), Rows.str(row, "parentId"),
                        Rows.str(row, "alignType")))
                .toList();
        return new AlignmentGraph(nodes, edges);
    }

    private ObjectiveView objectiveView(long tenantId, Map<String, Object> row) {
        if (row == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        String objectiveId = Rows.str(row, "objectiveId");
        List<KrView> krs = okrMapper.listKrs(tenantId, objectiveId).stream().map(this::krView).toList();
        return new ObjectiveView(objectiveId, Rows.str(row, "periodId"), Rows.lngVal(row, "ownerId"),
                Rows.str(row, "objType"), Rows.str(row, "title"), Rows.str(row, "visibility"),
                Rows.str(row, "status"), krs);
    }

    private KrView krView(Map<String, Object> row) {
        return new KrView(Rows.str(row, "krId"), Rows.str(row, "title"), Rows.str(row, "metricType"),
                Rows.dec(row, "targetValue"), Rows.dec(row, "currentValue"), Rows.dec(row, "progress"),
                Rows.str(row, "confidence"));
    }
}
