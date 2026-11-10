package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.WorkflowClient;
import com.fenglin.talent.mapper.PayrollMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.MyPayslipPeriodView;
import com.fenglin.talent.web.dto.TalentDtos.MyPayslipView;
import com.fenglin.talent.web.dto.TalentDtos.PayItemCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PayItemView;
import com.fenglin.talent.web.dto.TalentDtos.PayPeriodCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PayPeriodView;
import com.fenglin.talent.web.dto.TalentDtos.PayrollRecordView;
import com.fenglin.talent.web.dto.TalentDtos.PayslipSubmitResult;
import com.fenglin.talent.web.dto.TalentDtos.PayslipView;
import org.springframework.beans.factory.annotation.Value;
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
 * 薪酬：薪资项维护 → 周期核算 → 发放单审批 → 发放。
 *
 * <p>核算规则：员工基本薪资作为 BASE 收入项，再叠加启用中的薪资项（FIXED 固定金额 /
 * PERCENT 按基本薪资比例），收入合计为应发、扣减合计为扣款，实发 = 应发 - 扣款。
 * 发放单提交后走工作流审批，审批通过才可发放。</p>
 */
@Service
public class PayrollService {

    private static final String BASE_ITEM_CODE = "BASE";

    /**
     * 薪酬金额字段级权限：未持有时所有金额返回 null，并以 amountMasked=true 标记。
     */
    private static final String AMOUNT_VIEW = "talent:payroll:amount:view";

    private final PayrollMapper payrollMapper;
    private final TalentAudit audit;
    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;
    private final String payrollProcessKey;

    public PayrollService(PayrollMapper payrollMapper, TalentAudit audit, WorkflowClient workflowClient,
                          ObjectMapper objectMapper,
                          @Value("${fenglin.talent.approval-process-key.payroll}") String payrollProcessKey) {
        this.payrollMapper = payrollMapper;
        this.audit = audit;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
        this.payrollProcessKey = payrollProcessKey;
    }

    // ---------- 薪资项 ----------

    @Transactional(readOnly = true)
    public List<PayItemView> items(String permissions) {
        boolean visible = amountVisible(permissions);
        return payrollMapper.listItems(TalentContext.tenantId()).stream()
                .map(row -> toItemView(row, visible)).toList();
    }

