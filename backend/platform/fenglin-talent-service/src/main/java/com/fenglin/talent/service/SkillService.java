package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.OrgMapper;
import com.fenglin.talent.mapper.SkillMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.SkillDtos.DemandCreateRequest;
import com.fenglin.talent.web.dto.SkillDtos.DemandCreateResult;
import com.fenglin.talent.web.dto.SkillDtos.DemandStatusResult;
import com.fenglin.talent.web.dto.SkillDtos.EmployeeSkillItem;
import com.fenglin.talent.web.dto.SkillDtos.EmployeeSkillUpdateRequest;
import com.fenglin.talent.web.dto.SkillDtos.EmployeeSkillUpdateResult;
import com.fenglin.talent.web.dto.SkillDtos.FlowApplicationItem;
import com.fenglin.talent.web.dto.SkillDtos.FlowApplyRequest;
import com.fenglin.talent.web.dto.SkillDtos.FlowApplyResult;
import com.fenglin.talent.web.dto.SkillDtos.FlowApproveRequest;
import com.fenglin.talent.web.dto.SkillDtos.MarketItem;
import com.fenglin.talent.web.dto.SkillDtos.MatchItem;
import com.fenglin.talent.web.dto.SkillDtos.SkillHeat;
import com.fenglin.talent.web.dto.SkillDtos.SkillInventory;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagCreateRequest;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagCreateResult;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagView;
import com.fenglin.talent.web.dto.SkillDtos.SkillUpdate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C3 技能与活水：技能标签/员工技能/盘点、人才需求、活水市场与双线审批、人岗匹配。
 */
@Service
public class SkillService {

    private static final Set<String> CATEGORIES = Set.of("TECH", "MGMT", "BIZ", "GENERAL");
    private static final Set<String> DEMAND_TYPES = Set.of("INTERNAL", "EXTERNAL");
    private static final Set<String> APPLY_TYPES = Set.of("APPLY", "TRANSFER", "SECONDMENT");

    private final SkillMapper skillMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgMapper orgMapper;
    private final TalentAudit audit;
    private final ObjectMapper objectMapper;
    private final EmployeeBindingService employeeBinding;

    public SkillService(SkillMapper skillMapper, EmployeeMapper employeeMapper, OrgMapper orgMapper,
                        TalentAudit audit, ObjectMapper objectMapper, EmployeeBindingService employeeBinding) {
        this.skillMapper = skillMapper;
        this.employeeMapper = employeeMapper;
        this.orgMapper = orgMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.employeeBinding = employeeBinding;
    }

    // ---------- 技能标签 / 员工技能 ----------

