package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.OkrMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentNode;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentTree;
import com.fenglin.talent.web.dto.OkrDtos.KrCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.KrCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.KrProgressRequest;
import com.fenglin.talent.web.dto.OkrDtos.KrProgressResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveReviewRequest;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveReviewResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveView;
import com.fenglin.talent.web.dto.OkrDtos.PeriodCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.PeriodCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.PeriodView;
import com.fenglin.talent.web.dto.OkrDtos.ProposalCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalListItem;
import com.fenglin.talent.web.dto.OkrDtos.ProposalReviewRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalReviewResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalStatusResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalVoteRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalVoteResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C5 OKR 与创新：周期/目标/KR 加权进度/对齐网络/复盘、创新提案评审与立项。
 */
@Service
public class OkrService {

    private static final Set<String> PERIOD_TYPES = Set.of("YEAR", "QUARTER", "MONTH");
    private static final Set<String> OBJ_LEVELS = Set.of("COMPANY", "DEPT", "PERSONAL");
    private static final Set<String> METRIC_TYPES = Set.of("NUMBER", "PERCENT", "MILESTONE");
    private static final Set<String> ALIGN_TYPES = Set.of("ALIGN", "SUPPORT", "COLLAB");
    private static final Set<String> REVIEW_RESULTS = Set.of("PASS", "HOLD", "REJECT");

    private final OkrMapper okrMapper;
    private final TalentAudit audit;
    private final ObjectMapper objectMapper;

    public OkrService(OkrMapper okrMapper, TalentAudit audit, ObjectMapper objectMapper) {
        this.okrMapper = okrMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ---------- 周期 ----------

    @Transactional
    public PeriodCreateResult createPeriod(PeriodCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.periodName()) || isBlank(request.periodType())
                || request.startDate() == null || request.endDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期名称、类型与起止日期不能为空");
        }
        if (!PERIOD_TYPES.contains(request.periodType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期类型必须为 YEAR/QUARTER/MONTH");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "结束日期必须晚于开始日期");
        }
        long id = IdWorker.getId();
        okrMapper.insertPeriod(id, tenantId, request.periodName(), request.periodType(), request.startDate(),
                request.endDate(), request.reviewStart(), request.reviewEnd(), userId);
        audit.publish("TALENT_OKR_PERIOD_CREATED", "CREATE", "talent_okr_period", id, "LOW");
        return new PeriodCreateResult(id, "PLANNING");
    }

    @Transactional(readOnly = true)
    public List<PeriodView> listPeriods() {
        long tenantId = TalentContext.tenantId();
        return okrMapper.listPeriods(tenantId).stream()
                .map(row -> new PeriodView(Rows.lngVal(row, "id"), Rows.str(row, "periodName"),
                        Rows.str(row, "periodType"), Rows.date(row, "startDate"), Rows.date(row, "endDate"),
                        Rows.date(row, "reviewStart"), Rows.date(row, "reviewEnd"),
                        Rows.str(row, "periodStatus")))
                .toList();
    }

    // ---------- 目标 ----------

