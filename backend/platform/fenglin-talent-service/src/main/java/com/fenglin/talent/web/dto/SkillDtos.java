package com.fenglin.talent.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * C3 技能与活水 DTO。
 */
public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillTagCreateRequest(String name, String category, Long parentId) {
    }

    public record SkillTagCreateResult(Long id, String tagStatus) {
    }

    public record SkillTagView(Long id, String name, String category, Long parentId, String tagStatus,
                               List<SkillTagView> children) {
    }

    public record EmployeeSkillItem(Long skillId, String skillName, Integer selfLevel, Integer certLevel,
                                    BigDecimal years, LocalDate lastUsedAt, Long verifiedBy) {
    }

    public record SkillUpdate(Long skillId, Integer selfLevel, BigDecimal years, String evidence) {
    }

    public record EmployeeSkillUpdateRequest(List<SkillUpdate> skills) {
    }

    public record EmployeeSkillUpdateResult(int updated) {
    }

    public record SkillHeat(Long skillId, Integer level, Long count) {
    }

    public record SkillInventory(Long orgId, BigDecimal coverage, List<SkillHeat> heatmap) {
    }

    public record DemandCreateRequest(Long orgId, Long positionId, Object skillsRequired, Integer headcount,
                                      LocalDate expectedDate, String demandType, String publishScope,
                                      Long ownerId) {
    }

    public record DemandCreateResult(Long id, String demandNo, String dmStatus) {
    }

    public record DemandStatusResult(Long id, String dmStatus) {
    }

    public record MarketItem(Long demandId, String demandNo, String orgName, String positionName, Object skills,
                             Integer headcount, LocalDate expectedDate, BigDecimal matchScore) {
    }

    public record FlowApplyRequest(Long demandId, String applyType, String reason, Long employeeId) {
    }

    public record FlowApplyResult(Long id, String flowStatus) {
    }

    public record FlowApproveRequest(String node, Boolean approved, String opinion) {
    }

    public record FlowApplicationItem(Long id, String demandNo, String employeeName, String applyType,
                                      String flowStatus, java.time.Instant createdAt) {
    }

    public record MatchItem(Long employeeId, String name, BigDecimal matchScore, BigDecimal skillCoverage,
                            BigDecimal gradeFit, List<String> reasons) {
    }
}
