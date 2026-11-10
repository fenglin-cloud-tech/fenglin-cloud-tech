package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.core.RequestContext;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.WorkflowClient;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.LeaveMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.LeaveApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.LeaveBalanceView;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestResult;
import com.fenglin.talent.web.dto.TalentDtos.LeaveRequestView;
import com.fenglin.talent.web.dto.TalentDtos.LeaveTypeView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期：类型与年度额度按租户懒初始化，申请提交走工作流审批，通过后扣减额度。
 * 幂等：申请单 idempotency_key 唯一索引兜底，重复提交返回同一单据；额度扣减带 version 乐观锁。
 */
@Service
public class LeaveService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final LeaveMapper leaveMapper;
    private final EmployeeMapper employeeMapper;
    private final TalentAudit audit;
    private final WorkflowClient workflowClient;
    private final String leaveProcessKey;

    public LeaveService(LeaveMapper leaveMapper, EmployeeMapper employeeMapper, TalentAudit audit,
                        WorkflowClient workflowClient,
                        @Value("${fenglin.talent.approval-process-key.leave}") String leaveProcessKey) {
        this.leaveMapper = leaveMapper;
        this.employeeMapper = employeeMapper;
        this.audit = audit;
        this.workflowClient = workflowClient;
        this.leaveProcessKey = leaveProcessKey;
    }

    @Transactional
    public List<LeaveTypeView> types() {
        long tenantId = TalentContext.tenantId();
        ensureDefaults(tenantId, TalentContext.userId());
        return leaveMapper.listTypes(tenantId).stream()
                .map(row -> new LeaveTypeView(Rows.lngVal(row, "id"), Rows.str(row, "typeCode"),
                        Rows.str(row, "typeName"), Rows.dec(row, "annualQuotaDays"),
                        Rows.intVal(row, "paid") == 1))
                .toList();
    }

    @Transactional
    public List<LeaveBalanceView> balances(Long employeeId, Integer year) {
        long tenantId = TalentContext.tenantId();
        long targetEmployee = employeeId == null ? TalentContext.userId() : employeeId;
        if (employeeId != null && employeeMapper.findEmployee(tenantId, employeeId) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        int balanceYear = year == null ? java.time.LocalDate.now().getYear() : year;
        ensureDefaults(tenantId, TalentContext.userId());
        return leaveMapper.listTypes(tenantId).stream()
                .map(type -> balanceView(tenantId, targetEmployee, type, balanceYear))
                .toList();
    }

    @Transactional
    public LeaveRequestResult create(LeaveRequestCreateRequest request, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.leaveTypeId() == null || request.startDate() == null
                || request.endDate() == null || request.days() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "假期类型、起止日期与天数不能为空");
        }
        if (request.days().compareTo(ZERO) <= 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "请假天数必须大于 0");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        long employeeId = request.employeeId() == null ? userId : request.employeeId();
        if (request.employeeId() != null && employeeMapper.findEmployee(tenantId, employeeId) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        Map<String, Object> type = leaveMapper.findType(tenantId, request.leaveTypeId());
        if (type == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "假期类型不存在");
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Map<String, Object> existing = leaveMapper.findRequestByKey(tenantId, request.idempotencyKey().trim());
            if (existing != null) {
                return result(tenantId, existing);
            }
        }
        int year = request.startDate().getYear();
        Map<String, Object> balance = requireBalance(tenantId, employeeId, request.leaveTypeId(), year);
        BigDecimal available = Rows.dec(balance, "totalDays").subtract(Rows.dec(balance, "usedDays"));
        if (available.compareTo(request.days()) < 0) {
            throw new BizException(TalentErrorCode.LEAVE_BALANCE_INSUFFICIENT,
                    "假期额度不足，可用 " + available.toPlainString() + " 天");
        }

        long id = IdWorker.getId();
        String requestNo = leaveMapper.nextRequestNo();
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("leaveRequestId", id);
        formData.put("employeeId", employeeId);
        formData.put("leaveType", Rows.str(type, "typeCode"));
        formData.put("startDate", request.startDate().toString());
        formData.put("endDate", request.endDate().toString());
        formData.put("days", request.days());
        String instanceId = workflowClient.startApproval(permissions, RequestContext.requestId(), leaveProcessKey,
                String.valueOf(id), "LEAVE_REQUEST", "请假申请：" + Rows.str(type, "typeName"), formData);
        leaveMapper.insertRequest(id, tenantId, requestNo, employeeId, request.leaveTypeId(), request.startDate(),
                request.endDate(), request.days(), request.reason(), instanceId, blankToNull(request.idempotencyKey()),
                userId);
        audit.publish("TALENT_LEAVE_SUBMITTED", "CREATE", "talent_leave_request", id, "LOW");
        return new LeaveRequestResult(id, requestNo, "APPROVING", instanceId, Rows.dec(balance, "usedDays"));
    }

    @Transactional
    public LeaveRequestResult approve(long id, LeaveApprovalRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "审批结论不能为空");
        }
        Map<String, Object> current = requireRequest(tenantId, id);
        if (!"APPROVING".equals(Rows.str(current, "lrStatus"))) {
            throw new BizException(TalentErrorCode.LEAVE_STATE_INVALID, "仅审批中的请假单可办理");
        }
        BigDecimal usedDays;
        if (Boolean.TRUE.equals(request.approved())) {
            long employeeId = Rows.lngVal(current, "employeeId");
            long typeId = Rows.lngVal(current, "leaveTypeId");
            int year = Rows.date(current, "startDate").getYear();
            Map<String, Object> balance = requireBalance(tenantId, employeeId, typeId, year);
            BigDecimal total = Rows.dec(balance, "totalDays");
            usedDays = Rows.dec(balance, "usedDays").add(Rows.dec(current, "days"));
            if (usedDays.compareTo(total) > 0) {
                throw new BizException(TalentErrorCode.LEAVE_BALANCE_INSUFFICIENT, "假期额度不足，无法通过");
            }
            if (leaveMapper.updateUsed(tenantId, Rows.lngVal(balance, "id"), usedDays,
                    Rows.intVal(balance, "version"), userId) == 0) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "假期额度已被并发修改，请刷新后重试");
            }
            updateStatus(tenantId, id, "APPROVED", request.opinion(), userId);
        } else {
            updateStatus(tenantId, id, "REJECTED", request.opinion(), userId);
            usedDays = usedOf(tenantId, current);
        }
        audit.publish("TALENT_LEAVE_APPROVED", "UPDATE", "talent_leave_request", id, "MEDIUM");
        return result(tenantId, requireRequest(tenantId, id));
    }

    @Transactional
    public LeaveRequestResult cancel(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> current = requireRequest(tenantId, id);
        if (Rows.lngVal(current, "employeeId") != userId) {
            throw new BizException(TalentErrorCode.NO_PERMISSION, "只能撤销本人的请假单");
        }
        String status = Rows.str(current, "lrStatus");
        if (!"APPROVING".equals(status) && !"APPROVED".equals(status)) {
            throw new BizException(TalentErrorCode.LEAVE_STATE_INVALID, "当前状态不可撤销");
        }
        if ("APPROVED".equals(status)) {
            long employeeId = Rows.lngVal(current, "employeeId");
            long typeId = Rows.lngVal(current, "leaveTypeId");
            int year = Rows.date(current, "startDate").getYear();
            Map<String, Object> balance = leaveMapper.findBalance(tenantId, employeeId, typeId, year);
            if (balance != null) {
                BigDecimal used = Rows.dec(balance, "usedDays").subtract(Rows.dec(current, "days"));
                if (used.compareTo(ZERO) < 0) {
                    used = ZERO;
                }
                leaveMapper.updateUsed(tenantId, Rows.lngVal(balance, "id"), used,
                        Rows.intVal(balance, "version"), userId);
            }
        }
        if (leaveMapper.cancel(tenantId, id, userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "请假单状态已变化，请刷新后重试");
        }
        audit.publish("TALENT_LEAVE_CANCELED", "UPDATE", "talent_leave_request", id, "LOW");
        return result(tenantId, requireRequest(tenantId, id));
    }

    @Transactional(readOnly = true)
    public PageResult<LeaveRequestView> list(Long employeeId, String status, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        String filter = blankToNull(status);
        List<LeaveRequestView> records = leaveMapper
                .listRequests(tenantId, employeeId, filter, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, leaveMapper.countRequests(tenantId, employeeId, filter), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public PageResult<LeaveRequestView> myRequests(String status, Integer page, Integer size) {
        return list(TalentContext.userId(), status, page, size);
    }

    // ---------- 内部 ----------

    private void ensureDefaults(long tenantId, long userId) {
        if (leaveMapper.countTypes(tenantId) > 0) {
            return;
        }
        leaveMapper.insertType(IdWorker.getId(), tenantId, "ANNUAL", "年假", new BigDecimal("10.0"), 1, 10, userId);
        leaveMapper.insertType(IdWorker.getId(), tenantId, "SICK", "病假", new BigDecimal("10.0"), 1, 20, userId);
        leaveMapper.insertType(IdWorker.getId(), tenantId, "PERSONAL", "事假", new BigDecimal("5.0"), 0, 30, userId);
        leaveMapper.insertType(IdWorker.getId(), tenantId, "MARRIAGE", "婚假", new BigDecimal("3.0"), 1, 40, userId);
    }

    private Map<String, Object> requireBalance(long tenantId, long employeeId, long leaveTypeId, int year) {
        Map<String, Object> balance = leaveMapper.findBalance(tenantId, employeeId, leaveTypeId, year);
        if (balance != null) {
            return balance;
        }
        Map<String, Object> type = leaveMapper.findType(tenantId, leaveTypeId);
        if (type == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "假期类型不存在");
        }
        leaveMapper.insertBalance(IdWorker.getId(), tenantId, employeeId, leaveTypeId, year,
                Rows.dec(type, "annualQuotaDays"), TalentContext.userId());
        Map<String, Object> created = leaveMapper.findBalance(tenantId, employeeId, leaveTypeId, year);
        if (created == null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "假期额度初始化失败");
        }
        return created;
    }

    private LeaveBalanceView balanceView(long tenantId, long employeeId, Map<String, Object> type, int year) {
        Map<String, Object> balance = requireBalance(tenantId, employeeId, Rows.lngVal(type, "id"), year);
        BigDecimal total = Rows.dec(balance, "totalDays");
        BigDecimal used = Rows.dec(balance, "usedDays");
        return new LeaveBalanceView(Rows.lngVal(balance, "id"), Rows.lngVal(type, "id"),
                Rows.str(type, "typeCode"), Rows.str(type, "typeName"), year, total, used, total.subtract(used),
                Rows.intVal(balance, "version"));
    }

    private BigDecimal usedOf(long tenantId, Map<String, Object> request) {
        Map<String, Object> balance = leaveMapper.findBalance(tenantId, Rows.lngVal(request, "employeeId"),
                Rows.lngVal(request, "leaveTypeId"), Rows.date(request, "startDate").getYear());
        return balance == null ? ZERO : Rows.dec(balance, "usedDays");
    }

    private void updateStatus(long tenantId, long id, String status, String opinion, long userId) {
        if (leaveMapper.updateApproval(tenantId, id, status, opinion, userId) == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "请假单已被并发处理，请刷新后重试");
        }
    }

    private LeaveRequestResult result(long tenantId, Map<String, Object> request) {
        return new LeaveRequestResult(Rows.lngVal(request, "id"), Rows.str(request, "requestNo"),
                Rows.str(request, "lrStatus"), Rows.str(request, "processInstanceId"), usedOf(tenantId, request));
    }

    private Map<String, Object> requireRequest(long tenantId, long id) {
        Map<String, Object> row = leaveMapper.findRequest(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "请假单不存在");
        }
        return row;
    }

    private LeaveRequestView toView(Map<String, Object> row) {
        return new LeaveRequestView(Rows.lngVal(row, "id"), Rows.str(row, "requestNo"),
                Rows.lngVal(row, "employeeId"), Rows.str(row, "employeeName"), Rows.str(row, "leaveTypeName"),
                Rows.date(row, "startDate"), Rows.date(row, "endDate"), Rows.dec(row, "days"),
                Rows.str(row, "lrStatus"), Rows.str(row, "approveOpinion"), Rows.instant(row, "createdAt"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