    @Transactional
    public SkillTagCreateResult createTag(SkillTagCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.name()) || isBlank(request.category())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "技能名称与分类不能为空");
        }
        if (!CATEGORIES.contains(request.category())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "技能分类必须为 TECH/MGMT/BIZ/GENERAL");
        }
        if (request.parentId() != null && skillMapper.findTag(tenantId, request.parentId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "父级技能标签不存在");
        }
        long id = IdWorker.getId();
        skillMapper.insertTag(id, tenantId, request.name(), request.category(), request.parentId(), userId);
        audit.publish("TALENT_SKILL_TAG_CREATED", "CREATE", "talent_skill_tag", id, "LOW");
        return new SkillTagCreateResult(id, "ENABLED");
    }

    @Transactional(readOnly = true)
    public List<SkillTagView> listTags() {
        long tenantId = TalentContext.tenantId();
        List<Map<String, Object>> rows = skillMapper.listTags(tenantId);
        Map<Long, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byParent.computeIfAbsent(Rows.lng(row, "parentId"), key -> new ArrayList<>()).add(row);
        }
        return buildTagTree(byParent, null);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSkillItem> employeeSkills(long employeeId) {
        long tenantId = TalentContext.tenantId();
        return skillMapper.listEmployeeSkills(tenantId, employeeId).stream()
                .map(row -> new EmployeeSkillItem(Rows.lngVal(row, "skillId"), Rows.str(row, "skillName"),
                        Rows.intg(row, "selfLevel"), Rows.intg(row, "certLevel"), Rows.dec(row, "years"),
                        Rows.date(row, "lastUsedAt"), Rows.lng(row, "verifiedBy")))
                .toList();
    }

    /** 员工自助：按登录账号绑定的员工读取本人技能，服务端收敛，不接受调用方传 id。 */
    @Transactional(readOnly = true)
    public List<EmployeeSkillItem> myEmployeeSkills() {
        return employeeSkills(employeeBinding.requireCurrentEmployeeId());
    }

    @Transactional
    public EmployeeSkillUpdateResult updateEmployeeSkills(long employeeId, EmployeeSkillUpdateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.skills() == null || request.skills().isEmpty()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "skills 不能为空");
        }
        if (employeeMapper.findEmployee(tenantId, employeeId) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        int updated = 0;
        for (SkillUpdate skill : request.skills()) {
            if (skill == null || skill.skillId() == null) {
                throw new BizException(TalentErrorCode.PARAM_INVALID, "skillId 不能为空");
            }
            int level = skill.selfLevel() == null ? 0 : skill.selfLevel();
            if (level < 0 || level > 5) {
                throw new BizException(TalentErrorCode.PARAM_INVALID, "selfLevel 必须为 0-5");
            }
            if (skillMapper.findTag(tenantId, skill.skillId()) == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "技能标签不存在");
            }
            Map<String, Object> existing = skillMapper.findEmployeeSkill(tenantId, employeeId, skill.skillId());
            if (existing == null) {
                skillMapper.insertEmployeeSkill(IdWorker.getId(), tenantId, employeeId, skill.skillId(), level,
                        skill.years(), skill.evidence(), LocalDate.now(), userId);
            } else {
                skillMapper.updateEmployeeSkill(tenantId, Rows.lngVal(existing, "id"), level, skill.years(),
                        skill.evidence(), LocalDate.now(), userId);
            }
            updated++;
        }
        audit.publish("TALENT_EMPLOYEE_SKILLS_UPDATED", "UPDATE", "talent_employee_skill", employeeId, "LOW");
        return new EmployeeSkillUpdateResult(updated);
    }

    // ---------- 技能盘点 ----------

    @Transactional(readOnly = true)
    public SkillInventory inventory(Long orgId, Long skillId) {
        long tenantId = TalentContext.tenantId();
        String pathPrefix = null;
        if (orgId != null) {
            Map<String, Object> org = orgMapper.findOrg(tenantId, orgId);
            if (org == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
            }
            pathPrefix = Rows.str(org, "path");
        }
        long total = skillMapper.countEmployees(tenantId, pathPrefix);
        long covered = skillMapper.countEmployeesWithSkill(tenantId, pathPrefix);
        BigDecimal coverage = total == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(covered)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        List<SkillHeat> heatmap = skillMapper.skillHeatmap(tenantId, pathPrefix, skillId).stream()
                .map(row -> new SkillHeat(Rows.lngVal(row, "skillId"), Rows.intg(row, "level"),
                        Rows.lngVal(row, "count")))
                .toList();
        return new SkillInventory(orgId, coverage, heatmap);
    }

    // ---------- 人才需求 ----------

    @Transactional
    public DemandCreateResult createDemand(DemandCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.orgId() == null || request.headcount() == null
                || request.headcount() <= 0 || isBlank(request.demandType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "组织、需求人数与需求类型不能为空");
        }
        if (!DEMAND_TYPES.contains(request.demandType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "需求类型必须为 INTERNAL/EXTERNAL");
        }
        if (orgMapper.findOrg(tenantId, request.orgId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
        }
        long id = IdWorker.getId();
        String demandNo = "TD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + tail(id);
        long ownerId = request.ownerId() == null ? userId : request.ownerId();
        skillMapper.insertDemand(id, tenantId, demandNo, request.orgId(), request.positionId(),
                toJson(request.skillsRequired()), request.headcount(), request.expectedDate(),
                request.demandType(), request.publishScope(), ownerId, userId);
        audit.publish("TALENT_DEMAND_CREATED", "CREATE", "talent_talent_demand", id, "LOW");
        return new DemandCreateResult(id, demandNo, "DRAFT");
    }

    @Transactional
    public DemandStatusResult publishDemand(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> demand = skillMapper.findDemand(tenantId, id);
        if (demand == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "人才需求不存在");
        }
        if (!"DRAFT".equals(Rows.str(demand, "dmStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅草稿需求可发布");
        }
        skillMapper.publishDemand(tenantId, id, userId);
        audit.publish("TALENT_DEMAND_PUBLISHED", "UPDATE", "talent_talent_demand", id, "LOW");
        return new DemandStatusResult(id, "PUBLIC");
    }

    @Transactional(readOnly = true)
    public PageResult<MarketItem> market(String keyword, Long orgId, String skills, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<MarketItem> records = skillMapper.listMarket(tenantId, keyword, orgId, skills, null, pageSize,
                        (pageNo - 1) * pageSize).stream()
                .map(row -> new MarketItem(Rows.lngVal(row, "demandId"), Rows.str(row, "demandNo"),
                        Rows.str(row, "orgName"), Rows.str(row, "positionName"), parseJson(Rows.str(row, "skills")),
                        Rows.intVal(row, "headcount"), Rows.date(row, "expectedDate"), null))
                .toList();
        long total = skillMapper.countMarket(tenantId, keyword, orgId, skills, null);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    // ---------- 活水申请 ----------

    @Transactional
    public FlowApplyResult apply(FlowApplyRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.demandId() == null || isBlank(request.applyType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "需求与申请类型不能为空");
        }
        if (!APPLY_TYPES.contains(request.applyType())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "申请类型必须为 APPLY/TRANSFER/SECONDMENT");
        }
        Map<String, Object> demand = skillMapper.findDemand(tenantId, request.demandId());
        if (demand == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "人才需求不存在");
        }
        if (!"PUBLIC".equals(Rows.str(demand, "dmStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅已发布需求可申请");
        }
        long employeeId = request.employeeId() == null ? userId : request.employeeId();
        Map<String, Object> employee = employeeMapper.findEmployee(tenantId, employeeId);
        if (employee != null && "PROBATION".equals(Rows.str(employee, "empStatus"))) {
            throw new BizException(TalentErrorCode.PROBATION_FLOW_FORBIDDEN);
        }
        if (skillMapper.countInProgress(tenantId, employeeId) > 0) {
            throw new BizException(TalentErrorCode.FLOW_IN_PROGRESS);
        }
        long id = IdWorker.getId();
        skillMapper.insertFlowApplication(id, tenantId, request.demandId(), employeeId, request.applyType(),
                request.reason(), userId);
        audit.publish("TALENT_FLOW_APPLIED", "CREATE", "talent_flow_application", id, "MEDIUM");
        return new FlowApplyResult(id, "PENDING");
    }

    @Transactional(readOnly = true)
    public PageResult<FlowApplicationItem> applications(Long employeeId, Long demandId, String flowStatus,
                                                        Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<FlowApplicationItem> records = skillMapper.listFlowApplications(tenantId, employeeId, demandId,
                        flowStatus, pageSize, (pageNo - 1) * pageSize).stream()
                .map(row -> new FlowApplicationItem(Rows.lngVal(row, "id"), Rows.str(row, "demandNo"),
                        Rows.str(row, "employeeName"), Rows.str(row, "applyType"), Rows.str(row, "flowStatus"),
                        Rows.instant(row, "createdAt")))
                .toList();
        long total = skillMapper.countFlowApplications(tenantId, employeeId, demandId, flowStatus);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional
    public FlowApplyResult approve(long id, FlowApproveRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> application = skillMapper.findFlowApplication(tenantId, id);
        if (application == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "活水申请不存在");
        }
        String current = Rows.str(application, "flowStatus");
        if (!Set.of("PENDING", "CUR_MGR_AGREE", "TARGET_INTERVIEW", "HR_REVIEW").contains(current)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "当前活水申请不可审批");
        }
        if (request == null || isBlank(request.node()) || request.approved() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "审批节点与审批结论不能为空");
        }
        String curOpinion = null;
        String targetOpinion = null;
        String hrOpinion = null;
        String status;
        if (!request.approved()) {
            status = "REJECTED";
            switch (request.node()) {
                case "CUR_MGR" -> curOpinion = request.opinion();
                case "TARGET" -> targetOpinion = request.opinion();
                case "HR" -> hrOpinion = request.opinion();
                default -> throw new BizException(TalentErrorCode.PARAM_INVALID,
                        "审批节点必须为 CUR_MGR/TARGET/HR");
            }
        } else {
            switch (request.node()) {
                case "CUR_MGR" -> {
                    curOpinion = request.opinion();
                    status = "CUR_MGR_AGREE";
                }
                case "TARGET" -> {
                    targetOpinion = request.opinion();
                    status = "HR_REVIEW";
                }
                case "HR" -> {
                    hrOpinion = request.opinion();
                    status = "PASSED";
                }
                default -> throw new BizException(TalentErrorCode.PARAM_INVALID,
                        "审批节点必须为 CUR_MGR/TARGET/HR");
            }
        }
        skillMapper.updateFlowApplication(tenantId, id, status, curOpinion, targetOpinion, hrOpinion, userId);
        audit.publish("PASSED".equals(status) ? "TALENT_FLOW_APPROVED" : "TALENT_FLOW_APPROVAL_UPDATED",
                "UPDATE", "talent_flow_application", id, "MEDIUM");
        return new FlowApplyResult(id, status);
    }

    // ---------- 人岗匹配 ----------

    @Transactional(readOnly = true)
    public List<MatchItem> matches(long demandId, Integer topN) {
        long tenantId = TalentContext.tenantId();
        Map<String, Object> demand = skillMapper.findDemand(tenantId, demandId);
        if (demand == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "人才需求不存在");
        }
        List<Map<String, Object>> required = requiredSkills(Rows.str(demand, "skillsRequired"));
        String pathPrefix = null;
        Map<String, Object> org = orgMapper.findOrg(tenantId, Rows.lngVal(demand, "orgId"));
        if (org != null) {
            pathPrefix = Rows.str(org, "path");
        }
        int limit = topN == null || topN <= 0 ? 10 : Math.min(topN, 100);
        List<MatchItem> items = new ArrayList<>();
        for (Map<String, Object> employee : skillMapper.listEmployeesWithSkills(tenantId, pathPrefix)) {
            Map<Long, Integer> owned = ownedSkills(Rows.str(employee, "skills"));
            int matched = 0;
            for (Map<String, Object> need : required) {
                long skillId = ((Number) need.getOrDefault("skillId", 0)).longValue();
                int level = need.get("level") instanceof Number number ? number.intValue() : 1;
                Integer own = owned.get(skillId);
                if (own != null && own >= level) {
                    matched++;
                }
            }
            BigDecimal coverage = required.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(matched).divide(BigDecimal.valueOf(required.size()), 2, RoundingMode.HALF_UP);
            BigDecimal gradeFit = Rows.lng(employee, "gradeId") == null ? BigDecimal.valueOf(0.5)
                    : BigDecimal.ONE;
            BigDecimal score = coverage.multiply(BigDecimal.valueOf(80))
                    .add(gradeFit.multiply(BigDecimal.valueOf(20)))
                    .setScale(2, RoundingMode.HALF_UP);
            List<String> reasons = new ArrayList<>();
            reasons.add("技能覆盖 " + matched + "/" + required.size());
            reasons.add(gradeFit.compareTo(BigDecimal.ONE) == 0 ? "职级已定" : "职级待定");
            items.add(new MatchItem(Rows.lngVal(employee, "id"), Rows.str(employee, "name"), score, coverage,
                    gradeFit, reasons));
        }
        return items.stream()
                .sorted(Comparator.comparing(MatchItem::matchScore).reversed())
                .limit(limit)
                .toList();
    }

    // ---------- helpers ----------

    private List<Map<String, Object>> requiredSkills(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = objectMapper.readValue(json, List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    result.add(normalized);
                }
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "需求技能格式非法", ex);
        }
    }

    private Map<Long, Integer> ownedSkills(String json) {
        Map<Long, Integer> owned = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return owned;
        }
        try {
            List<?> list = objectMapper.readValue(json, List.class);
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object skillId = map.get("skillId");
                    Object level = map.get("level");
                    if (skillId instanceof Number id && level instanceof Number lv) {
                        owned.merge(id.longValue(), lv.intValue(), Math::max);
                    }
                }
            }
            return owned;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("员工技能解析失败", ex);
        }
    }

    private List<SkillTagView> buildTagTree(Map<Long, List<Map<String, Object>>> byParent, Long parentId) {
        List<SkillTagView> views = new ArrayList<>();
        for (Map<String, Object> row : byParent.getOrDefault(parentId, List.of())) {
            long id = Rows.lngVal(row, "id");
            views.add(new SkillTagView(id, Rows.str(row, "name"), Rows.str(row, "category"),
                    Rows.lng(row, "parentId"), Rows.str(row, "tagStatus"), buildTagTree(byParent, id)));
        }
        return views;
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
