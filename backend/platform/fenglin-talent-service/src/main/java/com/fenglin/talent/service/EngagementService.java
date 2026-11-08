package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.EngagementMapper;
import com.fenglin.talent.mapper.OrgMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.support.TalentCrypto;
import com.fenglin.talent.web.dto.EngagementDtos.Answer;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionCreateRequest;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionCreateResult;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionUpdateRequest;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionUpdateResult;
import com.fenglin.talent.web.dto.EngagementDtos.OrgBreakdown;
import com.fenglin.talent.web.dto.EngagementDtos.RiskItem;
import com.fenglin.talent.web.dto.EngagementDtos.RiskScoreTriggerRequest;
import com.fenglin.talent.web.dto.EngagementDtos.RiskScoreTriggerResult;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyAnalysis;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyCreateRequest;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyCreateResult;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyListItem;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyPublishResult;
import com.fenglin.talent.web.dto.EngagementDtos.SurveyQuestionsResult;
import com.fenglin.talent.web.dto.EngagementDtos.SurveySubmitRequest;
import com.fenglin.talent.web.dto.EngagementDtos.SurveySubmitResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C4 敬业度与离职预测：问卷投放/答卷/分析、规则化离职风险评分、干预跟进。
 */
@Service
public class EngagementService {

    private static final Set<String> SURVEY_TYPES = Set.of("QUARTERLY", "ENPS", "PULSE");
    private static final Set<String> MEASURE_TYPES = Set.of("TALK", "SALARY", "TRANSFER", "TRAINING");
    private static final Set<String> INTERVENTION_STATUS = Set.of("OPEN", "FOLLOWING", "CLOSED");
    private static final int MIN_SAMPLE = 5;

    private final EngagementMapper engagementMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgMapper orgMapper;
    private final TalentCrypto crypto;
    private final TalentAudit audit;
    private final ObjectMapper objectMapper;

