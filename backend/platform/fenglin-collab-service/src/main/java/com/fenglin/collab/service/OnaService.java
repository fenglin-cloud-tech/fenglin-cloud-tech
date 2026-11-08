package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.OnaMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.DeptNetworkLinkView;
import com.fenglin.collab.web.dto.CollabDtos.DeptNetworkNodeView;
import com.fenglin.collab.web.dto.CollabDtos.DeptNetworkView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingEfficiencyView;
import com.fenglin.collab.web.dto.CollabDtos.OnaCollaboratorView;
import com.fenglin.collab.web.dto.CollabDtos.OnaConfigRequest;
import com.fenglin.collab.web.dto.CollabDtos.OnaConfigView;
import com.fenglin.collab.web.dto.CollabDtos.OnaEdgeView;
import com.fenglin.collab.web.dto.CollabDtos.OnaGraphView;
import com.fenglin.collab.web.dto.CollabDtos.OnaKeyConnectorView;
import com.fenglin.collab.web.dto.CollabDtos.OnaMetricView;
import com.fenglin.collab.web.dto.CollabDtos.OnaNodeView;
import com.fenglin.collab.web.dto.CollabDtos.OnaSnapshotView;
import com.fenglin.collab.web.dto.CollabDtos.OnaSummary;
import com.fenglin.collab.web.dto.CollabDtos.SelfProfileView;
import com.fenglin.collab.web.dto.CollabDtos.SelfTrendPoint;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织网络分析：图谱、指标、关键连接人、快照、会议效率、部门网络、个人画像与度量配置。
 *
 * <p>图谱与指标由离线快照任务按协作行为（IM/文档/会议/审批/OKR）聚合写入；
 * 本服务负责读取、匿名阈值校验、越权校验与访问审计。快照生成任务由数据工程侧调度。</p>
 */
@Service
public class OnaService {

    private static final int DEFAULT_ANONYMITY_THRESHOLD = 5;
    private static final BigDecimal COST_PER_PERSON_HOUR = BigDecimal.valueOf(100);
    private static final List<String> SCOPES = List.of("SELF", "DEPT", "TENANT");
    private static final List<String> SCOPE_TYPES = List.of("DEPT", "TENANT");

    private final OnaMapper onaMapper;
    private final CollabAudit audit;