    @Transactional
    public ObjectiveCreateResult createObjective(ObjectiveCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.periodId() == null || isBlank(request.objLevel())
                || isBlank(request.objective())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期、层级与目标内容不能为空");
        }
        if (!OBJ_LEVELS.contains(request.objLevel())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "目标层级必须为 COMPANY/DEPT/PERSONAL");
        }
        if (okrMapper.findPeriod(tenantId, request.periodId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "OKR 周期不存在");
        }
        if (request.parentObjectiveId() != null) {
            Map<String, Object> parent = okrMapper.findObjective(tenantId, request.parentObjectiveId());
            if (parent == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "父目标不存在");
            }
            if (Rows.lngVal(parent, "periodId") != request.periodId()) {
                throw new BizException(TalentErrorCode.OKR_ALIGNMENT_CROSS_PERIOD);
            }
        }
        long id = IdWorker.getId();
        okrMapper.insertObjective(id, tenantId, request.periodId(), userId, request.orgId(), request.objLevel(),
                request.objective(), request.parentObjectiveId(), request.visibility(), userId);
        audit.publish("TALENT_OKR_OBJECTIVE_CREATED", "CREATE", "talent_okr_objective", id, "LOW");
        return new ObjectiveCreateResult(id, "DRAFT");
    }

    @Transactional(readOnly = true)
    public List<ObjectiveView> listObjectives(Long periodId, Long orgId, String objLevel, Long ownerId,
                                              Boolean tree, Long rootObjectiveId) {
        long tenantId = TalentContext.tenantId();
        if (Boolean.TRUE.equals(tree) || rootObjectiveId != null) {
            return List.of(buildTreeRoot(tenantId, periodId, rootObjectiveId).root());
        }
        return okrMapper.listObjectives(tenantId, periodId, orgId, objLevel, ownerId, null).stream()
                .map(row -> toObjectiveView(row, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlignmentTree alignmentTree(Long periodId, Long rootObjectiveId) {
        long tenantId = TalentContext.tenantId();
        AlignmentTree tree = buildTreeRoot(tenantId, periodId, rootObjectiveId);
        return new AlignmentTree(tree.root(), tree.children());
    }

    // ---------- 关键结果 ----------

    @Transactional
    public KrCreateResult createKr(long objectiveId, KrCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (okrMapper.findObjective(tenantId, objectiveId) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (request == null || isBlank(request.title()) || isBlank(request.metricType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "KR 标题与度量类型不能为空");
        }
        if (!METRIC_TYPES.contains(request.metricType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "度量类型必须为 NUMBER/PERCENT/MILESTONE");
        }
        long id = IdWorker.getId();
        okrMapper.insertKr(id, tenantId, objectiveId, request.title(), request.metricType(),
                request.startValue() == null ? BigDecimal.ZERO : request.startValue(),
                request.targetValue() == null ? BigDecimal.ZERO : request.targetValue(),
                request.weight(), userId);
        audit.publish("TALENT_OKR_KR_CREATED", "CREATE", "talent_okr_key_result", id, "LOW");
        return new KrCreateResult(id, "NOT_STARTED");
    }

    @Transactional
    public KrProgressResult updateKrProgress(long id, KrProgressRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> kr = okrMapper.findKr(tenantId, id);
        if (kr == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "关键结果不存在");
        }
        if (request == null || request.currentValue() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "currentValue 不能为空");
        }
        BigDecimal start = Rows.dec(kr, "startValue") == null ? BigDecimal.ZERO : Rows.dec(kr, "startValue");
        BigDecimal target = Rows.dec(kr, "targetValue") == null ? BigDecimal.ZERO : Rows.dec(kr, "targetValue");
        BigDecimal current = request.currentValue();
        int progress = computeProgress(start, target, current);
        String krStatus = progress >= 100 ? "DONE" : progress == 0 ? "NOT_STARTED"
                : progress >= 50 ? "ON_TRACK" : "AT_RISK";
        int version = request.version() == null ? Rows.intVal(kr, "version") : request.version();
        int updated = okrMapper.updateKrProgress(tenantId, id, current, progress, krStatus, version, userId);
        if (updated == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "KR 已被并发修改，请刷新后重试");
        }
        long objectiveId = Rows.lngVal(kr, "objectiveId");
        int objectiveProgress = recomputeObjectiveProgress(tenantId, objectiveId, userId);
        audit.publish("TALENT_OKR_KR_PROGRESS_UPDATED", "UPDATE", "talent_okr_key_result", id, "LOW");
        return new KrProgressResult(id, progress, krStatus, objectiveProgress);
    }

    // ---------- 对齐 ----------

    @Transactional
    public AlignmentCreateResult align(AlignmentCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.childObjectiveId() == null || request.parentObjectiveId() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "子目标与父目标不能为空");
        }
        if (request.childObjectiveId().equals(request.parentObjectiveId())) {
            throw new BizException(TalentErrorCode.OKR_ALIGNMENT_CYCLE);
        }
        Map<String, Object> child = okrMapper.findObjective(tenantId, request.childObjectiveId());
        Map<String, Object> parent = okrMapper.findObjective(tenantId, request.parentObjectiveId());
        if (child == null || parent == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (Rows.lngVal(child, "periodId") != Rows.lngVal(parent, "periodId")) {
            throw new BizException(TalentErrorCode.OKR_ALIGNMENT_CROSS_PERIOD);
        }
        String alignType = isBlank(request.alignType()) ? "ALIGN" : request.alignType();
        if (!ALIGN_TYPES.contains(alignType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "对齐类型必须为 ALIGN/SUPPORT/COLLAB");
        }
        ensureNoCycle(tenantId, request.childObjectiveId(), request.parentObjectiveId());
        long id = IdWorker.getId();
        okrMapper.insertAlignment(id, tenantId, request.childObjectiveId(), request.parentObjectiveId(),
                alignType, request.weight(), userId);
        okrMapper.updateObjectiveParent(tenantId, request.childObjectiveId(), request.parentObjectiveId(), userId);
        audit.publish("TALENT_OKR_ALIGNED", "CREATE", "talent_okr_alignment", id, "LOW");
        return new AlignmentCreateResult(id, "ALIGNED");
    }

    // ---------- 复盘 ----------

    @Transactional
    public ObjectiveReviewResult review(long id, ObjectiveReviewRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (okrMapper.findObjective(tenantId, id) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "目标不存在");
        }
        if (request == null || request.finalScore() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "finalScore 不能为空");
        }
        if (request.finalScore().compareTo(BigDecimal.ZERO) < 0
                || request.finalScore().compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "finalScore 必须介于 0 与 1 之间");
        }
        okrMapper.updateObjectiveReview(tenantId, id, request.finalScore(), userId);
        audit.publish("TALENT_OKR_OBJECTIVE_REVIEWED", "UPDATE", "talent_okr_objective", id, "LOW");
        return new ObjectiveReviewResult(id, "CLOSED", request.finalScore());
    }

    // ---------- 创新提案 ----------

    @Transactional
    public ProposalCreateResult createProposal(ProposalCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.title()) || isBlank(request.content())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "标题与内容不能为空");
        }
        long id = IdWorker.getId();
        String proposalNo = "PR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + tail(id);
        okrMapper.insertProposal(id, tenantId, proposalNo, request.title(), request.category(),
                request.content(), userId, toJson(request.teamMembers()), request.expectedBenefit(),
                toJson(request.attachmentIds()), userId);
        audit.publish("TALENT_INNOVATION_SUBMITTED", "CREATE", "talent_innovation_proposal", id, "LOW");
        return new ProposalCreateResult(id, proposalNo, "REVIEWING");
    }

    @Transactional(readOnly = true)
    public PageResult<ProposalListItem> listProposals(String propStatus, String category, String keyword,
                                                      Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<ProposalListItem> records = okrMapper.listProposals(tenantId, propStatus, category, keyword,
                        pageSize, (pageNo - 1) * pageSize).stream()
                .map(row -> new ProposalListItem(Rows.lngVal(row, "id"), Rows.str(row, "proposalNo"),
                        Rows.str(row, "title"), Rows.str(row, "category"), Rows.str(row, "proposer"),
                        Rows.intVal(row, "votes"), Rows.str(row, "propStatus")))
                .toList();
        long total = okrMapper.countProposals(tenantId, propStatus, category, keyword);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional
    public ProposalReviewResult reviewProposal(long id, ProposalReviewRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> proposal = requireProposal(tenantId, id);
        if (request == null || isBlank(request.result())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "评审结果不能为空");
        }
        if (!REVIEW_RESULTS.contains(request.result())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "评审结果必须为 PASS/HOLD/REJECT");
        }
        int roundNo = (int) okrMapper.countReviews(tenantId, id) + 1;
        okrMapper.insertReview(IdWorker.getId(), tenantId, id, userId, roundNo, request.score(),
                request.comment(), request.result(), userId);
        String status = Rows.str(proposal, "propStatus");
        if ("PASS".equals(request.result())) {
            status = "SCORING";
        } else if ("REJECT".equals(request.result())) {
            status = "REJECTED";
        }
        if (!status.equals(Rows.str(proposal, "propStatus"))) {
            okrMapper.updateProposalStatus(tenantId, id, status, userId);
        }
        audit.publish("TALENT_INNOVATION_REVIEWED", "CREATE", "talent_innovation_review", id, "LOW");
        return new ProposalReviewResult(id, request.result());
    }

    @Transactional
    public ProposalVoteResult vote(long id, ProposalVoteRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> proposal = requireProposal(tenantId, id);
        if (Rows.lngVal(proposal, "proposerId") == userId) {
            throw new BizException(TalentErrorCode.PROPOSAL_SELF_VOTE_FORBIDDEN);
        }
        okrMapper.incrementVotes(tenantId, id, userId);
        audit.publish("TALENT_INNOVATION_VOTED", "UPDATE", "talent_innovation_proposal", id, "LOW");
        return new ProposalVoteResult(id, Rows.intVal(proposal, "votes") + 1);
    }

    @Transactional
    public ProposalStatusResult incubate(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> proposal = requireProposal(tenantId, id);
        String status = Rows.str(proposal, "propStatus");
        if (!Set.of("REVIEWING", "SCORING").contains(status)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅评审或评分中的提案可立项");
        }
        okrMapper.updateProposalStatus(tenantId, id, "INCUBATING", userId);
        audit.publish("TALENT_INNOVATION_INCUBATED", "UPDATE", "talent_innovation_proposal", id, "MEDIUM");
        return new ProposalStatusResult(id, "INCUBATING");
    }

    // ---------- helpers ----------

    private int recomputeObjectiveProgress(long tenantId, long objectiveId, long userId) {
        List<Map<String, Object>> krs = okrMapper.listKrsByObjective(tenantId, objectiveId);
        if (krs.isEmpty()) {
            return 0;
        }
        long weightSum = 0;
        long weighted = 0;
        long plain = 0;
        for (Map<String, Object> kr : krs) {
            int progress = Rows.intVal(kr, "progress");
            int weight = Rows.intVal(kr, "weight");
            weightSum += weight;
            weighted += (long) weight * progress;
            plain += progress;
        }
        int objectiveProgress = weightSum == 0
                ? (int) Math.round((double) plain / krs.size())
                : (int) Math.round((double) weighted / weightSum);
        okrMapper.updateObjectiveProgress(tenantId, objectiveId, objectiveProgress, userId);
        return objectiveProgress;
    }

    private int computeProgress(BigDecimal start, BigDecimal target, BigDecimal current) {
        if (target.compareTo(start) == 0) {
            return current.compareTo(target) >= 0 ? 100 : 0;
        }
        BigDecimal ratio = current.subtract(start)
                .multiply(BigDecimal.valueOf(100))
                .divide(target.subtract(start), 2, RoundingMode.HALF_UP);
        int progress = ratio.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, progress));
    }

    private void ensureNoCycle(long tenantId, long childId, long parentId) {
        Long cursor = parentId;
        for (int i = 0; i < 100 && cursor != null; i++) {
            if (cursor == childId) {
                throw new BizException(TalentErrorCode.OKR_ALIGNMENT_CYCLE);
            }
            Map<String, Object> row = okrMapper.findObjectiveParent(tenantId, cursor);
            cursor = row == null ? null : Rows.lng(row, "parentObjectiveId");
        }
    }

    private AlignmentTree buildTreeRoot(long tenantId, Long periodId, Long rootObjectiveId) {
        Map<String, Object> rootRow;
        if (rootObjectiveId != null) {
            rootRow = okrMapper.findObjective(tenantId, rootObjectiveId);
        } else {
            rootRow = okrMapper.listObjectives(tenantId, periodId, null, null, null, null).stream()
                    .filter(row -> Rows.lng(row, "parentObjectiveId") == null)
                    .findFirst()
                    .orElse(null);
        }
        if (rootRow == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "OKR 目标不存在");
        }
        Map<Long, Map<String, Object>> alignments = new LinkedHashMap<>();
        for (Map<String, Object> row : okrMapper.listAlignments(tenantId, periodId)) {
            alignments.put(Rows.lngVal(row, "childObjectiveId"), row);
        }
        List<AlignmentNode> children = buildChildren(tenantId, Rows.lngVal(rootRow, "periodId"),
                Rows.lngVal(rootRow, "id"), alignments);
        return new AlignmentTree(toObjectiveView(rootRow, children.stream()
                .map(node -> node.objective()).toList()), children);
    }

    private List<AlignmentNode> buildChildren(long tenantId, long periodId, long parentId,
                                              Map<Long, Map<String, Object>> alignments) {
        List<AlignmentNode> nodes = new ArrayList<>();
        for (Map<String, Object> row : okrMapper.listObjectives(tenantId, periodId, null, null, null, parentId)) {
            long childId = Rows.lngVal(row, "id");
            List<AlignmentNode> grandchildren = buildChildren(tenantId, periodId, childId, alignments);
            Map<String, Object> alignment = alignments.get(childId);
            nodes.add(new AlignmentNode(toObjectiveView(row, grandchildren.stream()
                    .map(node -> node.objective()).toList()),
                    alignment == null ? null : Rows.str(alignment, "alignType"),
                    alignment == null ? null : Rows.intg(alignment, "weight"),
                    grandchildren));
        }
        return nodes;
    }

    private ObjectiveView toObjectiveView(Map<String, Object> row, List<ObjectiveView> children) {
        return new ObjectiveView(Rows.lngVal(row, "id"), Rows.lngVal(row, "periodId"), Rows.lng(row, "orgId"),
                Rows.lngVal(row, "ownerId"), Rows.str(row, "ownerName"), Rows.str(row, "objLevel"),
                Rows.str(row, "objective"), Rows.intg(row, "progress"), Rows.str(row, "confidence"),
                Rows.lng(row, "parentObjectiveId"), Rows.str(row, "objStatus"), Rows.dec(row, "finalScore"),
                children);
    }

    private Map<String, Object> requireProposal(long tenantId, long id) {
        Map<String, Object> proposal = okrMapper.findProposal(tenantId, id);
        if (proposal == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "创新提案不存在");
        }
        return proposal;
    }

    private String tail(long id) {
        String value = Long.toString(id);
        return value.length() <= 6 ? value : value.substring(value.length() - 6);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "JSON 字段格式非法", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
