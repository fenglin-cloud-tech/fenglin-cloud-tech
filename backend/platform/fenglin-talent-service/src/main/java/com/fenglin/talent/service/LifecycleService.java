package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.RequestContext;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.WorkflowClient;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.LifecycleMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCompleteResult;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.ProbationEvaluateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ProbationEvaluateResult;
import com.fenglin.talent.web.dto.TalentDtos.ResignationApproveRequest;
import com.fenglin.talent.web.dto.TalentDtos.ResignationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ResignationStatusResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferAfter;
import com.fenglin.talent.web.dto.TalentDtos.TransferApprovalResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.TransferCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferEffectiveResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferEmployee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 入转调离：入职建档、试用期评估、异动（工作流 + 生效回写）、离职申请与审批。
 */
@Service
public class LifecycleService {

    private static final Set<String> TRANSFER_TYPES = Set.of("TRANSFER", "PROMOTION", "ADJUST_SALARY",
            "SECONDMENT");
    private static final Set<String> RESIGN_TYPES = Set.of("VOLUNTARY", "INVOLUNTARY", "RETIRE",
            "CONTRACT_END");
    private static final Set<String> PROBATION_RESULTS = Set.of("PASSED", "EXTENDED", "FAILED");

    private final LifecycleMapper lifecycleMapper;
    private final EmployeeMapper employeeMapper;
    private final TalentAudit audit;
    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;
    private final String lifecycleProcessKey;
    private final int probationMonths;
    private final int probationExtendMaxMonths;

    public LifecycleService(LifecycleMapper lifecycleMapper, EmployeeMapper employeeMapper, TalentAudit audit,
                            WorkflowClient workflowClient, ObjectMapper objectMapper,
                            @Value("${fenglin.talent.approval-process-key.lifecycle}") String lifecycleProcessKey,
                            @Value("${fenglin.talent.probation-months:3}") int probationMonths,
                            @Value("${fenglin.talent.probation-extend-max-months:3}") int probationExtendMaxMonths) {
        this.lifecycleMapper = lifecycleMapper;
        this.employeeMapper = employeeMapper;
        this.audit = audit;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
        this.lifecycleProcessKey = lifecycleProcessKey;
        this.probationMonths = probationMonths;
        this.probationExtendMaxMonths = probationExtendMaxMonths;
    }

    // ---------- 入职 ----------

