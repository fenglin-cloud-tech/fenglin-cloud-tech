package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.RecruitService;
import com.fenglin.talent.web.dto.TalentDtos.ApplicationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ApplicationCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.CandidateCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.CandidateCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.CandidateListItem;
import com.fenglin.talent.web.dto.TalentDtos.InterviewCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.InterviewCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.InterviewEvaluateRequest;
import com.fenglin.talent.web.dto.TalentDtos.InterviewEvaluateResult;
import com.fenglin.talent.web.dto.TalentDtos.MyInterviewItem;
import com.fenglin.talent.web.dto.TalentDtos.OfferCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OfferStatusResult;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionListItem;
import com.fenglin.talent.web.dto.TalentDtos.RequisitionSubmitResult;
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
 * 招聘接口：需求、候选人、应聘、面试、Offer。
 */
@RestController
@RequestMapping("/api/talent/v1/recruit")
public class TalentRecruitController {

    private static final String REQUISITION_CREATE = "talent:admin:recruit:create";
    private static final String REQUISITION_LIST = "talent:admin:recruit:list";
    private static final String REQUISITION_SUBMIT = "talent:admin:recruit:submit";
    private static final String CANDIDATE_CREATE = "talent:admin:candidate:create";
    private static final String CANDIDATE_LIST = "talent:admin:candidate:list";
    private static final String INTERVIEW_CREATE = "talent:admin:interview:create";
    private static final String INTERVIEW_EVALUATE = "talent:interview:evaluate";
    private static final String INTERVIEW_MY = "biz:talent:interview:my";
    private static final String OFFER_CREATE = "talent:admin:offer:create";

    private final RecruitService recruitService;

    public TalentRecruitController(RecruitService recruitService) {
        this.recruitService = recruitService;
    }

    @PostMapping("/requisitions")
    public ApiResponse<RequisitionCreateResult> createRequisition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RequisitionCreateRequest request) {
        PermissionGuard.require(permissions, REQUISITION_CREATE);
        return ApiResponse.success(recruitService.createRequisition(request));
    }

    @GetMapping("/requisitions")
    public ApiResponse<PageResult<RequisitionListItem>> listRequisitions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "reqStatus", required = false) String reqStatus,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, REQUISITION_LIST);
        return ApiResponse.success(recruitService.listRequisitions(reqStatus, orgId, ownerId, page, size));
    }

    @PostMapping("/requisitions/{id}/submit")
    public ApiResponse<RequisitionSubmitResult> submitRequisition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REQUISITION_SUBMIT);
        return ApiResponse.success(recruitService.submitRequisition(id, permissions));
    }

    @PostMapping("/candidates")
    public ApiResponse<CandidateCreateResult> createCandidate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CandidateCreateRequest request) {
        PermissionGuard.require(permissions, CANDIDATE_CREATE);
        return ApiResponse.success(recruitService.createCandidate(request));
    }

    @GetMapping("/candidates")
    public ApiResponse<PageResult<CandidateListItem>> listCandidates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "requisitionId", required = false) Long requisitionId,
            @RequestParam(value = "candStatus", required = false) String candStatus,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CANDIDATE_LIST);
        return ApiResponse.success(recruitService.listCandidates(requisitionId, candStatus, source, keyword,
                page, size));
    }

    /**
     * 应聘记录创建（接口文档缺口补齐：面试需以 application 为前置）。
     */
    @PostMapping("/applications")
    public ApiResponse<ApplicationCreateResult> createApplication(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ApplicationCreateRequest request) {
        PermissionGuard.require(permissions, CANDIDATE_CREATE);
        return ApiResponse.success(recruitService.createApplication(request));
    }

    @PostMapping("/interviews")
    public ApiResponse<InterviewCreateResult> createInterview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody InterviewCreateRequest request) {
        PermissionGuard.require(permissions, INTERVIEW_CREATE);
        return ApiResponse.success(recruitService.createInterview(request));
    }

    @PostMapping("/interviews/{id}/evaluate")
    public ApiResponse<InterviewEvaluateResult> evaluateInterview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody InterviewEvaluateRequest request) {
        PermissionGuard.require(permissions, INTERVIEW_EVALUATE);
        return ApiResponse.success(recruitService.evaluateInterview(id, request));
    }

    @GetMapping("/interviews/my")
    public ApiResponse<List<MyInterviewItem>> myInterviews(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, INTERVIEW_MY);
        return ApiResponse.success(recruitService.myInterviews());
    }

    @PostMapping("/offers")
    public ApiResponse<OfferStatusResult> createOffer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody OfferCreateRequest request) {
        PermissionGuard.require(permissions, OFFER_CREATE);
        return ApiResponse.success(recruitService.createOffer(request));
    }

    @PostMapping("/offers/{id}/send")
    public ApiResponse<OfferStatusResult> sendOffer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, OFFER_CREATE);
        return ApiResponse.success(recruitService.sendOffer(id));
    }

    @PostMapping("/offers/{id}/accept")
    public ApiResponse<OfferStatusResult> acceptOffer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, OFFER_CREATE);
        return ApiResponse.success(recruitService.acceptOffer(id));
    }
}