    @Transactional
    public PayItemView createItem(PayItemCreateRequest request, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || blank(request.itemCode()) || blank(request.itemName())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "薪资项编码与名称不能为空");
        }
        String itemType = request.itemType() == null ? "" : request.itemType().trim().toUpperCase();
        if (!"EARNING".equals(itemType) && !"DEDUCTION".equals(itemType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "薪资项类型仅支持 EARNING / DEDUCTION");
        }
        String calcType = request.calcType() == null || request.calcType().isBlank()
                ? "FIXED" : request.calcType().trim().toUpperCase();
        if (!"FIXED".equals(calcType) && !"PERCENT".equals(calcType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "核算方式仅支持 FIXED / PERCENT");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "薪资项金额不能为负");
        }
        if ("PERCENT".equals(calcType) && amount.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "比例型薪资项的 amount 需在 0~1 之间（0.1 表示 10%）");
        }
        String itemCode = request.itemCode().trim().toUpperCase();
        if (payrollMapper.findItemByCode(tenantId, itemCode) != null) {
            throw new BizException(TalentErrorCode.PAYROLL_ITEM_DUPLICATED, "薪资项编码已存在：" + itemCode);
        }
        long id = IdWorker.getId();
        payrollMapper.insertItem(id, tenantId, itemCode, request.itemName().trim(), itemType, calcType,
                scale(amount), Boolean.FALSE.equals(request.taxable()) ? 0 : 1,
                request.sortNo() == null ? 0 : request.sortNo(), "ACTIVE", request.remark(), userId);
        audit.publish("TALENT_PAY_ITEM_CREATED", "CREATE", "talent_pay_item", id, "MEDIUM");
        return toItemView(payrollMapper.findItemByCode(tenantId, itemCode), amountVisible(permissions));
    }

    // ---------- 薪资周期 ----------

    @Transactional(readOnly = true)
    public PageResult<PayPeriodView> periods(Integer page, Integer size, String permissions) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        boolean visible = amountVisible(permissions);
        List<PayPeriodView> records = payrollMapper
                .listPeriods(tenantId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(row -> toPeriodView(row, visible)).toList();
        return PageResult.of(records, payrollMapper.countPeriods(tenantId), pageNo, pageSize);
    }

    @Transactional
    public PayPeriodView createPeriod(PayPeriodCreateRequest request, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || blank(request.periodCode()) || request.startDate() == null
                || request.endDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期编码与起止日期不能为空");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "周期结束日期不能早于开始日期");
        }
        String periodCode = request.periodCode().trim();
        if (payrollMapper.findPeriodByCode(tenantId, periodCode) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "薪资周期已存在：" + periodCode);
        }
        long id = IdWorker.getId();
        payrollMapper.insertPeriod(id, tenantId, periodCode, request.startDate(), request.endDate(),
                request.payDate(), userId);
        audit.publish("TALENT_PAY_PERIOD_CREATED", "CREATE", "talent_pay_period", id, "MEDIUM");
        return toPeriodView(payrollMapper.findPeriod(tenantId, id), amountVisible(permissions));
    }

    @Transactional
    public PayPeriodView calculate(long periodId, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> period = requirePeriod(tenantId, periodId);
        String status = Rows.str(period, "periodStatus");
        if (!"DRAFT".equals(status) && !"CALCULATED".equals(status)) {
            throw new BizException(TalentErrorCode.PAYROLL_PERIOD_STATE_INVALID, "当前周期状态不可核算");
        }
        List<Map<String, Object>> employees = payrollMapper.employeesForPeriod(tenantId,
                Rows.date(period, "startDate"), Rows.date(period, "endDate"));
        if (employees.isEmpty()) {
            throw new BizException(TalentErrorCode.PAYROLL_CALCULATION_EMPTY, "周期内没有可核算的在职员工");
        }
        List<Map<String, Object>> items = payrollMapper.listItems(tenantId).stream()
                .filter(item -> "ACTIVE".equals(Rows.str(item, "itemStatus")))
                .toList();
        payrollMapper.deleteRecords(tenantId, periodId, userId);

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;
        for (Map<String, Object> employee : employees) {
            BigDecimal base = scale(Rows.dec(employee, "baseSalary"));
            BigDecimal earning = base;
            BigDecimal deduction = BigDecimal.ZERO;
            List<Map<String, Object>> lines = new ArrayList<>();
            lines.add(line(BASE_ITEM_CODE, "基本工资", "EARNING", base));
            for (Map<String, Object> item : items) {
                BigDecimal amount = "PERCENT".equals(Rows.str(item, "calcType"))
                        ? scale(base.multiply(Rows.dec(item, "amount")))
                        : scale(Rows.dec(item, "amount"));
                String itemType = Rows.str(item, "itemType");
                if ("DEDUCTION".equals(itemType)) {
                    deduction = deduction.add(amount);
                } else {
                    earning = earning.add(amount);
                }
                lines.add(line(Rows.str(item, "itemCode"), Rows.str(item, "itemName"), itemType, amount));
            }
            BigDecimal gross = scale(earning);
            BigDecimal net = scale(gross.subtract(deduction));
            payrollMapper.insertRecord(IdWorker.getId(), tenantId, periodId, Rows.lngVal(employee, "id"), base,
                    gross, deduction, gross, net, detail(base, lines), userId);
            grossTotal = grossTotal.add(gross);
            netTotal = netTotal.add(net);
        }
        if (payrollMapper.updateCalculated(tenantId, periodId, status, employees.size(), grossTotal, netTotal,
                userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "薪资周期已被并发修改，请刷新后重试");
        }
        audit.publish("TALENT_PAYROLL_CALCULATED", "UPDATE", "talent_pay_period", periodId, "HIGH");
        return toPeriodView(payrollMapper.findPeriod(tenantId, periodId), amountVisible(permissions));
    }

    @Transactional(readOnly = true)
    public PageResult<PayrollRecordView> records(long periodId, Integer page, Integer size, String permissions) {
        long tenantId = TalentContext.tenantId();
        requirePeriod(tenantId, periodId);
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        boolean visible = amountVisible(permissions);
        List<PayrollRecordView> records = payrollMapper
                .listRecords(tenantId, periodId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(row -> toRecordView(row, visible)).toList();
        return PageResult.of(records, payrollMapper.countRecords(tenantId, periodId), pageNo, pageSize);
    }

    // ---------- 员工自助薪资单 ----------

    /**
     * 本人薪资单：employeeId 只取令牌上下文，任何请求参数都不能替代；
     * 金额沿用 isAmountVisible 判断，无权限时字段不返回并标记 amountMasked。
     */
    @Transactional(readOnly = true)
    public PageResult<MyPayslipView> mySlips(Long periodId, Integer page, Integer size, String permissions) {
        long tenantId = TalentContext.tenantId();
        long employeeId = TalentContext.userId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        boolean visible = amountVisible(permissions);
        List<MyPayslipView> records = payrollMapper
                .listMySlipRecords(tenantId, employeeId, periodId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(row -> toMyPayslipView(row, visible)).toList();
        return PageResult.of(records, payrollMapper.countMySlipRecords(tenantId, employeeId, periodId),
                pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public List<MyPayslipPeriodView> mySlipPeriods() {
        long tenantId = TalentContext.tenantId();
        long employeeId = TalentContext.userId();
        return payrollMapper.listMySlipPeriods(tenantId, employeeId).stream()
                .map(row -> new MyPayslipPeriodView(Rows.lngVal(row, "id"), Rows.str(row, "periodCode"),
                        Rows.date(row, "startDate"), Rows.date(row, "endDate"), Rows.date(row, "payDate")))
                .toList();
    }

    // ---------- 发放单 ----------

    @Transactional
    public PayslipSubmitResult submitSlip(long periodId, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> period = requirePeriod(tenantId, periodId);
        if (!"CALCULATED".equals(Rows.str(period, "periodStatus"))) {
            throw new BizException(TalentErrorCode.PAYROLL_PERIOD_STATE_INVALID, "仅已核算周期可提交发放单");
        }
        if (payrollMapper.findSlipByPeriod(tenantId, periodId) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "该周期已存在发放单");
        }
        int employeeCount = Rows.intVal(period, "employeeCount");
        BigDecimal grossTotal = Rows.dec(period, "grossTotal");
        BigDecimal netTotal = Rows.dec(period, "netTotal");
        boolean visible = amountVisible(permissions);
        long id = IdWorker.getId();
        String slipNo = payrollMapper.nextSlipNo();
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("payslipId", id);
        formData.put("slipNo", slipNo);
        formData.put("periodCode", Rows.str(period, "periodCode"));
        formData.put("employeeCount", employeeCount);
        formData.put("amountMasked", !visible);
        if (visible) {
            formData.put("grossTotal", grossTotal);
            formData.put("netTotal", netTotal);
        }
        String instanceId = workflowClient.startApproval(permissions, RequestContext.requestId(), payrollProcessKey,
                String.valueOf(id), "PAYROLL_SLIP", "薪资发放单：" + Rows.str(period, "periodCode"), formData);
        payrollMapper.insertSlip(id, tenantId, periodId, slipNo, employeeCount, grossTotal, netTotal, instanceId,
                userId);
        if (payrollMapper.updatePeriodStatus(tenantId, periodId, "CALCULATED", "APPROVING", userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "薪资周期状态已变化，请刷新后重试");
        }
        audit.publish("TALENT_PAYSLIP_SUBMITTED", "CREATE", "talent_payslip", id, "HIGH");
        return new PayslipSubmitResult(id, slipNo, "APPROVING", periodId, instanceId, employeeCount,
                visible ? netTotal : null, !visible);
    }

    @Transactional
    public PayslipView approve(long slipId, ApprovalRequest request, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "审批结论不能为空");
        }
        Map<String, Object> slip = requireSlip(tenantId, slipId);
        if (!"APPROVING".equals(Rows.str(slip, "slipStatus"))) {
            throw new BizException(TalentErrorCode.PAYROLL_SLIP_STATE_INVALID, "仅审批中的发放单可办理");
        }
        long periodId = Rows.lngVal(slip, "periodId");
        if (Boolean.TRUE.equals(request.approved())) {
            if (payrollMapper.updateSlipApproval(tenantId, slipId, "APPROVED", request.opinion(), userId) == 0) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "发放单已被并发处理，请刷新后重试");
            }
            updatePeriod(tenantId, periodId, "APPROVING", "APPROVED", userId);
        } else {
            if (payrollMapper.updateSlipApproval(tenantId, slipId, "VOID", request.opinion(), userId) == 0) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "发放单已被并发处理，请刷新后重试");
            }
            updatePeriod(tenantId, periodId, "APPROVING", "CALCULATED", userId);
        }
        audit.publish("TALENT_PAYSLIP_APPROVED", "UPDATE", "talent_payslip", slipId, "HIGH");
        return toSlipView(payrollMapper.findSlip(tenantId, slipId), amountVisible(permissions));
    }

    @Transactional
    public PayslipView pay(long slipId, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> slip = requireSlip(tenantId, slipId);
        if (!"APPROVED".equals(Rows.str(slip, "slipStatus"))) {
            throw new BizException(TalentErrorCode.PAYROLL_SLIP_STATE_INVALID, "仅审批通过的发放单可发放");
        }
        if (payrollMapper.markPaid(tenantId, slipId, userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "发放单状态已变化，请刷新后重试");
        }
        updatePeriod(tenantId, Rows.lngVal(slip, "periodId"), "APPROVED", "CLOSED", userId);
        audit.publish("TALENT_PAYSLIP_PAID", "UPDATE", "talent_payslip", slipId, "HIGH");
        return toSlipView(payrollMapper.findSlip(tenantId, slipId), amountVisible(permissions));
    }

    @Transactional(readOnly = true)
    public PageResult<PayslipView> slips(Integer page, Integer size, String permissions) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        boolean visible = amountVisible(permissions);
        List<PayslipView> records = payrollMapper
                .listSlips(tenantId, pageSize, (pageNo - 1) * pageSize)
                .stream().map(row -> toSlipView(row, visible)).toList();
        return PageResult.of(records, payrollMapper.countSlips(tenantId), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public PayslipView slip(long slipId, String permissions) {
        long tenantId = TalentContext.tenantId();
        return toSlipView(requireSlip(tenantId, slipId), amountVisible(permissions));
    }

    // ---------- 内部 ----------

    private void updatePeriod(long tenantId, long periodId, String expected, String status, long userId) {
        if (payrollMapper.updatePeriodStatus(tenantId, periodId, expected, status, userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "薪资周期状态已变化，请刷新后重试");
        }
    }

    private Map<String, Object> requirePeriod(long tenantId, long id) {
        Map<String, Object> row = payrollMapper.findPeriod(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "薪资周期不存在");
        }
        return row;
    }

    private Map<String, Object> requireSlip(long tenantId, long id) {
        Map<String, Object> row = payrollMapper.findSlip(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "发放单不存在");
        }
        return row;
    }

    private Map<String, Object> line(String code, String name, String type, BigDecimal amount) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("code", code);
        line.put("name", name);
        line.put("type", type);
        line.put("amount", amount);
        return line;
    }

    private String detail(BigDecimal base, List<Map<String, Object>> lines) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("base", base);
        detail.put("items", lines);
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "核算明细序列化失败");
        }
    }

    private boolean amountVisible(String permissions) {
        return PermissionGuard.has(permissions, AMOUNT_VIEW);
    }

    private PayItemView toItemView(Map<String, Object> row, boolean amountVisible) {
        return new PayItemView(Rows.lngVal(row, "id"), Rows.str(row, "itemCode"), Rows.str(row, "itemName"),
                Rows.str(row, "itemType"), Rows.str(row, "calcType"),
                amountVisible ? Rows.dec(row, "amount") : null,
                Rows.intVal(row, "taxable") == 1, Rows.intVal(row, "sortNo"), Rows.str(row, "itemStatus"),
                Rows.intVal(row, "version"), !amountVisible);
    }

    private PayPeriodView toPeriodView(Map<String, Object> row, boolean amountVisible) {
        return new PayPeriodView(Rows.lngVal(row, "id"), Rows.str(row, "periodCode"),
                Rows.date(row, "startDate"), Rows.date(row, "endDate"), Rows.date(row, "payDate"),
                Rows.str(row, "periodStatus"), Rows.intVal(row, "employeeCount"),
                amountVisible ? Rows.dec(row, "grossTotal") : null,
                amountVisible ? Rows.dec(row, "netTotal") : null,
                Rows.intVal(row, "version"), !amountVisible);
    }

    private PayrollRecordView toRecordView(Map<String, Object> row, boolean amountVisible) {
        return new PayrollRecordView(Rows.lngVal(row, "id"), Rows.lngVal(row, "periodId"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"),
                amountVisible ? Rows.dec(row, "baseSalary") : null,
                amountVisible ? Rows.dec(row, "earningTotal") : null,
                amountVisible ? Rows.dec(row, "deductionTotal") : null,
                amountVisible ? Rows.dec(row, "grossPay") : null,
                amountVisible ? Rows.dec(row, "netPay") : null,
                Rows.str(row, "recStatus"), !amountVisible);
    }

    private MyPayslipView toMyPayslipView(Map<String, Object> row, boolean amountVisible) {
        return new MyPayslipView(Rows.lngVal(row, "id"), Rows.lngVal(row, "periodId"),
                Rows.str(row, "periodCode"), Rows.date(row, "payDate"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"),
                amountVisible ? Rows.dec(row, "baseSalary") : null,
                amountVisible ? Rows.dec(row, "earningTotal") : null,
                amountVisible ? Rows.dec(row, "deductionTotal") : null,
                amountVisible ? Rows.dec(row, "grossPay") : null,
                amountVisible ? Rows.dec(row, "netPay") : null,
                Rows.str(row, "recStatus"), !amountVisible);
    }

    private PayslipView toSlipView(Map<String, Object> row, boolean amountVisible) {
        return new PayslipView(Rows.lngVal(row, "id"), Rows.str(row, "slipNo"), Rows.lngVal(row, "periodId"),
                Rows.str(row, "periodCode"), Rows.intVal(row, "employeeCount"),
                amountVisible ? Rows.dec(row, "grossTotal") : null,
                amountVisible ? Rows.dec(row, "netTotal") : null,
                Rows.str(row, "slipStatus"), Rows.str(row, "processInstanceId"),
                Rows.str(row, "approveOpinion"), Rows.instant(row, "createdAt"), !amountVisible);
    }

    private BigDecimal scale(BigDecimal value) {
        BigDecimal target = value == null ? BigDecimal.ZERO : value;
        return target.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