    public EngagementService(EngagementMapper engagementMapper, EmployeeMapper employeeMapper,
                             OrgMapper orgMapper, TalentCrypto crypto, TalentAudit audit,
                             ObjectMapper objectMapper) {
        this.engagementMapper = engagementMapper;
        this.employeeMapper = employeeMapper;
        this.orgMapper = orgMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ---------- 问卷 ----------

    @Transactional
    public SurveyCreateResult createSurvey(SurveyCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.title()) || isBlank(request.surveyType())
                || request.startAt() == null || request.endAt() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "标题、类型与起止时间不能为空");
        }
        if (!SURVEY_TYPES.contains(request.surveyType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "问卷类型必须为 QUARTERLY/ENPS/PULSE");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        if (request.questions() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "问卷题目不能为空");
        }
        long id = IdWorker.getId();
        String surveyNo = "SV" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + tail(id);
        boolean anonymous = request.anonymous() == null || request.anonymous();
        engagementMapper.insertSurvey(id, tenantId, surveyNo, request.title(), request.surveyType(),
                request.startAt(), request.endAt(), anonymous, toJson(request.questions()),
                toJson(request.dimensions()), toJson(request.targetScope()), userId);
        audit.publish("TALENT_SURVEY_CREATED", "CREATE", "talent_engagement_survey", id, "LOW");
        return new SurveyCreateResult(id, surveyNo, "DRAFT");
    }

    @Transactional(readOnly = true)
    public PageResult<SurveyListItem> listSurveys(Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<SurveyListItem> records = engagementMapper.listSurveys(tenantId, pageSize, (pageNo - 1) * pageSize)
                .stream()
                .map(row -> new SurveyListItem(Rows.lngVal(row, "id"), Rows.str(row, "surveyNo"),
                        Rows.str(row, "title"), Rows.str(row, "surveyType"), Rows.str(row, "surveyStatus"),
                        Rows.instant(row, "startAt"), Rows.instant(row, "endAt"),
                        Rows.intVal(row, "responseCount")))
                .toList();
        long total = engagementMapper.countSurveys(tenantId);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional
    public SurveyPublishResult publishSurvey(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> survey = requireSurvey(tenantId, id);
        if (!"DRAFT".equals(Rows.str(survey, "surveyStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅草稿问卷可发布");
        }
        engagementMapper.publishSurvey(tenantId, id, userId);
        long targetCount = engagementMapper.countTargetEmployees(tenantId);
        audit.publish("TALENT_SURVEY_PUBLISHED", "UPDATE", "talent_engagement_survey", id, "LOW");
        return new SurveyPublishResult(id, "PUBLISHED", targetCount);
    }

    @Transactional(readOnly = true)
    public SurveyQuestionsResult questions(long id) {
        long tenantId = TalentContext.tenantId();
        Map<String, Object> survey = requireSurvey(tenantId, id);
        if (!"PUBLISHED".equals(Rows.str(survey, "surveyStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "问卷尚未发布");
        }
        return new SurveyQuestionsResult(id, Rows.bool(survey, "anonymous"),
                parseJson(Rows.str(survey, "questions")), null);
    }

    // ---------- 答卷 ----------

    @Transactional
    public SurveySubmitResult submit(long surveyId, SurveySubmitRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> survey = requireSurvey(tenantId, surveyId);
        if (!"PUBLISHED".equals(Rows.str(survey, "surveyStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "问卷尚未发布");
        }
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "answers 不能为空");
        }
        long employeeId = request.employeeId() == null ? userId : request.employeeId();
        Map<String, Object> employee = employeeMapper.findEmployee(tenantId, employeeId);
        Long deptId = employee == null ? null : Rows.lng(employee, "orgId");
        boolean anonymous = Rows.bool(survey, "anonymous");
        String employeeHash = null;
        if (!anonymous) {
            employeeHash = crypto.hash(tenantId, "employee:" + employeeId);
            if (engagementMapper.countResponseByHash(tenantId, surveyId, employeeHash) > 0) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "已提交过该问卷");
            }
        }
        Map<String, String> dimensions = dimensionByQid(Rows.str(survey, "questions"));
        Map<String, BigDecimal> dimSum = new LinkedHashMap<>();
        Map<String, Integer> dimCount = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int answered = 0;
        for (Answer answer : request.answers()) {
            if (answer == null || answer.qid() == null || !(answer.value() instanceof Number number)) {
                continue;
            }
            BigDecimal value = new BigDecimal(number.toString());
            total = total.add(value);
            answered++;
            String dimension = dimensions.get(answer.qid());
            if (dimension != null) {
                dimSum.merge(dimension, value, BigDecimal::add);
                dimCount.merge(dimension, 1, Integer::sum);
            }
        }
        BigDecimal scoreTotal = answered == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(answered), 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> dimScores = new LinkedHashMap<>();
        dimSum.forEach((dimension, sum) -> dimScores.put(dimension,
                sum.divide(BigDecimal.valueOf(dimCount.get(dimension)), 2, RoundingMode.HALF_UP)));
        engagementMapper.insertResponse(IdWorker.getId(), tenantId, surveyId, employeeHash, deptId,
                toJson(request.answers()), scoreTotal, toJson(dimScores),
                OffsetDateTime.now(ZoneOffset.UTC), request.durationSec(), userId);
        engagementMapper.incrementResponseCount(tenantId, surveyId, userId);
        audit.publish("TALENT_SURVEY_SUBMITTED", "CREATE", "talent_survey_response", surveyId, "LOW");
        return new SurveySubmitResult(true);
    }

    @Transactional(readOnly = true)
    public SurveyAnalysis analysis(long surveyId) {
        long tenantId = TalentContext.tenantId();
        requireSurvey(tenantId, surveyId);
        long sample = engagementMapper.countResponses(tenantId, surveyId);
        if (sample < MIN_SAMPLE) {
            throw new BizException(TalentErrorCode.SURVEY_SAMPLE_INSUFFICIENT);
        }
        List<Map<String, Object>> responses = engagementMapper.listResponses(tenantId, surveyId);
        BigDecimal total = BigDecimal.ZERO;
        int scored = 0;
        int promoters = 0;
        int detractors = 0;
        Map<String, BigDecimal> dimSum = new LinkedHashMap<>();
        Map<String, Integer> dimCount = new LinkedHashMap<>();
        Map<Long, BigDecimal> orgSum = new LinkedHashMap<>();
        Map<Long, Integer> orgCount = new LinkedHashMap<>();
        for (Map<String, Object> response : responses) {
            BigDecimal score = Rows.dec(response, "scoreTotal");
            if (score != null) {
                total = total.add(score);
                scored++;
                if (score.compareTo(BigDecimal.valueOf(4)) >= 0) {
                    promoters++;
                } else if (score.compareTo(BigDecimal.valueOf(3)) < 0) {
                    detractors++;
                }
            }
            Map<String, Object> dims = parseObject(Rows.str(response, "dimScores"));
            for (Map.Entry<String, Object> entry : dims.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    BigDecimal value = new BigDecimal(number.toString());
                    dimSum.merge(entry.getKey(), value, BigDecimal::add);
                    dimCount.merge(entry.getKey(), 1, Integer::sum);
                }
            }
            Long deptId = Rows.lng(response, "deptId");
            if (deptId != null && score != null) {
                orgSum.merge(deptId, score, BigDecimal::add);
                orgCount.merge(deptId, 1, Integer::sum);
            }
        }
        BigDecimal overall = scored == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);
        BigDecimal enps = BigDecimal.valueOf(promoters - detractors)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(sample), 1, RoundingMode.HALF_UP);
        Map<String, BigDecimal> dimScores = new LinkedHashMap<>();
        dimSum.forEach((dimension, sum) -> dimScores.put(dimension,
                sum.divide(BigDecimal.valueOf(dimCount.get(dimension)), 2, RoundingMode.HALF_UP)));
        List<Map<String, Object>> trend = engagementMapper.listScoreTrend(tenantId, surveyId);
        List<OrgBreakdown> orgBreakdown = new ArrayList<>();
        orgSum.forEach((orgId, sum) -> orgBreakdown.add(new OrgBreakdown(orgId,
                sum.divide(BigDecimal.valueOf(orgCount.get(orgId)), 2, RoundingMode.HALF_UP),
                orgCount.get(orgId))));
        return new SurveyAnalysis(overall, enps, dimScores, trend, orgBreakdown);
    }

    // ---------- 离职风险 ----------

    @Transactional(readOnly = true)
    public PageResult<RiskItem> risks(String riskLevel, Long orgId, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<RiskItem> records = engagementMapper.listRisks(tenantId, riskLevel, orgId, pageSize,
                        (pageNo - 1) * pageSize).stream()
                .map(row -> new RiskItem(Rows.lngVal(row, "employeeId"), Rows.str(row, "name"),
                        Rows.str(row, "orgName"), Rows.intVal(row, "riskScore"), Rows.str(row, "riskLevel"),
                        Rows.intg(row, "trend"), parseJson(Rows.str(row, "factors")),
                        Rows.instant(row, "scoredAt")))
                .toList();
        long total = engagementMapper.countRisks(tenantId, riskLevel, orgId);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional
    public RiskScoreTriggerResult triggerScoring(RiskScoreTriggerRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Long orgId = request == null || request.scope() == null ? null : request.scope().orgId();
        String pathPrefix = null;
        if (orgId != null) {
            Map<String, Object> org = orgMapper.findOrg(tenantId, orgId);
            if (org == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
            }
            pathPrefix = Rows.str(org, "path");
        }
        List<Map<String, Object>> employees = engagementMapper.listEmployeesForScoring(tenantId, pathPrefix);
        OffsetDateTime scoredAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (Map<String, Object> employee : employees) {
            long employeeId = Rows.lngVal(employee, "id");
            List<Map<String, Object>> factors = new ArrayList<>();
            int score = 30;
            factors.add(factor("base", 30));
            LocalDate hireDate = Rows.date(employee, "hireDate");
            if (hireDate != null && hireDate.isBefore(LocalDate.now().minusYears(2))) {
                score += 20;
                factors.add(factor("tenure_over_2y", 20));
            }
            Map<String, Object> transfer = engagementMapper.findLatestTransfer(tenantId, employeeId);
            if (transfer != null) {
                LocalDate effectiveDate = Rows.date(transfer, "effectiveDate");
                if (effectiveDate != null && effectiveDate.isAfter(LocalDate.now().minusDays(180))) {
                    score -= 15;
                    factors.add(factor("recent_transfer", -15));
                }
                BigDecimal salary = salaryOf(Rows.str(transfer, "afterJson"));
                BigDecimal bandMin = Rows.dec(employee, "bandMin");
                BigDecimal bandMax = Rows.dec(employee, "bandMax");
                if (salary != null && bandMin != null && bandMax != null
                        && salary.compareTo(bandMin.add(bandMax).divide(BigDecimal.valueOf(2), 2,
                        RoundingMode.HALF_UP)) < 0) {
                    score += 20;
                    factors.add(factor("salary_below_band_mid", 20));
                }
            }
            Map<String, Object> engagement = engagementMapper.findLatestScoreByHash(tenantId,
                    crypto.hash(tenantId, "employee:" + employeeId));
            if (engagement != null && Rows.dec(engagement, "scoreTotal") != null
                    && Rows.dec(engagement, "scoreTotal").compareTo(BigDecimal.valueOf(3)) < 0) {
                score += 25;
                factors.add(factor("low_engagement", 25));
            }
            score = Math.max(0, Math.min(100, score));
            String level = score >= 70 ? "HIGH" : score >= 40 ? "MID" : "LOW";
            Map<String, Object> previous = engagementMapper.findLatestRisk(tenantId, employeeId);
            Integer trend = previous == null ? null : score - Rows.intVal(previous, "riskScore");
            engagementMapper.insertRisk(IdWorker.getId(), tenantId, employeeId, score, level, toJson(factors),
                    "rule-v1", scoredAt, trend, userId);
        }
        long taskId = IdWorker.getId();
        audit.publish("TALENT_ATTRITION_SCORED", "CREATE", "talent_attrition_risk", taskId, "MEDIUM");
        return new RiskScoreTriggerResult(taskId, employees.size());
    }

    @Transactional
    public InterventionCreateResult createIntervention(InterventionCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.employeeId() == null || isBlank(request.measureType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "员工与措施类型不能为空");
        }
        if (!MEASURE_TYPES.contains(request.measureType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "措施类型必须为 TALK/SALARY/TRANSFER/TRAINING");
        }
        if (employeeMapper.findEmployee(tenantId, request.employeeId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        long id = IdWorker.getId();
        engagementMapper.insertIntervention(id, tenantId, request.employeeId(), request.riskId(),
                request.measureType(), request.content(), request.ownerId() == null ? userId : request.ownerId(),
                userId);
        audit.publish("TALENT_INTERVENTION_CREATED", "CREATE", "talent_intervention", id, "MEDIUM");
        return new InterventionCreateResult(id, "OPEN");
    }

    @Transactional
    public InterventionUpdateResult updateIntervention(long id, InterventionUpdateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> intervention = engagementMapper.findIntervention(tenantId, id);
        if (intervention == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "干预记录不存在");
        }
        String status = request == null || request.status() == null
                ? Rows.str(intervention, "ivStatus") : request.status();
        if (!INTERVENTION_STATUS.contains(status)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "状态必须为 OPEN/FOLLOWING/CLOSED");
        }
        String followUp = request == null || request.followUp() == null
                ? Rows.str(intervention, "followUp") : request.followUp();
        engagementMapper.updateIntervention(tenantId, id, followUp, status, userId);
        audit.publish("TALENT_INTERVENTION_UPDATED", "UPDATE", "talent_intervention", id, "MEDIUM");
        return new InterventionUpdateResult(id, status);
    }

    // ---------- helpers ----------

    private Map<String, Object> factor(String code, int delta) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("code", code);
        factor.put("delta", delta);
        return factor;
    }

    private BigDecimal salaryOf(String afterJson) {
        Map<String, Object> after = parseObject(afterJson);
        Object salary = after.get("salary");
        return salary instanceof Number number ? new BigDecimal(number.toString()) : null;
    }

    private Map<String, String> dimensionByQid(String questionsJson) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        Object questions = parseJson(questionsJson);
        if (questions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object qid = map.get("qid");
                    Object dimension = map.get("dimension");
                    if (qid != null && dimension != null) {
                        dimensions.put(String.valueOf(qid), String.valueOf(dimension));
                    }
                }
            }
        }
        return dimensions;
    }

    private Map<String, Object> requireSurvey(long tenantId, long id) {
        Map<String, Object> survey = engagementMapper.findSurvey(tenantId, id);
        if (survey == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "问卷不存在");
        }
        return survey;
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 解析失败", ex);
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 解析失败", ex);
        }
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
