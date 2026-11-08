package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.SkillService;
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
import com.fenglin.talent.web.dto.SkillDtos.SkillInventory;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagCreateRequest;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagCreateResult;
import com.fenglin.talent.web.dto.SkillDtos.SkillTagView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C3 技能与活水接口（API-180 ~ API-240）。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentSkillController {

    private static final String TAG_CREATE = "talent:admin:skill:tag:create";
    private static final String SKILL_VIEW = "talent:skill:view";
    private static final String SKILL_UPDATE = "talent:skill:update";
    private static final String INVENTORY = "talent:admin:skill:inventory";
    private static final String DEMAND_CREATE = "talent:admin:demand:create";
    private static final String DEMAND_PUBLISH = "talent:admin:demand:publish";
    private static final String FLOW_MARKET = "biz:talent:flow:market";
    private static final String FLOW_APPLY = "biz:talent:flow:apply";
    private static final String FLOW_LIST = "talent:flow:list";
    private static final String FLOW_APPROVE = "talent:admin:flow:approve";
    private static final String FLOW_MATCH = "talent:admin:flow:match";

    private final SkillService skillService;

    public TalentSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping("/skills/tags")
    public ApiResponse<SkillTagCreateResult> createTag(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody SkillTagCreateRequest request) {
        PermissionGuard.require(permissions, TAG_CREATE);
        return ApiResponse.success(skillService.createTag(request));
    }

    @GetMapping("/skills/tags")
    public ApiResponse<List<SkillTagView>> tags(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, SKILL_VIEW);
        return ApiResponse.success(skillService.listTags());
    }

    @GetMapping("/skills/employees/{id}")
    public ApiResponse<List<EmployeeSkillItem>> employeeSkills(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SKILL_VIEW);
        return ApiResponse.success(skillService.employeeSkills(id));
    }

    @PostMapping("/skills/employees/{id}")
    public ApiResponse<EmployeeSkillUpdateResult> updateEmployeeSkills(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody EmployeeSkillUpdateRequest request) {
        PermissionGuard.require(permissions, SKILL_UPDATE);
        return ApiResponse.success(skillService.updateEmployeeSkills(id, request));
    }

    @GetMapping("/skills/inventory")
    public ApiResponse<SkillInventory> inventory(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "skillId", required = false) Long skillId) {
        PermissionGuard.require(permissions, INVENTORY);
        return ApiResponse.success(skillService.inventory(orgId, skillId));
    }

    @PostMapping("/flow/demands")
    public ApiResponse<DemandCreateResult> createDemand(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DemandCreateRequest request) {
        PermissionGuard.require(permissions, DEMAND_CREATE);
        return ApiResponse.success(skillService.createDemand(request));
    }

    @PostMapping("/flow/demands/{id}/publish")
    public ApiResponse<DemandStatusResult> publishDemand(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, DEMAND_PUBLISH);
        return ApiResponse.success(skillService.publishDemand(id));
    }

    @GetMapping("/flow/market")
    public ApiResponse<PageResult<MarketItem>> market(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, FLOW_MARKET);
        return ApiResponse.success(skillService.market(keyword, orgId, skills, page, size));
    }

    @PostMapping("/flow/applications")
    public ApiResponse<FlowApplyResult> apply(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody FlowApplyRequest request) {
        PermissionGuard.require(permissions, FLOW_APPLY);
        return ApiResponse.success(skillService.apply(request));
    }

    @GetMapping("/flow/applications")
    public ApiResponse<PageResult<FlowApplicationItem>> applications(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "demandId", required = false) Long demandId,
            @RequestParam(value = "flowStatus", required = false) String flowStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, FLOW_LIST);
        return ApiResponse.success(skillService.applications(employeeId, demandId, flowStatus, page, size));
    }

    @PostMapping("/flow/applications/{id}/approve")
    public ApiResponse<FlowApplyResult> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody FlowApproveRequest request) {
        PermissionGuard.require(permissions, FLOW_APPROVE);
        return ApiResponse.success(skillService.approve(id, request));
    }

    @GetMapping("/flow/demands/{id}/matches")
    public ApiResponse<List<MatchItem>> matches(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "topN", required = false) Integer topN) {
        PermissionGuard.require(permissions, FLOW_MATCH);
        return ApiResponse.success(skillService.matches(id, topN));
    }
}
