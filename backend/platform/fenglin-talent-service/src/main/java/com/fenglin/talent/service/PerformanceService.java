package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.core.RequestContext;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.WorkflowClient;
import com.fenglin.talent.mapper.PerformanceMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfCycleCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfCycleView;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationSubmitResult;
import com.fenglin.talent.web.dto.TalentDtos.PerfEvaluationView;
import com.fenglin.talent.web.dto.TalentDtos.PerfGoalCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PerfGoalView;
import com.fenglin.talent.web.dto.TalentDtos.PerfMyView;
import com.fenglin.talent.web.dto.TalentDtos.PerfResultView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效：周期 → 目标制定与审批 → 评估提交审批 → 结果计算与发布。
 *
 * <p>周期状态机：DRAFT 草稿 → GOAL_SETTING 目标制定 → EVALUATING 评估中 → CLOSED 已关闭；
 * 目标需审批通过后才可开启评估；评估提交走工作流审批，审批通过后计入结果；
 * 结果按分数降序排名并换算等级与奖金系数，全部发布后才允许关闭周期。</p>
 */
@Service
public class PerformanceService {

    private final PerformanceMapper performanceMapper;
    private final TalentAudit audit;
    private final WorkflowClient workflowClient;
    private final EmployeeBindingService employeeBinding;
    private final String performanceProcessKey;

    public PerformanceService(PerformanceMapper performanceMapper, TalentAudit audit,
                              WorkflowClient workflowClient, EmployeeBindingService employeeBinding,
                              @Value("${fenglin.talent.approval-process-key.performance}") String performanceProcessKey) {
        this.performanceMapper = performanceMapper;
        this.audit = audit;
        this.workflowClient = workflowClient;
        this.employeeBinding = employeeBinding;
        this.performanceProcessKey = performanceProcessKey;
    }

    // ---------- 周期 ----------

    @Transactional(readOnly = true)
    public PageResult<PerfCycleView> cycles(Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfCycleView> records = performanceMapper
                .listCycles(tenantId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toCycleView).toList();
        return PageResult.of(records, performanceMapper.countCycles(tenantId), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public PerfCycleView cycle(long cycleId) {
        long tenantId = TalentContext.tenantId();
        return toCycleView(requireCycle(tenantId, cycleId));
    }

    @Transactional
    public PerfCycleView createCycle(PerfCycleCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || blank(request.cycleCode()) || blank(request.cycleName())
                || request.startDate() == null || request.endDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期编码、名称与起止日期不能为空");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期结束日期不能早于开始日期");
        }
        String cycleType = blank(request.cycleType()) ? "QUARTER" : request.cycleType().trim().toUpperCase();
        if (!List.of("ANNUAL", "HALF_YEAR", "QUARTER", "MONTH").contains(cycleType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期类型仅支持 ANNUAL / HALF_YEAR / QUARTER / MONTH");
        }
        String cycleCode = request.cycleCode().trim();
        if (performanceMapper.findCycleByCode(tenantId, cycleCode) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "绩效周期已存在：" + cycleCode);
        }
        long id = IdWorker.getId();
        performanceMapper.insertCycle(id, tenantId, cycleCode, request.cycleName().trim(), cycleType,
                request.startDate(), request.endDate(), userId);
        audit.publish("TALENT_PERF_CYCLE_CREATED", "CREATE", "talent_perf_cycle", id, "MEDIUM");
        return toCycleView(performanceMapper.findCycle(tenantId, id));
    }

