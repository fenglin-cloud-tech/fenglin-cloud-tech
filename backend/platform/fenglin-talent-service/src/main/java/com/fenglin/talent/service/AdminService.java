package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.AdminMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.GradeResult;
import com.fenglin.talent.web.dto.TalentDtos.GradeUpsertRequest;
import com.fenglin.talent.web.dto.TalentDtos.GradeView;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountPlanCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountPlanCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.PositionCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PositionCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.PositionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 岗位、职级与编制计划管理。
 */
@Service
public class AdminService {

    private final AdminMapper adminMapper;
    private final TalentAudit audit;

    public AdminService(AdminMapper adminMapper, TalentAudit audit) {
        this.adminMapper = adminMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<PositionView> listPositions(String status, String keyword, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String keywordFilter = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<PositionView> records = adminMapper.listPositions(tenantId, statusFilter, keywordFilter,
                        pageSize, (pageNo - 1) * pageSize).stream()
                .map(row -> new PositionView(Rows.lngVal(row, "id"), Rows.str(row, "positionCode"),
                        Rows.str(row, "positionName"), Rows.str(row, "jobFamily"), Rows.str(row, "jobSequence"),
                        Rows.intVal(row, "headcount"), Rows.lng(row, "orgId"), Rows.str(row, "status")))
                .toList();
        return PageResult.of(records, adminMapper.countPositions(tenantId, statusFilter, keywordFilter),
                pageNo, pageSize);
    }

    @Transactional
    public PositionCreateResult createPosition(PositionCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.positionCode() == null || request.positionCode().isBlank()
                || request.positionName() == null || request.positionName().isBlank()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "岗位编码与名称不能为空");
        }
        long id = IdWorker.getId();
        adminMapper.insertPosition(id, tenantId, request.positionCode(), request.positionName(), request.orgId(),
                request.jobFamily(), request.jobSequence(), request.headcount() == null ? 0 : request.headcount(),
                request.description(), userId);
        audit.publish("TALENT_POSITION_CREATED", "CREATE", "talent_position", id, "LOW");
        return new PositionCreateResult(id, request.positionCode());
    }

    @Transactional(readOnly = true)
    public List<GradeView> listGrades() {
        long tenantId = TalentContext.tenantId();
        return adminMapper.listGrades(tenantId).stream()
                .map(row -> new GradeView(Rows.lngVal(row, "id"), Rows.str(row, "gradeCode"),
                        Rows.str(row, "gradeName"), Rows.str(row, "sequence"), Rows.intVal(row, "gradeLevel"),
                        Rows.dec(row, "bandMin"), Rows.dec(row, "bandMax")))
                .toList();
    }

    @Transactional
    public GradeResult createGrade(GradeUpsertRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        validateGrade(request, true);
        if (adminMapper.findGradeByCode(tenantId, request.gradeCode().trim()) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "职级编码已存在");
        }
        long id = IdWorker.getId();
        adminMapper.insertGrade(id, tenantId, request.gradeCode().trim(), request.gradeName().trim(),
                request.sequence().trim(), request.gradeLevel(), request.bandMin(), request.bandMax(), userId);
        audit.publish("TALENT_GRADE_CREATED", "CREATE", "talent_grade", id, "LOW");
        return new GradeResult(id, request.gradeCode().trim());
    }

    @Transactional
    public GradeResult updateGrade(long id, GradeUpsertRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        validateGrade(request, false);
        if (adminMapper.findGrade(tenantId, id) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "职级不存在");
        }
        adminMapper.updateGrade(tenantId, id, request.gradeName().trim(), request.sequence().trim(),
                request.gradeLevel(), request.bandMin(), request.bandMax(), userId);
        audit.publish("TALENT_GRADE_UPDATED", "UPDATE", "talent_grade", id, "LOW");
        Map<String, Object> grade = adminMapper.findGrade(tenantId, id);
        return new GradeResult(id, Rows.str(grade, "gradeCode"));
    }

    private void validateGrade(GradeUpsertRequest request, boolean requireCode) {
        if (request == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "职级信息不能为空");
        }
        if (requireCode && (request.gradeCode() == null || request.gradeCode().isBlank())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "职级编码不能为空");
        }
        if (request.gradeName() == null || request.gradeName().isBlank()
                || request.sequence() == null || request.sequence().isBlank()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "职级名称与序列不能为空");
        }
        if (request.gradeLevel() == null || request.gradeLevel() < 1 || request.gradeLevel() > 99) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "职级等级需在 1-99 之间");
        }
        if (request.bandMin() != null && request.bandMax() != null
                && request.bandMin().compareTo(request.bandMax()) > 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "薪酬带宽下限不能大于上限");
        }
    }

    @Transactional
    public HeadcountPlanCreateResult createHeadcountPlan(HeadcountPlanCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.orgId() == null || request.budgetHeadcount() == null
                || request.budgetHeadcount() <= 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "组织与编制人数（正整数）不能为空");
        }
        int planYear = request.planYear() == null ? LocalDate.now().getYear() : request.planYear();
        long id = IdWorker.getId();
        adminMapper.insertHeadcountPlan(id, tenantId, planYear, request.orgId(), request.positionId(),
                request.budgetHeadcount(), userId);
        audit.publish("TALENT_HEADCOUNT_PLAN_CREATED", "CREATE", "talent_headcount_plan", id, "LOW");
        return new HeadcountPlanCreateResult(id, "DRAFT");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findGrade(long tenantId, long gradeId) {
        return adminMapper.findGrade(tenantId, gradeId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findHeadcountPlan(long tenantId, long orgId, Long positionId, int planYear) {
        return adminMapper.findHeadcountPlan(tenantId, orgId, positionId, planYear);
    }
}
