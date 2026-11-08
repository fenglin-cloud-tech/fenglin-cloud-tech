package com.fenglin.finance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.client.WorkflowClient;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.LedgerMapper;
import com.fenglin.finance.mapper.ProjectMapper;
import com.fenglin.finance.support.FinanceAudit;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.support.Rows;
import com.fenglin.finance.web.dto.FinanceDtos.CostCollectionRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CostView;
import com.fenglin.finance.web.dto.FinanceDtos.MemberCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.MemberView;
import com.fenglin.finance.web.dto.FinanceDtos.MilestoneCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.MilestoneView;
import com.fenglin.finance.web.dto.FinanceDtos.ProfitabilityView;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectApproveResult;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectDetail;
import com.fenglin.finance.web.dto.FinanceDtos.ProjectView;
import com.fenglin.finance.web.dto.FinanceDtos.RevenueRecognizeRequest;
import com.fenglin.finance.web.dto.FinanceDtos.RevenueRecognizeResult;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementView;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetApproveRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetApproveResult;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TimesheetView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D2 项目核算：立项、成员、里程碑、工时成本、收入确认、结算与盈利能力。
 *
 * <p>偏差标注：收入确认按“已发生成本 / 预计总成本”完工百分比法；文档要求同时支持里程碑法，
 * 主链先实现成本法并在结算时生成应收凭证，避免与收入确认重复时由业务侧择一使用。</p>
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private static final BigDecimal HOURS_LIMIT = BigDecimal.valueOf(24);

    private final ProjectMapper projectMapper;
    private final CommonsMapper commonsMapper;
    private final LedgerMapper ledgerMapper;
    private final VoucherService voucherService;
    private final ArapService arapService;
    private final WorkflowClient workflowClient;
    private final FinanceAudit audit;

    public ProjectService(ProjectMapper projectMapper, CommonsMapper commonsMapper, LedgerMapper ledgerMapper,
                          VoucherService voucherService, ArapService arapService,
                          WorkflowClient workflowClient, FinanceAudit audit) {
        this.projectMapper = projectMapper;
        this.commonsMapper = commonsMapper;
        this.ledgerMapper = ledgerMapper;
        this.voucherService = voucherService;
        this.arapService = arapService;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    @Transactional
    public ProjectView create(ProjectCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.projectName() == null || request.projectName().isBlank()
                || request.contractAmount() == null || request.contractAmount().signum() < 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        String projectNo = "PJ-" + String.format("%06d", commonsMapper.nextProjectNo());
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? "CNY" : request.currencyCode();
        projectMapper.insertProject(id, tenantId, projectNo, request.projectName(),
                request.projectType() == null || request.projectType().isBlank()
                        ? "PROJECT" : request.projectType(),
                request.customerId(), request.customerName(), request.contractNo(),
                FinanceContext.amount(request.contractAmount()),
                FinanceContext.amount(nullSafe(request.budgetAmount())),
                FinanceContext.amount(nullSafe(request.estimatedTotalCost())),
                request.startDate(), request.endDate(), request.managerId(), request.deptId(), currency,
                "APPROVING", request.description(), FinanceContext.userId());
        workflowClient.startApproval("biz:finance:project:create", null, "financeProjectApproval",
                projectNo, "FIN_PROJECT", "项目立项 " + request.projectName(),
                Map.of("project_id", id, "contract_amount", request.contractAmount()));
        audit.publish("fin_project.created", "CREATE", "fin_project", id, "MEDIUM");
        return view(projectMapper.findProject(tenantId, id));
    }

    @Transactional
    public ProjectApproveResult approve(long id) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, id);
        if (projectMapper.updateProjectStatus(tenantId, id, "ACTIVE", "APPROVING") == 0) {
            throw new BizException(FinanceErrorCode.PROJECT_STATE_INVALID);
        }
        String projectNo = Rows.str(projectMapper.findProject(tenantId, id), "projectNo");
        audit.publish("fin_project.approved", "APPROVE", "fin_project", id, "MEDIUM");
        return new ProjectApproveResult(id, projectNo, "ACTIVE", null);
    }

    public List<ProjectView> list(String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        List<ProjectView> views = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listProjects(tenantId, status, limit, offset)) {
            views.add(new ProjectView(Rows.lngVal(row, "id"), Rows.str(row, "projectNo"),
                    Rows.str(row, "projectName"), Rows.str(row, "projectType"), null,
                    Rows.str(row, "customerName"), null, Rows.dec(row, "contractAmount"),
                    Rows.dec(row, "budgetAmount"), null, Rows.dec(row, "actualCost"),
                    Rows.dec(row, "recognizedRevenue"), Rows.str(row, "startDate"), Rows.str(row, "endDate"),
                    Rows.lng(row, "managerId"), null, null, Rows.str(row, "status"), null));
        }
        return views;
    }

    public ProjectDetail detail(long id) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> project = requireProject(tenantId, id);
        List<MemberView> members = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listMembers(tenantId, id)) {
            members.add(new MemberView(Rows.lngVal(row, "id"), Rows.lngVal(row, "employeeId"),
                    Rows.str(row, "employeeName"), Rows.str(row, "memberRole"),
                    Rows.dec(row, "allocationPct"), Rows.dec(row, "costRate"), Rows.str(row, "joinDate"),
                    Rows.str(row, "status")));
        }
        List<MilestoneView> milestones = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listMilestones(tenantId, id)) {
            milestones.add(new MilestoneView(Rows.lngVal(row, "id"), Rows.str(row, "milestoneName"),
                    Rows.str(row, "planDate"), Rows.str(row, "actualDate"), Rows.str(row, "deliverable"),
                    Rows.dec(row, "weight"), Rows.str(row, "status")));
        }
        List<CostView> costs = costViews(tenantId, id);
        List<SettlementView> settlements = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listSettlements(tenantId, id)) {
            settlements.add(new SettlementView(Rows.lngVal(row, "id"), Rows.str(row, "settlementNo"), id,
                    Rows.dec(row, "settlementAmount"), null, null, Rows.str(row, "status"),
                    Rows.lng(row, "receivableId"), null, Rows.instant(row, "settledAt")));
        }
        List<TimesheetView> timesheets = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listTimesheets(tenantId, id, null, 50, 0)) {
            timesheets.add(timesheetView(row));
        }
        return new ProjectDetail(view(project), members, milestones, costs, settlements, timesheets);
    }

    @Transactional
    public MemberView addMember(long projectId, MemberCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        if (request.employeeId() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        LocalDate joinDate = request.joinDate() == null ? LocalDate.now() : request.joinDate();
        projectMapper.insertMember(id, tenantId, projectId, request.employeeId(), request.employeeName(),
                request.memberRole(), request.allocationPct(), request.costRate(), joinDate,
                FinanceContext.userId());
        audit.publish("fin_project_member.created", "CREATE", "fin_project_member", id, "LOW");
        return new MemberView(id, request.employeeId(), request.employeeName(), request.memberRole(),
                request.allocationPct(), request.costRate(), joinDate.toString(), "ACTIVE");
    }

    public List<MemberView> listMembers(long projectId) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        List<MemberView> views = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listMembers(tenantId, projectId)) {
            views.add(new MemberView(Rows.lngVal(row, "id"), Rows.lngVal(row, "employeeId"),
                    Rows.str(row, "employeeName"), Rows.str(row, "memberRole"),
                    Rows.dec(row, "allocationPct"), Rows.dec(row, "costRate"), Rows.str(row, "joinDate"),
                    Rows.str(row, "status")));
        }
        return views;
    }

    @Transactional
    public MilestoneView addMilestone(long projectId, MilestoneCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        if (request.milestoneName() == null || request.milestoneName().isBlank()
                || request.planDate() == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        long id = IdWorker.getId();
        projectMapper.insertMilestone(id, tenantId, projectId, request.milestoneName(), request.planDate(),
                request.deliverable(), request.weight() == null ? BigDecimal.ZERO : request.weight(),
                FinanceContext.userId());
        return new MilestoneView(id, request.milestoneName(), request.planDate().toString(), null,
                request.deliverable(), request.weight(), "PENDING");
    }

    public List<MilestoneView> listMilestones(long projectId) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        List<MilestoneView> views = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listMilestones(tenantId, projectId)) {
            views.add(new MilestoneView(Rows.lngVal(row, "id"), Rows.str(row, "milestoneName"),
                    Rows.str(row, "planDate"), Rows.str(row, "actualDate"), Rows.str(row, "deliverable"),
                    Rows.dec(row, "weight"), Rows.str(row, "status")));
        }
        return views;
    }

    @Transactional
    public TimesheetView createTimesheet(TimesheetCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.employeeId() == null || request.projectId() == null || request.workDate() == null
                || request.hours() == null || request.hours().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        requireProject(tenantId, request.projectId());
        BigDecimal existing = projectMapper.sumHours(tenantId, request.employeeId(), request.workDate());
        if (nullSafe(existing).add(request.hours()).compareTo(HOURS_LIMIT) > 0) {
            throw new BizException(FinanceErrorCode.TIMESHEET_HOURS_EXCEEDED);
        }
        Map<String, Object> member = projectMapper.findMember(tenantId, request.projectId(),
                request.employeeId());
        BigDecimal rate = member == null ? null : Rows.dec(member, "costRate");
        String role = member == null ? null : Rows.str(member, "memberRole");
        if (rate == null) {
            rate = projectMapper.findLaborRate(tenantId, request.employeeId(), role, request.workDate());
        }
        if (rate == null) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        BigDecimal cost = request.hours().multiply(rate).setScale(4, RoundingMode.HALF_UP);
        long id = IdWorker.getId();
        String timesheetNo = "TS-" + String.format("%06d", commonsMapper.nextTimesheetNo());
        projectMapper.insertTimesheet(id, tenantId, timesheetNo, request.employeeId(), request.employeeName(),
                request.projectId(), request.milestoneId(), request.workDate(),
                FinanceContext.amount(request.hours()), request.taskDesc(), FinanceContext.amount(rate),
                FinanceContext.amount(cost), FinanceContext.userId());
        audit.publish("fin_timesheet.created", "CREATE", "fin_timesheet", id, "LOW");
        return timesheetView(projectMapper.findTimesheet(tenantId, id));
    }

    public List<TimesheetView> listTimesheets(long projectId, String status, int limit, int offset) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        List<TimesheetView> views = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listTimesheets(tenantId, projectId, status, limit, offset)) {
            views.add(timesheetView(row));
        }
        return views;
    }

    @Transactional
    public TimesheetApproveResult approveTimesheets(TimesheetApproveRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.timesheetIds() == null || request.timesheetIds().isEmpty()) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        int approved = 0;
        int rejected = 0;
        BigDecimal costTotal = BigDecimal.ZERO;
        String businessKey = null;
        for (Long id : request.timesheetIds()) {
            Map<String, Object> row = projectMapper.findTimesheet(tenantId, id);
            if (row == null) {
                rejected++;
                continue;
            }
            if (projectMapper.approveTimesheet(tenantId, id, FinanceContext.userId()) == 0) {
                rejected++;
                continue;
            }
            approved++;
            long projectId = Rows.lngVal(row, "projectId");
            BigDecimal cost = nullSafe(Rows.dec(row, "costAmount"));
            costTotal = costTotal.add(cost);
            projectMapper.insertCost(IdWorker.getId(), tenantId,
                    "CT-" + String.format("%06d", commonsMapper.nextCostNo()), projectId, "LABOR", id,
                    "5101", cost, LocalDate.parse(Rows.str(row, "workDate")),
                    "工时成本 " + Rows.str(row, "timesheetNo"), FinanceContext.userId());
            projectMapper.addActualCost(tenantId, projectId, cost);
            if (businessKey == null) {
                businessKey = Rows.str(row, "timesheetNo");
            }
        }
        String instance = null;
        if (businessKey != null) {
            instance = workflowClient.startApproval("biz:finance:timesheet:approve", null,
                    "financeTimesheetApproval", businessKey, "FIN_TIMESHEET", "工时审批 " + businessKey,
                    Map.of("approved_count", approved));
        }
        audit.publish("fin_timesheet.approved", "APPROVE", "fin_timesheet", request.timesheetIds().get(0),
                "MEDIUM");
        return new TimesheetApproveResult(approved, rejected, FinanceContext.amount(costTotal), instance);
    }

    @Transactional
    public TimesheetView rejectTimesheet(long id, String reason) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> row = projectMapper.findTimesheet(tenantId, id);
        if (row == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (projectMapper.rejectTimesheet(tenantId, id, FinanceContext.userId(), reason) == 0) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        return timesheetView(projectMapper.findTimesheet(tenantId, id));
    }

    @Transactional
    public CostView collectCost(CostCollectionRequest request) {
        long tenantId = FinanceContext.tenantId();
        if (request.projectId() == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        requireProject(tenantId, request.projectId());
        long id = IdWorker.getId();
        String costNo = "CT-" + String.format("%06d", commonsMapper.nextCostNo());
        LocalDate costDate = request.costDate() == null ? LocalDate.now() : request.costDate();
        projectMapper.insertCost(id, tenantId, costNo, request.projectId(),
                request.costType() == null || request.costType().isBlank()
                        ? "OTHER" : request.costType(),
                request.sourceBizId(), request.subjectCode(), FinanceContext.amount(request.amount()),
                costDate, request.description(), FinanceContext.userId());
        projectMapper.addActualCost(tenantId, request.projectId(), FinanceContext.amount(request.amount()));
        audit.publish("fin_cost_collection.created", "CREATE", "fin_cost_collection", id, "LOW");
        return new CostView(id, costNo, request.projectId(), request.costType(), request.sourceBizId(),
                FinanceContext.amount(request.amount()), costDate.toString(), request.description());
    }

    public List<CostView> listCosts(long projectId) {
        long tenantId = FinanceContext.tenantId();
        requireProject(tenantId, projectId);
        return costViews(tenantId, projectId);
    }

    @Transactional
    public RevenueRecognizeResult recognizeRevenue(long projectId, RevenueRecognizeRequest request) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> project = requireProject(tenantId, projectId);
        if (!"ACTIVE".equals(Rows.str(project, "status"))) {
            throw new BizException(FinanceErrorCode.PROJECT_STATE_INVALID);
        }
        BigDecimal contractAmount = nullSafe(Rows.dec(project, "contractAmount"));
        BigDecimal actualCost = nullSafe(Rows.dec(project, "actualCost"));
        BigDecimal estimatedCost = nullSafe(Rows.dec(project, "estimatedTotalCost"));
        BigDecimal recognized = nullSafe(Rows.dec(project, "recognizedRevenue"));
        BigDecimal target;
        if (request != null && request.amount() != null) {
            target = request.amount().min(contractAmount);
        } else {
            if (estimatedCost.signum() <= 0) {
                throw new BizException(FinanceErrorCode.PARAM_INVALID);
            }
            target = contractAmount.multiply(actualCost)
                    .divide(estimatedCost, 4, RoundingMode.HALF_UP).min(contractAmount);
        }
        BigDecimal delta = target.subtract(recognized);
        Long voucherId = null;
        if (delta.signum() > 0) {
            long ledgerId = defaultLedgerId(tenantId);
            voucherId = voucherService.createSystem(ledgerId, LocalDate.now(),
                    "项目收入确认", "PROJECT_REVENUE", projectId, "POSTED",
                    Rows.str(project, "currencyCode"), List.of(
                            new VoucherService.EntryDraft("确认项目收入", "1122", "DEBIT",
                                    FinanceContext.amount(delta)),
                            new VoucherService.EntryDraft("确认项目收入", "6001", "CREDIT",
                                    FinanceContext.amount(delta))));
            projectMapper.updateRecognizedRevenue(tenantId, projectId, FinanceContext.amount(target));
        }
        audit.publish("fin_project.revenue_recognized", "RECOGNIZE", "fin_project", projectId, "MEDIUM");
        return new RevenueRecognizeResult(projectId, "COST_PERCENTAGE",
                FinanceContext.amount(target.max(recognized)), FinanceContext.amount(delta.max(BigDecimal.ZERO)),
                voucherId);
    }

    @Transactional
    public SettlementView createSettlement(long projectId, SettlementCreateRequest request) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> project = requireProject(tenantId, projectId);
        if (request == null || request.settlementAmount() == null || request.settlementAmount().signum() <= 0) {
            throw new BizException(FinanceErrorCode.PARAM_INVALID);
        }
        BigDecimal settled = nullSafe(projectMapper.sumSettled(tenantId, projectId));
        if (settled.add(request.settlementAmount()).compareTo(nullSafe(Rows.dec(project, "contractAmount"))) > 0) {
            throw new BizException(FinanceErrorCode.SETTLEMENT_AMOUNT_EXCEEDED);
        }
        long id = IdWorker.getId();
        String settlementNo = "ST-" + String.format("%06d", commonsMapper.nextSettlementNo());
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? Rows.str(project, "currencyCode") : request.currencyCode();
        projectMapper.insertSettlement(id, tenantId, settlementNo, projectId,
                FinanceContext.amount(request.settlementAmount()), currency, FinanceContext.userId());
        workflowClient.startApproval("biz:finance:settlement:create", null, "financeProjectSettlement",
                settlementNo, "FIN_SETTLEMENT", "项目结算 " + settlementNo,
                Map.of("settlement_id", id, "amount", request.settlementAmount()));
        audit.publish("fin_project_settlement.created", "CREATE", "fin_project_settlement", id, "MEDIUM");
        return new SettlementView(id, settlementNo, projectId,
                FinanceContext.amount(request.settlementAmount()), FinanceContext.zero(), currency,
                "APPROVING", null, null, null);
    }

    @Transactional
    public SettlementView approveSettlement(long settlementId) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> settlement = projectMapper.findSettlement(tenantId, settlementId);
        if (settlement == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"APPROVING".equals(Rows.str(settlement, "status"))) {
            throw new BizException(FinanceErrorCode.STATE_CONFLICT);
        }
        long projectId = Rows.lngVal(settlement, "projectId");
        Map<String, Object> project = requireProject(tenantId, projectId);
        BigDecimal amount = Rows.dec(settlement, "settlementAmount");
        BigDecimal settled = nullSafe(projectMapper.sumSettled(tenantId, projectId));
        if (settled.add(amount).compareTo(nullSafe(Rows.dec(project, "contractAmount"))) > 0) {
            throw new BizException(FinanceErrorCode.SETTLEMENT_AMOUNT_EXCEEDED);
        }
        long receivableId = arapService.createReceivable("SETTLEMENT", settlementId, projectId,
                Rows.str(project, "contractNo"), Rows.lng(project, "customerId"),
                Rows.str(project, "customerName"), Rows.str(settlement, "currencyCode"), amount,
                LocalDate.now(), LocalDate.now().plusDays(30));
        long ledgerId = defaultLedgerId(tenantId);
        long voucherId = voucherService.createSystem(ledgerId, LocalDate.now(), "项目结算",
                "SETTLEMENT", settlementId, "POSTED", Rows.str(settlement, "currencyCode"), List.of(
                        new VoucherService.EntryDraft("项目结算应收", "1122", "DEBIT",
                                FinanceContext.amount(amount)),
                        new VoucherService.EntryDraft("项目结算收入", "6001", "CREDIT",
                                FinanceContext.amount(amount))));
        projectMapper.settleSettlement(tenantId, settlementId, receivableId, voucherId);
        audit.publish("fin_project_settlement.settled", "SETTLE", "fin_project_settlement", settlementId,
                "HIGH");
        return settlementView(projectMapper.findSettlement(tenantId, settlementId));
    }

    public ProfitabilityView profitability(long projectId) {
        long tenantId = FinanceContext.tenantId();
        Map<String, Object> project = requireProject(tenantId, projectId);
        BigDecimal contractAmount = nullSafe(Rows.dec(project, "contractAmount"));
        BigDecimal actualCost = nullSafe(Rows.dec(project, "actualCost"));
        BigDecimal recognized = nullSafe(Rows.dec(project, "recognizedRevenue"));
        BigDecimal grossProfit = recognized.subtract(actualCost);
        BigDecimal margin = recognized.signum() == 0 ? BigDecimal.ZERO
                : grossProfit.multiply(BigDecimal.valueOf(100)).divide(recognized, 2, RoundingMode.HALF_UP);
        BigDecimal budget = nullSafe(Rows.dec(project, "budgetAmount"));
        BigDecimal usage = budget.signum() == 0 ? BigDecimal.ZERO
                : actualCost.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
        List<String> warnings = new ArrayList<>();
        if (budget.signum() > 0 && actualCost.compareTo(budget) > 0) {
            warnings.add("实际成本已超项目预算");
        }
        BigDecimal estimated = nullSafe(Rows.dec(project, "estimatedTotalCost"));
        if (estimated.signum() > 0 && actualCost.compareTo(estimated) > 0) {
            warnings.add("实际成本已超预计总成本，完工百分比可能大于 100%");
        }
        if (grossProfit.signum() < 0) {
            warnings.add("项目毛利为负");
        }
        return new ProfitabilityView(projectId, contractAmount, actualCost, recognized, grossProfit, margin,
                usage, warnings);
    }

    private List<CostView> costViews(long tenantId, long projectId) {
        List<CostView> costs = new ArrayList<>();
        for (Map<String, Object> row : projectMapper.listCosts(tenantId, projectId)) {
            costs.add(new CostView(Rows.lngVal(row, "id"), Rows.str(row, "costNo"),
                    Rows.lngVal(row, "projectId"), Rows.str(row, "costType"), Rows.lng(row, "sourceBizId"),
                    Rows.dec(row, "amount"), Rows.str(row, "costDate"), Rows.str(row, "description")));
        }
        return costs;
    }

    private SettlementView settlementView(Map<String, Object> row) {
        BigDecimal settled = Rows.dec(row, "settledAmount");
        return new SettlementView(Rows.lngVal(row, "id"), Rows.str(row, "settlementNo"),
                Rows.lngVal(row, "projectId"), Rows.dec(row, "settlementAmount"),
                settled == null ? FinanceContext.zero() : settled, Rows.str(row, "currencyCode"),
                Rows.str(row, "status"), Rows.lng(row, "receivableId"), Rows.lng(row, "voucherId"),
                Rows.instant(row, "settledAt"));
    }

    private TimesheetView timesheetView(Map<String, Object> row) {
        return new TimesheetView(Rows.lngVal(row, "id"), Rows.str(row, "timesheetNo"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"),
                Rows.lngVal(row, "projectId"), Rows.str(row, "workDate"), Rows.dec(row, "hours"),
                Rows.dec(row, "costRate"), Rows.dec(row, "costAmount"), Rows.str(row, "status"),
                Rows.str(row, "rejectReason"));
    }

    private ProjectView view(Map<String, Object> row) {
        return new ProjectView(Rows.lngVal(row, "id"), Rows.str(row, "projectNo"),
                Rows.str(row, "projectName"), Rows.str(row, "projectType"), Rows.lng(row, "customerId"),
                Rows.str(row, "customerName"), Rows.str(row, "contractNo"), Rows.dec(row, "contractAmount"),
                Rows.dec(row, "budgetAmount"), Rows.dec(row, "estimatedTotalCost"),
                Rows.dec(row, "actualCost"), Rows.dec(row, "recognizedRevenue"), Rows.str(row, "startDate"),
                Rows.str(row, "endDate"), Rows.lng(row, "managerId"), Rows.lng(row, "deptId"),
                Rows.str(row, "currencyCode"), Rows.str(row, "status"), Rows.str(row, "description"));
    }

    private Map<String, Object> requireProject(long tenantId, long id) {
        Map<String, Object> project = projectMapper.findProject(tenantId, id);
        if (project == null) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return project;
    }

    private long defaultLedgerId(long tenantId) {
        List<Map<String, Object>> ledgers = ledgerMapper.listLedgers(tenantId);
        if (ledgers.isEmpty()) {
            throw new BizException(FinanceErrorCode.RESOURCE_NOT_FOUND);
        }
        return Rows.lngVal(ledgers.get(0), "id");
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? FinanceContext.zero() : value;
    }
}