    @Transactional
    public OnboardingCreateResult createOnboarding(OnboardingCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.employeeId() == null || request.onboardDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "员工与入职日期不能为空");
        }
        requireEmployee(tenantId, request.employeeId());
        long id = IdWorker.getId();
        lifecycleMapper.insertOnboarding(id, tenantId, request.employeeId(), request.offerId(),
                request.onboardDate(), toJson(request.checklist()), request.mentorId(), userId);
        lifecycleMapper.insertProbation(IdWorker.getId(), tenantId, request.employeeId(),
                request.onboardDate().plusMonths(probationMonths), userId);
        audit.publish("TALENT_EMPLOYEE_ONBOARDING_CREATED", "CREATE", "talent_onboarding", id, "MEDIUM");
        return new OnboardingCreateResult(id, "PENDING");
    }

    @Transactional
    public OnboardingCompleteResult completeOnboarding(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> onboarding = lifecycleMapper.findOnboarding(tenantId, id);
        if (onboarding == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "入职记录不存在");
        }
        if (!"PENDING".equals(Rows.str(onboarding, "obStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "入职记录已完成");
        }
        lifecycleMapper.updateOnboardingStatus(tenantId, id, "DONE", userId);
        employeeMapper.updateStatus(tenantId, Rows.lngVal(onboarding, "employeeId"), "PROBATION", null, null,
                userId);
        audit.publish("TALENT_EMPLOYEE_ONBOARDED", "UPDATE", "talent_onboarding", id, "MEDIUM");
        return new OnboardingCompleteResult(id, "DONE", "PROBATION");
    }

    // ---------- 试用期 ----------

    @Transactional
    public ProbationEvaluateResult evaluateProbation(long id, ProbationEvaluateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> probation = lifecycleMapper.findProbation(tenantId, id);
        if (probation == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "试用期记录不存在");
        }
        if (request == null || request.result() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "试用期结论不能为空");
        }
        if (!PROBATION_RESULTS.contains(request.result())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "试用期结论必须为 PASSED/EXTENDED/FAILED");
        }
        long employeeId = Rows.lngVal(probation, "employeeId");
        LocalDate endDate = Rows.date(probation, "endDate");
        String status = request.result();
        switch (status) {
            case "PASSED" -> {
                lifecycleMapper.updateProbation(tenantId, id, endDate, request.evaluation(), "PASSED", userId);
                employeeMapper.updateStatus(tenantId, employeeId, "ACTIVE", LocalDate.now(), null, userId);
            }
            case "EXTENDED" -> {
                int months = request.extendMonths() == null ? probationExtendMaxMonths : request.extendMonths();
                if (months < 1 || months > probationExtendMaxMonths) {
                    throw new BizException(TalentErrorCode.PARAM_INVALID,
                            "试用期延长不得超过 " + probationExtendMaxMonths + " 个月");
                }
                lifecycleMapper.updateProbation(tenantId, id, endDate.plusMonths(months), request.evaluation(),
                        "EXTENDED", userId);
            }
            default -> {
                lifecycleMapper.updateProbation(tenantId, id, endDate, request.evaluation(), "FAILED", userId);
                employeeMapper.updateStatus(tenantId, employeeId, "RESIGNED", null, LocalDate.now(), userId);
            }
        }
        audit.publish("TALENT_PROBATION_EVALUATED", "UPDATE", "talent_probation", id, "MEDIUM");
        return new ProbationEvaluateResult(id, status);
    }

    // ---------- 异动 ----------

    @Transactional
    public TransferCreateResult createTransfer(TransferCreateRequest request, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.employeeId() == null || request.transferType() == null
                || request.effectiveDate() == null || request.after() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "员工、异动类型、生效日与异动后信息不能为空");
        }
        if (!TRANSFER_TYPES.contains(request.transferType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID,
                    "异动类型必须为 TRANSFER/PROMOTION/ADJUST_SALARY/SECONDMENT");
        }
        if (request.effectiveDate().isBefore(LocalDate.now())) {
            throw new BizException(TalentErrorCode.TRANSFER_EFFECTIVE_INVALID);
        }
        Map<String, Object> employee = requireEmployee(tenantId, request.employeeId());
        TransferAfter before = new TransferAfter(Rows.lng(employee, "orgId"), Rows.lng(employee, "positionId"),
                Rows.lng(employee, "gradeId"), null);
        long id = IdWorker.getId();
        lifecycleMapper.insertTransfer(id, tenantId, request.employeeId(), request.transferType(),
                toJson(before), toJson(request.after()), request.effectiveDate(), request.reason(), userId);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("transferId", id);
        formData.put("employeeId", request.employeeId());
        formData.put("transferType", request.transferType());
        formData.put("effectiveDate", request.effectiveDate().toString());
        String instanceId = workflowClient.startApproval(permissions, RequestContext.requestId(),
                lifecycleProcessKey, String.valueOf(id), "EMPLOYEE_TRANSFER",
                "员工异动审批：" + Rows.str(employee, "name"), formData);
        audit.publish("TALENT_TRANSFER_CREATED", "CREATE", "talent_transfer", id, "MEDIUM");
        return new TransferCreateResult(id, "APPROVING", instanceId);
    }

    @Transactional
    public TransferEffectiveResult createEffectiveTransfer(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> transfer = lifecycleMapper.findTransfer(tenantId, id);
        if (transfer == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "异动记录不存在");
        }
        if (!"APPROVING".equals(Rows.str(transfer, "tfStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅审批中的异动可生效");
        }
        TransferAfter after = fromJson(Rows.str(transfer, "afterJson"), TransferAfter.class);
        long employeeId = Rows.lngVal(transfer, "employeeId");
        Map<String, Object> employee = requireEmployee(tenantId, employeeId);
        Long orgId = after.orgId() == null ? Rows.lng(employee, "orgId") : after.orgId();
        Long positionId = after.positionId() == null ? Rows.lng(employee, "positionId") : after.positionId();
        Long gradeId = after.gradeId() == null ? Rows.lng(employee, "gradeId") : after.gradeId();
        employeeMapper.updateOrgPositionGrade(tenantId, employeeId, orgId, positionId, gradeId, userId);
        lifecycleMapper.updateTransferStatus(tenantId, id, "EFFECTIVE", userId);
        audit.publish("TALENT_EMPLOYEE_TRANSFERRED", "UPDATE", "talent_transfer", id, "MEDIUM");
        return new TransferEffectiveResult(id, "EFFECTIVE", new TransferEmployee(orgId, positionId, gradeId));
    }

    /**
     * 异动审批回调：通过 -> 生效并回写员工组织/岗位/职级；驳回 -> CANCELED。
     */
    @Transactional
    public TransferApprovalResult approveTransfer(long id, ApprovalRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> transfer = lifecycleMapper.findTransfer(tenantId, id);
        if (transfer == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "异动记录不存在");
        }
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "approved 不能为空");
        }
        String status = Rows.str(transfer, "tfStatus");
        long employeeId = Rows.lngVal(transfer, "employeeId");
        if ((request.approved() && "EFFECTIVE".equals(status))
                || (!request.approved() && "CANCELED".equals(status))) {
            return new TransferApprovalResult(id, status, currentEmployee(tenantId, employeeId));
        }
        if (!"APPROVING".equals(status)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅审批中的异动可处理");
        }
        if (request.approved()) {
            TransferEffectiveResult effective = createEffectiveTransfer(id);
            return new TransferApprovalResult(id, effective.tfStatus(), effective.employee());
        }
        lifecycleMapper.updateTransferStatus(tenantId, id, "CANCELED", userId);
        audit.publish("TALENT_TRANSFER_CANCELED", "APPROVE", "talent_transfer", id, "MEDIUM");
        return new TransferApprovalResult(id, "CANCELED", currentEmployee(tenantId, employeeId));
    }

    // ---------- 离职 ----------

    @Transactional
    public ResignationStatusResult createResignation(ResignationCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.employeeId() == null || request.resignType() == null
                || request.applyDate() == null || request.lastWorkDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "员工、离职类型、申请日与最后工作日不能为空");
        }
        if (!RESIGN_TYPES.contains(request.resignType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID,
                    "离职类型必须为 VOLUNTARY/INVOLUNTARY/RETIRE/CONTRACT_END");
        }
        if (request.lastWorkDate().isBefore(request.applyDate())) {
            throw new BizException(TalentErrorCode.TRANSFER_EFFECTIVE_INVALID, "最后工作日不得早于申请日");
        }
        requireEmployee(tenantId, request.employeeId());
        if (lifecycleMapper.countApprovingResignation(tenantId, request.employeeId()) > 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "该员工已有审批中的离职申请");
        }
        long id = IdWorker.getId();
        lifecycleMapper.insertResignation(id, tenantId, request.employeeId(), request.resignType(),
                request.applyDate(), request.lastWorkDate(), request.reasonCategory(), userId);
        audit.publish("TALENT_RESIGNATION_SUBMITTED", "CREATE", "talent_resignation", id, "MEDIUM");
        return new ResignationStatusResult(id, "APPROVING");
    }

    @Transactional
    public ResignationStatusResult approveResignation(long id, ResignationApproveRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> resignation = lifecycleMapper.findResignation(tenantId, id);
        if (resignation == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "离职申请不存在");
        }
        if (!"APPROVING".equals(Rows.str(resignation, "rsStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅审批中的离职申请可处理");
        }
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "approved 不能为空");
        }
        if (request.approved()) {
            lifecycleMapper.updateResignationStatus(tenantId, id, "EFFECTIVE", userId);
            employeeMapper.updateStatus(tenantId, Rows.lngVal(resignation, "employeeId"), "RESIGNED", null,
                    Rows.date(resignation, "lastWorkDate"), userId);
            audit.publish("TALENT_EMPLOYEE_RESIGNED", "UPDATE", "talent_resignation", id, "HIGH");
            return new ResignationStatusResult(id, "EFFECTIVE");
        }
        lifecycleMapper.updateResignationStatus(tenantId, id, "REVOKED", userId);
        audit.publish("TALENT_RESIGNATION_REVOKED", "UPDATE", "talent_resignation", id, "MEDIUM");
        return new ResignationStatusResult(id, "REVOKED");
    }

    private Map<String, Object> requireEmployee(long tenantId, long employeeId) {
        Map<String, Object> employee = employeeMapper.findEmployee(tenantId, employeeId);
        if (employee == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        return employee;
    }

    private TransferEmployee currentEmployee(long tenantId, long employeeId) {
        Map<String, Object> employee = requireEmployee(tenantId, employeeId);
        return new TransferEmployee(Rows.lng(employee, "orgId"), Rows.lng(employee, "positionId"),
                Rows.lng(employee, "gradeId"));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "异动/清单格式非法", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("异动快照解析失败", ex);
        }
    }
}