    @Transactional
    public PerfCycleView openGoals(long cycleId) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireCycle(tenantId, cycleId);
        if (performanceMapper.updateCycleStatus(tenantId, cycleId, "DRAFT", "GOAL_SETTING", userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_CYCLE_STATE_INVALID, "仅草稿周期可开启目标制定");
        }
        audit.publish("TALENT_PERF_GOALS_OPENED", "UPDATE", "talent_perf_cycle", cycleId, "MEDIUM");
        return toCycleView(performanceMapper.findCycle(tenantId, cycleId));
    }

    @Transactional
    public PerfCycleView startEvaluation(long cycleId) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireCycle(tenantId, cycleId);
        if (performanceMapper.countApprovedGoals(tenantId, cycleId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_GOAL_STATE_INVALID, "目标制定阶段尚无审批通过的目标");
        }
        if (performanceMapper.updateCycleStatus(tenantId, cycleId, "GOAL_SETTING", "EVALUATING", userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_CYCLE_STATE_INVALID, "仅目标制定中的周期可开启评估");
        }
        audit.publish("TALENT_PERF_EVALUATION_STARTED", "UPDATE", "talent_perf_cycle", cycleId, "MEDIUM");
        return toCycleView(performanceMapper.findCycle(tenantId, cycleId));
    }

    @Transactional
    public PerfCycleView closeCycle(long cycleId) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireCycle(tenantId, cycleId);
        if (performanceMapper.countResults(tenantId, cycleId) == 0
                || performanceMapper.countResultsByStatus(tenantId, cycleId, "CALCULATED") > 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_RESULT_NOT_READY,
                    "结果为空或存在未发布结果，周期不可关闭");
        }
        if (performanceMapper.updateCycleStatus(tenantId, cycleId, "EVALUATING", "CLOSED", userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_CYCLE_STATE_INVALID, "仅评估中的周期可关闭");
        }
        audit.publish("TALENT_PERF_CYCLE_CLOSED", "UPDATE", "talent_perf_cycle", cycleId, "HIGH");
        return toCycleView(performanceMapper.findCycle(tenantId, cycleId));
    }

    // ---------- 目标 ----------

    @Transactional(readOnly = true)
    public PageResult<PerfGoalView> goals(long cycleId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        requireCycle(tenantId, cycleId);
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfGoalView> records = performanceMapper
                .listGoals(tenantId, cycleId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toGoalView).toList();
        return PageResult.of(records, performanceMapper.countGoals(tenantId, cycleId), pageNo, pageSize);
    }

    @Transactional
    public PerfGoalView createGoal(PerfGoalCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.cycleId() == null || request.employeeId() == null
                || blank(request.goalName())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期、员工与目标名称不能为空");
        }
        Map<String, Object> cycle = requireCycle(tenantId, request.cycleId());
        String cycleStatus = Rows.str(cycle, "cycleStatus");
        if (!"DRAFT".equals(cycleStatus) && !"GOAL_SETTING".equals(cycleStatus)) {
            throw new BizException(TalentErrorCode.PERFORMANCE_GOAL_STATE_INVALID, "当前周期状态不可新增目标");
        }
        requireEmployee(tenantId, request.employeeId());
        BigDecimal weight = request.weight() == null ? BigDecimal.ZERO : request.weight();
        if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "目标权重需在 0~100 之间");
        }
        BigDecimal used = performanceMapper.sumGoalWeight(tenantId, request.cycleId(), request.employeeId());
        if (used.add(weight).compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "该员工周期内目标权重合计不能超过 100");
        }
        String measureType = blank(request.measureType()) ? "QUANT" : request.measureType().trim().toUpperCase();
        if (!"QUANT".equals(measureType) && !"QUAL".equals(measureType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "衡量方式仅支持 QUANT / QUAL");
        }
        long id = IdWorker.getId();
        performanceMapper.insertGoal(id, tenantId, request.cycleId(), request.employeeId(),
                request.goalName().trim(), request.goalDesc(), scale(weight), measureType,
                request.targetValue(), userId);
        audit.publish("TALENT_PERF_GOAL_CREATED", "CREATE", "talent_perf_goal", id, "MEDIUM");
        return toGoalView(performanceMapper.findGoal(tenantId, id));
    }

    @Transactional
    public PerfGoalView submitGoal(long goalId) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> goal = requireGoal(tenantId, goalId);
        Map<String, Object> cycle = requireCycle(tenantId, Rows.lngVal(goal, "cycleId"));
        if (!"GOAL_SETTING".equals(Rows.str(cycle, "cycleStatus"))) {
            throw new BizException(TalentErrorCode.PERFORMANCE_GOAL_STATE_INVALID, "仅目标制定中的周期可提交目标");
        }
        if (performanceMapper.updateGoalStatus(tenantId, goalId, "DRAFT", "SUBMITTED", userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_GOAL_STATE_INVALID, "仅草稿目标可提交");
        }
        audit.publish("TALENT_PERF_GOAL_SUBMITTED", "UPDATE", "talent_perf_goal", goalId, "MEDIUM");
        return toGoalView(performanceMapper.findGoal(tenantId, goalId));
    }

    @Transactional
    public PerfGoalView approveGoal(long goalId, ApprovalRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "审批结论不能为空");
        }
        requireGoal(tenantId, goalId);
        String status = Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED";
        if (performanceMapper.updateGoalApproval(tenantId, goalId, status, request.opinion(), userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_GOAL_STATE_INVALID, "仅待审批目标可办理审批");
        }
        audit.publish("TALENT_PERF_GOAL_APPROVED", "UPDATE", "talent_perf_goal", goalId, "MEDIUM");
        return toGoalView(performanceMapper.findGoal(tenantId, goalId));
    }

    // ---------- 评估 ----------

    @Transactional(readOnly = true)
    public PageResult<PerfEvaluationView> evaluations(long cycleId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        requireCycle(tenantId, cycleId);
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfEvaluationView> records = performanceMapper
                .listEvaluations(tenantId, cycleId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toEvaluationView).toList();
        return PageResult.of(records, performanceMapper.countEvaluations(tenantId, cycleId), pageNo, pageSize);
    }

    @Transactional
    public PerfEvaluationView createEvaluation(PerfEvaluationCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.cycleId() == null || request.employeeId() == null
                || request.score() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期、员工与评估分数不能为空");
        }
        Map<String, Object> cycle = requireCycle(tenantId, request.cycleId());
        if (!"EVALUATING".equals(Rows.str(cycle, "cycleStatus"))) {
            throw new BizException(TalentErrorCode.PERFORMANCE_EVALUATION_STATE_INVALID, "仅评估中的周期可录入评估");
        }
        requireEmployee(tenantId, request.employeeId());
        if (performanceMapper.findEvaluationByEmployee(tenantId, request.cycleId(), request.employeeId()) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "该员工在本周期已存在评估");
        }
        BigDecimal score = request.score();
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "评估分数需在 0~100 之间");
        }
        String reviewType = blank(request.reviewType()) ? "MANAGER" : request.reviewType().trim().toUpperCase();
        if (!List.of("SELF", "MANAGER", "CROSS").contains(reviewType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "评估类型仅支持 SELF / MANAGER / CROSS");
        }
        long evaluatorId = request.evaluatorId() == null ? userId : request.evaluatorId();
        BigDecimal normalized = score.setScale(2, RoundingMode.HALF_UP);
        long id = IdWorker.getId();
        performanceMapper.insertEvaluation(id, tenantId, request.cycleId(), request.employeeId(), evaluatorId,
                reviewType, normalized, gradeOf(normalized), request.summary(), userId);
        audit.publish("TALENT_PERF_EVALUATION_CREATED", "CREATE", "talent_perf_evaluation", id, "MEDIUM");
        return toEvaluationView(performanceMapper.findEvaluation(tenantId, id));
    }

    @Transactional
    public PerfEvaluationSubmitResult submitEvaluation(long evaluationId, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> evaluation = requireEvaluation(tenantId, evaluationId);
        if (!"DRAFT".equals(Rows.str(evaluation, "evalStatus"))) {
            throw new BizException(TalentErrorCode.PERFORMANCE_EVALUATION_STATE_INVALID, "仅草稿评估可提交审批");
        }
        long cycleId = Rows.lngVal(evaluation, "cycleId");
        Map<String, Object> cycle = requireCycle(tenantId, cycleId);
        if (!"EVALUATING".equals(Rows.str(cycle, "cycleStatus"))) {
            throw new BizException(TalentErrorCode.PERFORMANCE_EVALUATION_STATE_INVALID, "仅评估中的周期可提交评估");
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("evaluationId", evaluationId);
        formData.put("cycleCode", Rows.str(cycle, "cycleCode"));
        formData.put("employeeId", Rows.lngVal(evaluation, "employeeId"));
        formData.put("employeeName", Rows.str(evaluation, "employeeName"));
        formData.put("score", Rows.dec(evaluation, "score"));
        formData.put("grade", Rows.str(evaluation, "grade"));
        String instanceId = workflowClient.startApproval(permissions, RequestContext.requestId(),
                performanceProcessKey, String.valueOf(evaluationId), "PERF_EVALUATION",
                "绩效评估：" + Rows.str(cycle, "cycleCode") + " / " + Rows.str(evaluation, "employeeName"),
                formData);
        if (performanceMapper.markEvaluationApproving(tenantId, evaluationId, instanceId, userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "评估已被并发处理，请刷新后重试");
        }
        audit.publish("TALENT_PERF_EVALUATION_SUBMITTED", "UPDATE", "talent_perf_evaluation", evaluationId, "HIGH");
        return new PerfEvaluationSubmitResult(evaluationId, "APPROVING", cycleId, instanceId,
                Rows.dec(evaluation, "score"), Rows.str(evaluation, "grade"));
    }

    @Transactional
    public PerfEvaluationView approveEvaluation(long evaluationId, ApprovalRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "审批结论不能为空");
        }
        requireEvaluation(tenantId, evaluationId);
        String status = Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED";
        if (performanceMapper.updateEvaluationApproval(tenantId, evaluationId, status, request.opinion(),
                userId) == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_EVALUATION_STATE_INVALID, "仅审批中的评估可办理");
        }
        audit.publish("TALENT_PERF_EVALUATION_APPROVED", "UPDATE", "talent_perf_evaluation", evaluationId, "HIGH");
        return toEvaluationView(performanceMapper.findEvaluation(tenantId, evaluationId));
    }

    // ---------- 结果 ----------

    @Transactional(readOnly = true)
    public PageResult<PerfResultView> results(long cycleId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        requireCycle(tenantId, cycleId);
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfResultView> records = performanceMapper
                .listResults(tenantId, cycleId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toResultView).toList();
        return PageResult.of(records, performanceMapper.countResults(tenantId, cycleId), pageNo, pageSize);
    }

    @Transactional
    public PageResult<PerfResultView> calculateResults(long cycleId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> cycle = requireCycle(tenantId, cycleId);
        if (!"EVALUATING".equals(Rows.str(cycle, "cycleStatus"))) {
            throw new BizException(TalentErrorCode.PERFORMANCE_CYCLE_STATE_INVALID, "仅评估中的周期可计算结果");
        }
        List<Map<String, Object>> approved = performanceMapper.approvedEvaluations(tenantId, cycleId);
        if (approved.isEmpty()) {
            throw new BizException(TalentErrorCode.PERFORMANCE_RESULT_NOT_READY, "尚无审批通过的评估，结果不可计算");
        }
        performanceMapper.deleteResults(tenantId, cycleId, userId);
        BigDecimal total = BigDecimal.ZERO;
        int rank = 1;
        for (Map<String, Object> row : approved) {
            BigDecimal score = scale(Rows.dec(row, "score"));
            performanceMapper.insertResult(IdWorker.getId(), tenantId, cycleId, Rows.lngVal(row, "employeeId"),
                    score, gradeOf(score), rank++, coefficientOf(gradeOf(score)), userId);
            total = total.add(score);
        }
        BigDecimal avg = total.divide(BigDecimal.valueOf(approved.size()), 2, RoundingMode.HALF_UP);
        performanceMapper.updateCycleStats(tenantId, cycleId, approved.size(), avg, userId);
        audit.publish("TALENT_PERF_RESULT_CALCULATED", "UPDATE", "talent_perf_cycle", cycleId, "HIGH");
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfResultView> records = performanceMapper
                .listResults(tenantId, cycleId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toResultView).toList();
        return PageResult.of(records, performanceMapper.countResults(tenantId, cycleId), pageNo, pageSize);
    }

    @Transactional
    public PageResult<PerfResultView> publishResults(long cycleId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireCycle(tenantId, cycleId);
        if (performanceMapper.countResultsByStatus(tenantId, cycleId, "CALCULATED") == 0) {
            throw new BizException(TalentErrorCode.PERFORMANCE_RESULT_NOT_READY, "没有待发布的绩效结果");
        }
        performanceMapper.publishResults(tenantId, cycleId, userId);
        audit.publish("TALENT_PERF_RESULT_PUBLISHED", "UPDATE", "talent_perf_cycle", cycleId, "HIGH");
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfResultView> records = performanceMapper
                .listResults(tenantId, cycleId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toResultView).toList();
        return PageResult.of(records, performanceMapper.countResults(tenantId, cycleId), pageNo, pageSize);
    }

    // ---------- 员工自助 ----------

    @Transactional(readOnly = true)
    public PageResult<PerfCycleView> myCycles(Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        long employeeId = employeeBinding.requireCurrentEmployeeId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<PerfCycleView> records = performanceMapper
                .listCyclesByEmployee(tenantId, employeeId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toCycleView).toList();
        return PageResult.of(records, performanceMapper.countCyclesByEmployee(tenantId, employeeId),
                pageNo, pageSize);
    }

    /** 员工自助详情：未指定周期时取本人最近一个有数据的周期。 */
    @Transactional(readOnly = true)
    public PerfMyView myPerformance(Long cycleId) {
        long tenantId = TalentContext.tenantId();
        long employeeId = employeeBinding.requireCurrentEmployeeId();
        if (cycleId == null) {
            List<Map<String, Object>> latest = performanceMapper
                    .listCyclesByEmployee(tenantId, employeeId, 1, 0);
            if (latest.isEmpty()) {
                return new PerfMyView(employeeId, null, List.of(), List.of(), List.of());
            }
            cycleId = Rows.lngVal(latest.get(0), "id");
        }
        Map<String, Object> cycle = requireCycle(tenantId, cycleId);
        List<PerfGoalView> goals = performanceMapper.listGoalsByEmployee(tenantId, cycleId, employeeId)
                .stream().map(this::toGoalView).toList();
        List<PerfEvaluationView> evaluations = performanceMapper
                .listEvaluationsByEmployee(tenantId, cycleId, employeeId)
                .stream().map(this::toEvaluationView).toList();
        List<PerfResultView> results = performanceMapper
                .listPublishedResultsByEmployee(tenantId, cycleId, employeeId)
                .stream().map(this::toResultView).toList();
        return new PerfMyView(employeeId, toCycleView(cycle), goals, evaluations, results);
    }

    // ---------- 内部 ----------

    private Map<String, Object> requireCycle(long tenantId, long id) {
        Map<String, Object> row = performanceMapper.findCycle(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "绩效周期不存在");
        }
        return row;
    }

    private Map<String, Object> requireGoal(long tenantId, long id) {
        Map<String, Object> row = performanceMapper.findGoal(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "绩效目标不存在");
        }
        return row;
    }

    private Map<String, Object> requireEvaluation(long tenantId, long id) {
        Map<String, Object> row = performanceMapper.findEvaluation(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "绩效评估不存在");
        }
        return row;
    }

    private void requireEmployee(long tenantId, long employeeId) {
        if (performanceMapper.findEmployee(tenantId, employeeId) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
    }

    /** 分数换算等级：>=90 S / >=80 A / >=70 B / >=60 C / 其余 D。 */
    private String gradeOf(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) {
            return "S";
        }
        if (score.compareTo(new BigDecimal("80")) >= 0) {
            return "A";
        }
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return "B";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "C";
        }
        return "D";
    }

    private BigDecimal coefficientOf(String grade) {
        return switch (grade) {
            case "S" -> new BigDecimal("1.50");
            case "A" -> new BigDecimal("1.30");
            case "C" -> new BigDecimal("0.80");
            case "D" -> new BigDecimal("0.50");
            default -> new BigDecimal("1.00");
        };
    }

    private PerfCycleView toCycleView(Map<String, Object> row) {
        return new PerfCycleView(Rows.lngVal(row, "id"), Rows.str(row, "cycleCode"),
                Rows.str(row, "cycleName"), Rows.str(row, "cycleType"), Rows.date(row, "startDate"),
                Rows.date(row, "endDate"), Rows.str(row, "cycleStatus"), Rows.intVal(row, "evaluationCount"),
                Rows.dec(row, "avgScore"), Rows.intVal(row, "version"));
    }

    private PerfGoalView toGoalView(Map<String, Object> row) {
        return new PerfGoalView(Rows.lngVal(row, "id"), Rows.lngVal(row, "cycleId"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"), Rows.str(row, "goalName"),
                Rows.str(row, "goalDesc"), Rows.dec(row, "weight"), Rows.str(row, "measureType"),
                Rows.str(row, "targetValue"), Rows.str(row, "goalStatus"), Rows.str(row, "approveOpinion"),
                Rows.instant(row, "createdAt"));
    }

    private PerfEvaluationView toEvaluationView(Map<String, Object> row) {
        return new PerfEvaluationView(Rows.lngVal(row, "id"), Rows.lngVal(row, "cycleId"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"), Rows.lngVal(row, "evaluatorId"),
                Rows.str(row, "reviewType"), Rows.dec(row, "score"), Rows.str(row, "grade"),
                Rows.str(row, "evalStatus"), Rows.str(row, "processInstanceId"),
                Rows.str(row, "approveOpinion"), Rows.instant(row, "createdAt"));
    }

    private PerfResultView toResultView(Map<String, Object> row) {
        return new PerfResultView(Rows.lngVal(row, "id"), Rows.lngVal(row, "cycleId"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"), Rows.dec(row, "finalScore"),
                Rows.str(row, "grade"), Rows.intVal(row, "rankNo"), Rows.dec(row, "bonusCoefficient"),
                Rows.str(row, "resultStatus"), Rows.instant(row, "publishedAt"));
    }

    private BigDecimal scale(BigDecimal value) {
        BigDecimal target = value == null ? BigDecimal.ZERO : value;
        return target.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
