package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.EngagementService;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionCreateRequest;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionCreateResult;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionUpdateRequest;
import com.fenglin.talent.web.dto.EngagementDtos.InterventionUpdateResult;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C4 敬业度与离职预测接口（API-250 ~ API-292）。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentEngagementController {

    private static final String SURVEY_CREATE = "talent:admin:survey:create";
    private static final String SURVEY_LIST = "talent:admin:survey:list";
    private static final String SURVEY_PUBLISH = "talent:admin:survey:publish";
    private static final String SURVEY_ANSWER = "biz:talent:survey:answer";
    private static final String SURVEY_SUBMIT = "biz:talent:survey:submit";
    private static final String SURVEY_ANALYSIS = "talent:admin:survey:analysis";
    private static final String ATTRITION_LIST = "talent:admin:attrition:list";
    private static final String ATTRITION_SCORE = "talent:admin:attrition:score";
    private static final String INTERVENTION_CREATE = "talent:admin:intervention:create";

    private final EngagementService engagementService;

    public TalentEngagementController(EngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @PostMapping("/engagement/surveys")
    public ApiResponse<SurveyCreateResult> createSurvey(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody SurveyCreateRequest request) {
        PermissionGuard.require(permissions, SURVEY_CREATE);
        return ApiResponse.success(engagementService.createSurvey(request));
    }

    @GetMapping("/engagement/surveys")
    public ApiResponse<PageResult<SurveyListItem>> listSurveys(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SURVEY_LIST);
        return ApiResponse.success(engagementService.listSurveys(page, size));
    }

    @PostMapping("/engagement/surveys/{id}/publish")
    public ApiResponse<SurveyPublishResult> publishSurvey(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SURVEY_PUBLISH);
        return ApiResponse.success(engagementService.publishSurvey(id));
    }

    @GetMapping("/engagement/surveys/{id}/questions")
    public ApiResponse<SurveyQuestionsResult> questions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SURVEY_ANSWER);
        return ApiResponse.success(engagementService.questions(id));
    }

    @PostMapping("/engagement/surveys/{id}/submit")
    public ApiResponse<SurveySubmitResult> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody SurveySubmitRequest request) {
        PermissionGuard.require(permissions, SURVEY_SUBMIT);
        return ApiResponse.success(engagementService.submit(id, request));
    }

    @GetMapping("/engagement/surveys/{id}/analysis")
    public ApiResponse<SurveyAnalysis> analysis(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SURVEY_ANALYSIS);
        return ApiResponse.success(engagementService.analysis(id));
    }

    @GetMapping("/attrition/risks")
    public ApiResponse<PageResult<RiskItem>> risks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ATTRITION_LIST);
        return ApiResponse.success(engagementService.risks(riskLevel, orgId, page, size));
    }

    @PostMapping("/attrition/score/trigger")
    public ApiResponse<RiskScoreTriggerResult> triggerScoring(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RiskScoreTriggerRequest request) {
        PermissionGuard.require(permissions, ATTRITION_SCORE);
        return ApiResponse.success(engagementService.triggerScoring(request));
    }

    @PostMapping("/attrition/interventions")
    public ApiResponse<InterventionCreateResult> createIntervention(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody InterventionCreateRequest request) {
        PermissionGuard.require(permissions, INTERVENTION_CREATE);
        return ApiResponse.success(engagementService.createIntervention(request));
    }

    @PutMapping("/attrition/interventions/{id}")
    public ApiResponse<InterventionUpdateResult> updateIntervention(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody InterventionUpdateRequest request) {
        PermissionGuard.require(permissions, INTERVENTION_CREATE);
        return ApiResponse.success(engagementService.updateIntervention(id, request));
    }
}