    public OnaService(OnaMapper onaMapper, CollabAudit audit) {
        this.onaMapper = onaMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OnaGraphView graph(String graphId, String scopeType, Long scopeDeptId) {
        long tenantId = CollabContext.tenantId();
        if (scopeType != null && !SCOPE_TYPES.contains(scopeType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "scopeType 必须为 DEPT/TENANT");
        }
        Map<String, Object> graph = onaMapper.findGraph(tenantId, graphId);
        if (graph == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "协作网络图谱不存在");
        }
        List<Map<String, Object>> edgeRows = onaMapper.listEdges(tenantId, Rows.str(graph, "graphId"));
        List<OnaEdgeView> edges = new ArrayList<>(edgeRows.size());
        Map<String, Long> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : edgeRows) {
            String sourceType = Rows.str(row, "sourceType");
            long sourceId = Rows.lngVal(row, "sourceId");
            String targetType = Rows.str(row, "targetType");
            long targetId = Rows.lngVal(row, "targetId");
            if (scopeDeptId != null && !("DEPT".equals(sourceType) && sourceId == scopeDeptId)
                    && !("DEPT".equals(targetType) && targetId == scopeDeptId)) {
                continue;
            }
            edges.add(new OnaEdgeView(String.valueOf(sourceId), String.valueOf(targetId),
                    Rows.dec(row, "weight"), Rows.intVal(row, "crossDept") == 1));
            nodes.putIfAbsent(sourceType + ":" + sourceId, sourceId);
            nodes.putIfAbsent(targetType + ":" + targetId, targetId);
        }
        List<OnaNodeView> nodeViews = nodes.entrySet().stream()
                .map(entry -> {
                    String type = entry.getKey().substring(0, entry.getKey().indexOf(':'));
                    return new OnaNodeView(entry.getValue(), type, null,
                            "DEPT".equals(type) ? entry.getValue() : null);
                })
                .toList();
        BigDecimal density = density(nodeViews.size(), edges.size());
        audit.publish("COLLAB_ONA_GRAPH_QUERIED", "READ", "collab_ona_graph",
                Rows.str(graph, "graphId"), "LOW");
        return new OnaGraphView(nodeViews, edges, new OnaSummary(nodeViews.size(), edges.size(), density));
    }

    @Transactional(readOnly = true)
    public OnaMetricView metric(String snapshotId, String nodeType, Long nodeId) {
        long tenantId = CollabContext.tenantId();
        if (nodeId == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "nodeId 不能为空");
        }
        String safeType = nodeType == null || nodeType.isBlank() ? "USER" : nodeType;
        String resolved = resolveSnapshotId(tenantId, snapshotId);
        if (resolved == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "ONA 快照不存在");
        }
        Map<String, Object> row = onaMapper.findMetric(tenantId, resolved, safeType, nodeId);
        if (row == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "ONA 指标不存在");
        }
        audit.publish("COLLAB_ONA_METRIC_QUERIED", "READ", "collab_ona_snapshot", resolved, "LOW");
        return new OnaMetricView(Rows.lngVal(row, "nodeId"), Rows.str(row, "nodeType"),
                Rows.dec(row, "degreeCentrality"), Rows.dec(row, "betweenness"),
                Rows.dec(row, "closeness"), Rows.dec(row, "clusteringCoef"),
                Rows.dec(row, "structuralHole"), Rows.intVal(row, "keyConnector") == 1,
                Rows.dec(row, "crossDeptRatio"));
    }

    @Transactional(readOnly = true)
    public List<OnaKeyConnectorView> keyConnectors(String snapshotId, Integer topN) {
        long tenantId = CollabContext.tenantId();
        String resolved = resolveSnapshotId(tenantId, snapshotId);
        if (resolved == null) {
            return List.of();
        }
        int safeLimit = topN == null ? 20 : Math.min(Math.max(topN, 1), 100);
        audit.publish("COLLAB_ONA_KEY_CONNECTORS_QUERIED", "READ", "collab_ona_snapshot", resolved, "LOW");
        return onaMapper.listKeyConnectors(tenantId, resolved, safeLimit).stream()
                .map(row -> new OnaKeyConnectorView(Rows.lngVal(row, "nodeId"), Rows.str(row, "nodeType"),
                        Rows.dec(row, "degreeCentrality"), Rows.dec(row, "crossDeptRatio"),
                        Rows.lngVal(row, "connections")))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<OnaSnapshotView> snapshots(String periodType, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        String safePeriod = periodType == null || periodType.isBlank() ? null : periodType;
        List<OnaSnapshotView> records = onaMapper
                .listSnapshots(tenantId, safePeriod, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new OnaSnapshotView(Rows.str(row, "snapshotId"), Rows.str(row, "periodType"),
                        Rows.str(row, "periodKey"), Rows.dec(row, "networkDensity"),
                        Rows.dec(row, "avgDegree"), Rows.dec(row, "crossDeptRatio"),
                        Rows.intVal(row, "keyConnectorCount"), Rows.intVal(row, "siloCount"),
                        Rows.instant(row, "generatedAt")))
                .toList();
        audit.publish("COLLAB_ONA_SNAPSHOTS_QUERIED", "READ", "collab_ona_snapshot", null, "LOW");
        return PageResult.of(records, onaMapper.countSnapshots(tenantId, safePeriod), safePage, safeSize);
    }

    /**
     * 会议效率：决策率、准时率、行动完成率、成本估算。
     * 超额参会率/超时率依赖会议计划人数与实际时长，当前模型未建模，固定返回 0 并在 warnings 中标注。
     */
    @Transactional(readOnly = true)
    public MeetingEfficiencyView meetingEfficiency(String periodKey, Long deptId) {
        long tenantId = CollabContext.tenantId();
        YearMonth month = parsePeriod(periodKey);
        OffsetDateTime startAt = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<String, Object> stats = onaMapper.meetingStats(tenantId, startAt, endAt);
        int meetingCount = Rows.intVal(stats, "meetingCount");
        BigDecimal avgMinutes = Rows.dec(stats, "avgMinutes") == null
                ? BigDecimal.ZERO : Rows.dec(stats, "avgMinutes");
        long decided = onaMapper.decidedMeetings(tenantId, startAt, endAt);
        long onTime = onaMapper.onTimeMeetings(tenantId, startAt, endAt);
        Map<String, Object> actions = onaMapper.actionStats(tenantId, startAt, endAt);
        long totalActions = Rows.lngVal(actions, "total");
        long doneActions = Rows.lngVal(actions, "done");
        long checkinTotal = onaMapper.checkinTotal(tenantId, startAt, endAt);

        BigDecimal decisionRate = rate(decided, meetingCount);
        BigDecimal onTimeRate = rate(onTime, meetingCount);
        BigDecimal actionCompletionRate = rate(doneActions, totalActions);
        BigDecimal efficiencyScore = decisionRate.multiply(new BigDecimal("0.4"))
                .add(onTimeRate.multiply(new BigDecimal("0.3")))
                .add(actionCompletionRate.multiply(new BigDecimal("0.3")))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal cost = BigDecimal.valueOf(checkinTotal)
                .multiply(avgMinutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                .multiply(COST_PER_PERSON_HOUR)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> warnings = new ArrayList<>();
        if (meetingCount == 0) {
            warnings.add("统计周期内无已结束会议");
        }
        if (deptId != null) {
            warnings.add("云协会议未建模所属部门，本次返回租户整体数据");
        }
        warnings.add("参会人数与计划时长未建模，超额参会率/超时率暂按 0 返回");
        warnings.add("岗位成本系数未配置，按 100 元/人时估算");

        audit.publish("COLLAB_ONA_MEETING_EFFICIENCY_QUERIED", "READ", "collab_meeting", null, "LOW");
        return new MeetingEfficiencyView(efficiencyScore, decisionRate, onTimeRate, actionCompletionRate,
                BigDecimal.ZERO, BigDecimal.ZERO, cost, warnings);
    }

    /**
     * 部门协作网络：读取最近快照所属图谱中的 DEPT 级边，按部门聚合。
     * 匿名阈值：部门节点数低于配置阈值时返回 2009001。
     */
    @Transactional(readOnly = true)
    public DeptNetworkView deptNetwork(Long deptId, String level) {
        long tenantId = CollabContext.tenantId();
        if (level != null && !"DEPT".equals(level)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "level 仅支持 DEPT");
        }
        Map<String, Object> latest = onaMapper.findLatestSnapshot(tenantId);
        if (latest == null) {
            throw new BizException(CollabErrorCode.ONA_SAMPLE_INSUFFICIENT, "暂无 ONA 快照");
        }
        String graphId = Rows.str(latest, "graphId");
        List<Map<String, Object>> linkRows = onaMapper.listDeptLinks(tenantId, graphId);
        Map<Long, Long> internal = new LinkedHashMap<>();
        List<DeptNetworkLinkView> links = new ArrayList<>(linkRows.size());
        for (Map<String, Object> row : linkRows) {
            long source = Rows.lngVal(row, "sourceDeptId");
            long target = Rows.lngVal(row, "targetDeptId");
            if (deptId != null && source != deptId && target != deptId) {
                continue;
            }
            long edgeCount = Rows.lngVal(row, "edgeCount");
            links.add(new DeptNetworkLinkView(String.valueOf(source), String.valueOf(target), edgeCount,
                    Rows.dec(row, "weight")));
            internal.putIfAbsent(source, 0L);
            internal.putIfAbsent(target, 0L);
            if (source == target) {
                internal.merge(source, edgeCount, Long::sum);
            }
        }
        int threshold = config().anonymityThreshold();
        if (internal.size() < threshold) {
            throw new BizException(CollabErrorCode.ONA_SAMPLE_INSUFFICIENT,
                    "部门节点数低于匿名阈值 " + threshold);
        }
        List<DeptNetworkNodeView> departments = internal.entrySet().stream()
                .map(entry -> new DeptNetworkNodeView(String.valueOf(entry.getKey()), 1, entry.getValue()))
                .toList();
        audit.publish("COLLAB_ONA_DEPT_NETWORK_QUERIED", "READ", "collab_ona_graph", graphId, "MEDIUM");
        return new DeptNetworkView(departments.size(), true, departments, links);
    }

    @Transactional(readOnly = true)
    public SelfProfileView selfProfile(String permissions, String userIdParam) {
        long tenantId = CollabContext.tenantId();
        long currentUserId = CollabContext.userId();
        long targetUserId = currentUserId;
        if (userIdParam != null && !userIdParam.isBlank()) {
            try {
                targetUserId = Long.parseLong(userIdParam);
            } catch (NumberFormatException ex) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "userId 非法");
            }
        }
        if (targetUserId != currentUserId) {
            PermissionGuard.require(permissions, "tenant:collab:ona:dept");
        }
        int threshold = config().anonymityThreshold();
        Map<String, Object> latest = onaMapper.findLatestSnapshot(tenantId);
        int myDegree = 0;
        BigDecimal crossDeptRatio = BigDecimal.ZERO;
        List<OnaCollaboratorView> collaborators = List.of();
        if (latest != null) {
            String graphId = Rows.str(latest, "graphId");
            myDegree = degreeOfNode(tenantId, graphId, targetUserId);
            if (targetUserId != currentUserId && myDegree < threshold) {
                throw new BizException(CollabErrorCode.ONA_PROFILE_FORBIDDEN);
            }
            Map<String, Object> metric = onaMapper.findUserMetric(tenantId, Rows.str(latest, "snapshotId"),
                    targetUserId);
            if (metric != null) {
                crossDeptRatio = Rows.dec(metric, "crossDeptRatio") == null
                        ? BigDecimal.ZERO : Rows.dec(metric, "crossDeptRatio");
            }
            collaborators = collectCollaborators(tenantId, graphId, targetUserId);
        }
        List<SelfTrendPoint> trend = onaMapper.listUserTrend(tenantId, targetUserId, 12).stream()
                .map(row -> new SelfTrendPoint(Rows.str(row, "periodKey"),
                        Rows.dec(row, "degree") == null ? 0 : Rows.dec(row, "degree").intValue()))
                .toList();
        audit.publish("COLLAB_ONA_SELF_PROFILE_QUERIED", "READ", "collab_ona_snapshot", null, "LOW");
        return new SelfProfileView(myDegree, crossDeptRatio, collaborators, trend);
    }

    @Transactional(readOnly = true)
    public OnaConfigView config() {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> row = onaMapper.findConfig(tenantId);
        if (row == null) {
            return new OnaConfigView(new BigDecimal("0.30"), new BigDecimal("0.25"), new BigDecimal("0.25"),
                    new BigDecimal("0.10"), new BigDecimal("0.10"), DEFAULT_ANONYMITY_THRESHOLD,
                    new BigDecimal("0.05"), "SELF");
        }
        return new OnaConfigView(Rows.dec(row, "weightIm"), Rows.dec(row, "weightDoc"),
                Rows.dec(row, "weightMeeting"), Rows.dec(row, "weightApproval"), Rows.dec(row, "weightOkr"),
                Rows.intVal(row, "anonymityThreshold"), Rows.dec(row, "keyConnectorTopRatio"),
                Rows.str(row, "profileVisibleScope"));
    }

    @Transactional
    public OnaConfigView saveConfig(OnaConfigRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "ONA 配置不能为空");
        }
        OnaConfigView current = config();
        BigDecimal im = weight(request.weightIm(), current.weightIm());
        BigDecimal doc = weight(request.weightDoc(), current.weightDoc());
        BigDecimal meeting = weight(request.weightMeeting(), current.weightMeeting());
        BigDecimal approval = weight(request.weightApproval(), current.weightApproval());
        BigDecimal okr = weight(request.weightOkr(), current.weightOkr());
        BigDecimal sum = im.add(doc).add(meeting).add(approval).add(okr);
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "度量权重之和必须为 1");
        }
        int threshold = request.anonymityThreshold() == null
                ? current.anonymityThreshold() : request.anonymityThreshold();
        if (threshold < 3 || threshold > 100) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "匿名阈值需在 3-100");
        }
        BigDecimal topRatio = request.keyConnectorTopRatio() == null
                ? current.keyConnectorTopRatio() : request.keyConnectorTopRatio();
        if (topRatio.compareTo(BigDecimal.ZERO) <= 0 || topRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "关键连接人比例需在 (0,1]");
        }
        String scope = request.profileVisibleScope() == null
                ? current.profileVisibleScope() : request.profileVisibleScope();
        if (!SCOPES.contains(scope)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "画像可见范围必须为 SELF/DEPT/TENANT");
        }
        onaMapper.upsertConfig(IdWorker.getId(), tenantId, im, doc, meeting, approval, okr, threshold,
                topRatio, scope, userId);
        audit.publish("COLLAB_ONA_CONFIG_UPDATED", "UPDATE", "collab_ona_config", null, "MEDIUM");
        return new OnaConfigView(im, doc, meeting, approval, okr, threshold, topRatio, scope);
    }

    private String resolveSnapshotId(long tenantId, String snapshotId) {
        if (snapshotId != null && !snapshotId.isBlank()) {
            return snapshotId;
        }
        Map<String, Object> latest = onaMapper.findLatestSnapshot(tenantId);
        return latest == null ? null : Rows.str(latest, "snapshotId");
    }

    private List<OnaCollaboratorView> collectCollaborators(long tenantId, String graphId, long userId) {
        if (graphId == null) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        return onaMapper.listUserEdges(tenantId, graphId, userId, 50).stream()
                .filter(row -> {
                    long sourceId = Rows.lngVal(row, "sourceId");
                    long partnerId = sourceId == userId ? Rows.lngVal(row, "targetId") : sourceId;
                    return seen.add(partnerId);
                })
                .limit(10)
                .map(row -> {
                    long sourceId = Rows.lngVal(row, "sourceId");
                    long partnerId = sourceId == userId ? Rows.lngVal(row, "targetId") : sourceId;
                    return new OnaCollaboratorView(partnerId, null,
                            Rows.dec(row, "weight") == null ? BigDecimal.ZERO : Rows.dec(row, "weight"));
                }).toList();
    }

    private int degreeOfNode(long tenantId, String graphId, long userId) {
        if (graphId == null) {
            return 0;
        }
        Set<Long> partners = new LinkedHashSet<>();
        onaMapper.listUserEdges(tenantId, graphId, userId, 1000).forEach(row -> {
            long sourceId = Rows.lngVal(row, "sourceId");
            partners.add(sourceId == userId ? Rows.lngVal(row, "targetId") : sourceId);
        });
        return partners.size();
    }

    private YearMonth parsePeriod(String periodKey) {
        if (periodKey == null || periodKey.isBlank()) {
            return YearMonth.from(LocalDate.now(ZoneOffset.UTC));
        }
        try {
            return YearMonth.parse(periodKey);
        } catch (RuntimeException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "periodKey 必须为 YYYY-MM");
        }
    }

    private BigDecimal weight(BigDecimal value, BigDecimal fallback) {
        BigDecimal result = value == null ? fallback : value;
        if (result.compareTo(BigDecimal.ZERO) < 0 || result.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "度量权重需在 [0,1]");
        }
        return result;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal density(int nodeCount, int edgeCount) {
        if (nodeCount < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal possible = BigDecimal.valueOf((long) nodeCount * (nodeCount - 1));
        return BigDecimal.valueOf(edgeCount).multiply(BigDecimal.valueOf(2))
                .divide(possible, 4, RoundingMode.HALF_UP);
    }
}
