package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.core.RequestContext;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.WorkflowClient;
import com.fenglin.talent.mapper.AdminMapper;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.RecruitMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.support.TalentCrypto;
import com.fenglin.talent.web.dto.TalentDtos.ApplicationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ApplicationCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.CandidateCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.CandidateCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.CandidateListItem;
import com.fenglin.talent.web.dto.TalentDtos.InterviewCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.InterviewCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.InterviewEvaluateRequest;
import com.fenglin.talent.web.dto.TalentDtos.InterviewEvaluateResult;
import com.fenglin.talent.web.dto.TalentDtos.MyInterviewItem;
import com.fenglin.talent.web.dto.TalentDtos.NameRef;
import com.fenglin.talent.web.dto.TalentDtos.OfferCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OfferStatusResult;
import com.fenglin.talent.web.dto.TalentDtos.ParsedResume;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionApprovalResult;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionListItem;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionSubmitResult;
import com.fenglin.talent.web.dto.TalentDtos.ResumeParseRequest;
import com.fenglin.talent.web.dto.TalentDtos.ResumeParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招聘主链：需求（编制校验 + 审批）、候选人（PII 加密 + 内推提示）、应聘、面试、Offer（薪酬带宽校验）。
 */
@Service
public class RecruitService {

    private static final Set<String> CAND_SOURCES = Set.of("REFERRAL", "JOB_SITE", "HEADHUNTER", "OFFICIAL");
    private static final Set<String> INTERVIEW_MODES = Set.of("ONSITE", "VIDEO", "PHONE");
    private static final Set<String> INTERVIEW_RESULTS = Set.of("PASS", "HOLD", "REJECT");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:姓\\s*名|名字)\\s*[:：]?\\s*([\\u4e00-\\u9fa5·]{2,20})");
    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("(\\d{1,2}(?:\\.\\d)?)\\s*年(?:以上)?(?:相关)?(?:工作)?经验");

    private final RecruitMapper recruitMapper;
    private final AdminMapper adminMapper;
    private final EmployeeMapper employeeMapper;
    private final TalentCrypto crypto;
    private final TalentAudit audit;
    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;
    private final EmployeeBindingService employeeBindingService;
    private final String recruitProcessKey;

    public RecruitService(RecruitMapper recruitMapper, AdminMapper adminMapper, EmployeeMapper employeeMapper,
                          TalentCrypto crypto, TalentAudit audit, WorkflowClient workflowClient,
                          ObjectMapper objectMapper, EmployeeBindingService employeeBindingService,
                          @Value("${fenglin.talent.approval-process-key.recruit}") String recruitProcessKey) {
        this.recruitMapper = recruitMapper;
        this.adminMapper = adminMapper;
        this.employeeMapper = employeeMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
        this.employeeBindingService = employeeBindingService;
        this.recruitProcessKey = recruitProcessKey;
    }

    // ---------- 招聘需求 ----------

    @Transactional
    public RequisitionCreateResult createRequisition(RequisitionCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.positionId() == null || request.orgId() == null
                || request.headcount() == null || request.headcount() <= 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "岗位、组织与招聘人数（正整数）不能为空");
        }
        int planYear = request.expectDate() == null ? LocalDate.now().getYear() : request.expectDate().getYear();
        Map<String, Object> plan = adminMapper.findHeadcountPlan(tenantId, request.orgId(), request.positionId(),
                planYear);
        if (plan != null) {
            long committed = recruitMapper.sumCommittedHeadcount(tenantId, request.orgId(), request.positionId(),
                    planYear);
            long remaining = Rows.intVal(plan, "budgetHeadcount") - Rows.intVal(plan, "usedHeadcount") - committed;
            if (request.headcount() > remaining) {
                throw new BizException(TalentErrorCode.HEADCOUNT_EXCEEDED,
                        "编制余额不足，剩余 " + Math.max(remaining, 0));
            }
        }
        String urgency = request.urgency() == null ? "MID" : request.urgency();
        if (!Set.of("LOW", "MID", "HIGH").contains(urgency)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "紧急度必须为 LOW/MID/HIGH");
        }
        String reasonType = request.reasonType() == null ? "NEW" : request.reasonType();
        if (!Set.of("NEW", "REPLACE").contains(reasonType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "需求原因必须为 NEW/REPLACE");
        }
        long id = IdWorker.getId();
        String reqNo = recruitMapper.nextReqNo();
        long ownerId = request.ownerId() == null ? userId : request.ownerId();
        recruitMapper.insertRequisition(id, tenantId, reqNo, request.positionId(), request.orgId(),
                request.headcount(), request.salaryMin(), request.salaryMax(), urgency, reasonType, ownerId,
                request.expectDate(), request.description(), request.requirements(), userId);
        audit.publish("TALENT_REQUISITION_CREATED", "CREATE", "talent_recruit_requisition", id, "LOW");
        return new RequisitionCreateResult(id, reqNo, "DRAFT");
    }

    @Transactional(readOnly = true)
    public PageResult<RequisitionListItem> listRequisitions(String reqStatus, Long orgId, Long ownerId,
                                                            Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<RequisitionListItem> records = recruitMapper.listRequisitions(tenantId, blankToNull(reqStatus), orgId,
                        ownerId, pageSize, (pageNo - 1) * pageSize).stream()
                .map(row -> new RequisitionListItem(Rows.lngVal(row, "id"), Rows.str(row, "reqNo"),
                        Rows.str(row, "positionName"), Rows.str(row, "orgName"), Rows.intVal(row, "headcount"),
                        Rows.str(row, "reqStatus"), new NameRef(Rows.lng(row, "ownerId"), Rows.str(row, "ownerName")),
                        Rows.instant(row, "createdAt")))
                .toList();
        return PageResult.of(records,
                recruitMapper.countRequisitions(tenantId, blankToNull(reqStatus), orgId, ownerId),
                pageNo, pageSize);
    }

    @Transactional
    public RequisitionSubmitResult submitRequisition(long id, String permissions) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> requisition = recruitMapper.findRequisition(tenantId, id);
        if (requisition == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "招聘需求不存在");
        }
        String status = Rows.str(requisition, "reqStatus");
        if ("APPROVING".equals(status)) {
            return new RequisitionSubmitResult(id, "APPROVING", null);
        }
        if (!"DRAFT".equals(status)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "当前状态不允许提交审批");
        }
        String reqNo = Rows.str(requisition, "reqNo");
        recruitMapper.updateRequisitionStatus(tenantId, id, "APPROVING", userId);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("reqNo", reqNo);
        formData.put("headcount", Rows.intVal(requisition, "headcount"));
        String instanceId = workflowClient.startApproval(permissions, RequestContext.requestId(),
                recruitProcessKey, reqNo, "RECRUIT_REQUISITION", "招聘需求审批：" + reqNo, formData);
        audit.publish("TALENT_REQUISITION_SUBMITTED", "SUBMIT", "talent_recruit_requisition", id, "MEDIUM");
        return new RequisitionSubmitResult(id, "APPROVING", instanceId);
    }

    /**
     * 招聘需求审批回调：通过 -> RECRUITING，驳回 -> DRAFT；同状态幂等返回。
     */
    @Transactional
    public RequisitionApprovalResult approveRequisition(long id, ApprovalRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> requisition = recruitMapper.findRequisition(tenantId, id);
        if (requisition == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "招聘需求不存在");
        }
        if (request == null || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "approved 不能为空");
        }
        String status = Rows.str(requisition, "reqStatus");
        String target = request.approved() ? "RECRUITING" : "DRAFT";
        if (target.equals(status)) {
            return new RequisitionApprovalResult(id, status);
        }
        if (!"APPROVING".equals(status)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅审批中的招聘需求可处理");
        }
        recruitMapper.updateRequisitionStatus(tenantId, id, target, userId);
        audit.publish(request.approved() ? "TALENT_REQUISITION_APPROVED" : "TALENT_REQUISITION_REJECTED",
                "APPROVE", "talent_recruit_requisition", id, "MEDIUM");
        return new RequisitionApprovalResult(id, target);
    }

    // ---------- 候选人 ----------

    @Transactional
    public CandidateCreateResult createCandidate(CandidateCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "候选人姓名不能为空");
        }
        String source = request.source() == null ? "OFFICIAL" : request.source();
        if (!CAND_SOURCES.contains(source)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID,
                    "来源必须为 REFERRAL/JOB_SITE/HEADHUNTER/OFFICIAL");
        }
        String phoneHash = crypto.hash(tenantId, request.phone());
        String idCardHash = crypto.hash(tenantId, request.idCard());
        if (phoneHash != null && recruitMapper.countByCandidatePhoneHash(tenantId, phoneHash) > 0) {
            throw new BizException(TalentErrorCode.PII_DUPLICATED, "候选人手机号已存在");
        }
        boolean referralHint = phoneHash != null && employeeMapper.countActiveByPhoneHash(tenantId, phoneHash) > 0;
        long id = IdWorker.getId();
        recruitMapper.insertCandidate(id, tenantId, request.name(),
                crypto.encrypt(tenantId, "talent_candidate", "phone", request.phone()), phoneHash,
                crypto.encrypt(tenantId, "talent_candidate", "id_card", request.idCard()), idCardHash,
                request.email(), request.gender(), request.birthDate(), source, request.experienceYears(),
                request.currentCompany(), request.expectedSalary(), toJson(request.tags()), userId);
        audit.publish("TALENT_CANDIDATE_CREATED", "CREATE", "talent_candidate", id, "MEDIUM");
        return new CandidateCreateResult(id, "NEW", referralHint);
    }

    @Transactional(readOnly = true)
    public PageResult<CandidateListItem> listCandidates(Long requisitionId, String candStatus, String source,
                                                        String keyword, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        String status = blankToNull(candStatus);
        String sourceFilter = blankToNull(source);
        String keywordFilter = blankToNull(keyword);
        List<CandidateListItem> records = recruitMapper.listCandidates(tenantId, requisitionId, status, sourceFilter,
                        keywordFilter, pageSize, (pageNo - 1) * pageSize).stream()
                .map(row -> new CandidateListItem(Rows.lngVal(row, "id"), Rows.str(row, "name"),
                        Rows.str(row, "source"), Rows.str(row, "candStatus"),
                        Rows.dec(row, "experienceYears"), Rows.str(row, "currentCompany"),
                        crypto.maskPhone(crypto.decrypt(tenantId, "talent_candidate", "phone",
                                Rows.str(row, "phoneEnc"))),
                        Rows.instant(row, "createdAt")))
                .toList();
        return PageResult.of(records, recruitMapper.countCandidates(tenantId, requisitionId, status, sourceFilter,
                keywordFilter), pageNo, pageSize);
    }

    /**
     * 简历文本解析：仅回填候选人空字段，手机号加密 + 哈希落库；无法解析时返回空结果而非报错。
     */
    @Transactional
    public ResumeParseResult parseResume(long id, ResumeParseRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> candidate = recruitMapper.findCandidateProfile(tenantId, id);
        if (candidate == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        String text = request == null ? null : request.resumeText();
        String parsedName = matchFirst(NAME_PATTERN, text, 1);
        String parsedPhone = matchFirst(PHONE_PATTERN, text, 0);
        String parsedEmail = matchFirst(EMAIL_PATTERN, text, 0);
        String yearsText = matchFirst(EXPERIENCE_PATTERN, text, 1);
        BigDecimal parsedYears = yearsText == null ? null : new BigDecimal(yearsText);

        String name = isBlank(Rows.str(candidate, "name")) ? parsedName : null;
        String email = isBlank(Rows.str(candidate, "email")) ? parsedEmail : null;
        BigDecimal years = Rows.dec(candidate, "experienceYears") == null ? parsedYears : null;
        String phoneEnc = null;
        String phoneHash = null;
        if (isBlank(Rows.str(candidate, "phoneHash")) && parsedPhone != null) {
            String hash = crypto.hash(tenantId, parsedPhone);
            if (recruitMapper.countByCandidatePhoneHash(tenantId, hash) > 0) {
                throw new BizException(TalentErrorCode.PII_DUPLICATED, "候选人手机号已存在");
            }
            phoneEnc = crypto.encrypt(tenantId, "talent_candidate", "phone", parsedPhone);
            phoneHash = hash;
        }
        boolean applied = name != null || email != null || years != null || phoneHash != null;
        if (applied) {
            recruitMapper.updateCandidateParsed(tenantId, id, name, phoneEnc, phoneHash, email, years, userId);
            audit.publish("TALENT_CANDIDATE_RESUME_PARSED", "UPDATE", "talent_candidate", id, "MEDIUM");
        }
        return new ResumeParseResult(id, new ParsedResume(parsedName, parsedPhone, parsedEmail, parsedYears),
                applied);
    }

    // ---------- 应聘记录（文档缺口补齐，面试前置） ----------

    @Transactional
    public ApplicationCreateResult createApplication(ApplicationCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.candidateId() == null || request.requisitionId() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "候选人与招聘需求不能为空");
        }
        if (recruitMapper.findCandidate(tenantId, request.candidateId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        if (recruitMapper.findRequisition(tenantId, request.requisitionId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "招聘需求不存在");
        }
        String stage = request.stage() == null ? "SCREENING" : request.stage();
        long id = IdWorker.getId();
        recruitMapper.insertApplication(id, tenantId, request.candidateId(), request.requisitionId(), stage, userId);
        audit.publish("TALENT_APPLICATION_CREATED", "CREATE", "talent_application", id, "LOW");
        return new ApplicationCreateResult(id, "IN_PROGRESS");
    }

    // ---------- 面试 ----------

    @Transactional
    public InterviewCreateResult createInterview(InterviewCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.applicationId() == null || request.interviewerId() == null
                || request.roundNo() == null || request.scheduledAt() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "应聘记录、轮次、面试官与面试时间不能为空");
        }
        Map<String, Object> application = recruitMapper.findApplication(tenantId, request.applicationId());
        if (application == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "应聘记录不存在");
        }
        String mode = request.mode() == null ? "ONSITE" : request.mode();
        if (!INTERVIEW_MODES.contains(mode)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "面试形式必须为 ONSITE/VIDEO/PHONE");
        }
        long id = IdWorker.getId();
        recruitMapper.insertInterview(id, tenantId, request.applicationId(), request.roundNo(),
                request.interviewerId(), request.scheduledAt(),
                request.durationMin() == null ? 60 : request.durationMin(), mode, request.location(), userId);
        recruitMapper.updateApplicationStage(tenantId, request.applicationId(), "INTERVIEWING", userId);
        recruitMapper.updateCandidateStatus(tenantId, Rows.lngVal(application, "candidateId"), "INTERVIEWING",
                userId);
        audit.publish("TALENT_INTERVIEW_SCHEDULED", "CREATE", "talent_interview", id, "LOW");
        return new InterviewCreateResult(id, "PENDING");
    }

    @Transactional
    public InterviewEvaluateResult evaluateInterview(long id, InterviewEvaluateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> interview = recruitMapper.findInterview(tenantId, id);
        if (interview == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "面试不存在");
        }
        if (request == null || request.version() == null || request.result() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "评价结果与 version 不能为空");
        }
        if (!INTERVIEW_RESULTS.contains(request.result())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "面试结果必须为 PASS/HOLD/REJECT");
        }
        int updated = recruitMapper.evaluateInterview(tenantId, id, request.score(), request.evaluation(),
                request.result(), request.version(), userId);
        if (updated == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "面试评价已被并发修改，请刷新后重试");
        }
        if (!"HOLD".equals(request.result())) {
            recruitMapper.updateApplicationStatus(tenantId, Rows.lngVal(interview, "applicationId"),
                    request.result(), userId);
        }
        audit.publish("TALENT_INTERVIEW_EVALUATED", "UPDATE", "talent_interview", id, "MEDIUM");
        return new InterviewEvaluateResult(id, "DONE", request.result());
    }

    @Transactional(readOnly = true)
    public List<MyInterviewItem> myInterviews() {
        long tenantId = TalentContext.tenantId();
        Long interviewerId = employeeBindingService.currentEmployeeIdOrNull();
        if (interviewerId == null) {
            return List.of();
        }
        return recruitMapper.listMyInterviews(tenantId, interviewerId).stream()
                .map(row -> new MyInterviewItem(Rows.lngVal(row, "id"), Rows.str(row, "candidateName"),
                        Rows.str(row, "positionName"), Rows.intVal(row, "roundNo"),
                        Rows.instant(row, "scheduledAt"), Rows.str(row, "mode"), Rows.str(row, "intStatus")))
                .toList();
    }

    // ---------- Offer ----------

    @Transactional
    public OfferStatusResult createOffer(OfferCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.candidateId() == null || request.requisitionId() == null
                || request.positionId() == null || request.baseSalary() == null || request.offerDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "候选人、需求、岗位、基本薪资与 Offer 日期不能为空");
        }
        if (recruitMapper.findCandidate(tenantId, request.candidateId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        if (recruitMapper.findRequisition(tenantId, request.requisitionId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "招聘需求不存在");
        }
        if (request.gradeId() != null) {
            Map<String, Object> grade = adminMapper.findGrade(tenantId, request.gradeId());
            if (grade == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "职级不存在");
            }
            BigDecimal min = Rows.dec(grade, "bandMin");
            BigDecimal max = Rows.dec(grade, "bandMax");
            if ((min != null && request.baseSalary().compareTo(min) < 0)
                    || (max != null && request.baseSalary().compareTo(max) > 0)) {
                throw new BizException(TalentErrorCode.SALARY_OUT_OF_BAND,
                        "基本薪资 " + request.baseSalary() + " 超出职级带宽 [" + min + ", " + max + "]");
            }
        }
        long id = IdWorker.getId();
        recruitMapper.insertOffer(id, tenantId, request.candidateId(), request.requisitionId(), request.positionId(),
                request.gradeId(), request.baseSalary(), request.bonus(), request.offerDate(),
                request.expectJoinDate(), userId);
        audit.publish("TALENT_OFFER_CREATED", "CREATE", "talent_offer", id, "MEDIUM");
        return new OfferStatusResult(id, "APPROVING");
    }

    @Transactional
    public OfferStatusResult sendOffer(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> offer = requireOffer(tenantId, id);
        if (!"APPROVING".equals(Rows.str(offer, "offerStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅审批中的 Offer 可发送");
        }
        recruitMapper.updateOfferStatus(tenantId, id, "SENT", userId);
        audit.publish("TALENT_OFFER_SENT", "UPDATE", "talent_offer", id, "MEDIUM");
        return new OfferStatusResult(id, "SENT");
    }

    @Transactional
    public OfferStatusResult acceptOffer(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> offer = requireOffer(tenantId, id);
        if (!"SENT".equals(Rows.str(offer, "offerStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅已发送的 Offer 可接受");
        }
        recruitMapper.updateOfferStatus(tenantId, id, "ACCEPTED", userId);
        recruitMapper.updateCandidateStatus(tenantId, Rows.lngVal(offer, "candidateId"), "OFFER", userId);
        audit.publish("TALENT_OFFER_ACCEPTED", "UPDATE", "talent_offer", id, "MEDIUM");
        return new OfferStatusResult(id, "ACCEPTED");
    }

    private Map<String, Object> requireOffer(long tenantId, long id) {
        Map<String, Object> offer = recruitMapper.findOffer(tenantId, id);
        if (offer == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "Offer 不存在");
        }
        return offer;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "标签格式非法", ex);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String matchFirst(Pattern pattern, String text, int group) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : null;
    }
}
