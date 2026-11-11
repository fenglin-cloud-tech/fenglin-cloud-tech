package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.LifecycleService;
import com.fenglin.talent.web.dto.TalentDtos.ApprovalRequest;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCompleteResult;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.OnboardingView;
import com.fenglin.talent.web.dto.TalentDtos.ProbationEvaluateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ProbationEvaluateResult;
import com.fenglin.talent.web.dto.TalentDtos.ResignationApproveRequest;
import com.fenglin.talent.web.dto.TalentDtos.ResignationCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.ResignationStatusResult;
import com.fenglin.talent.web.dto.TalentDtos.ResignationView;
import com.fenglin.talent.web.dto.TalentDtos.TransferAfter;
import com.fenglin.talent.web.dto.TalentDtos.TransferApprovalResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.TransferCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferEffectiveResult;
import com.fenglin.talent.web.dto.TalentDtos.TransferView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入转调离接口。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentLifecycleController {

    private static final String ONBOARDING_CREATE = "talent:admin:onboarding:create";
    private static final String ONBOARDING_COMPLETE = "talent:admin:onboarding:complete";
    private static final String ONBOARDING_LIST = "talent:admin:onboarding:list";
    private static final String PROBATION_EVALUATE = "talent:admin:probation:evaluate";
    private static final String TRANSFER_CREATE = "talent:admin:transfer:create";
    private static final String TRANSFER_APPROVE = "talent:admin:transfer:approve";
    private static final String TRANSFER_EFFECTIVE = "talent:admin:transfer:effective";
    private static final String TRANSFER_LIST = "talent:admin:transfer:list";
    private static final String RESIGNATION_APPLY = "biz:talent:resignation:apply";
    private static final String RESIGNATION_APPROVE = "talent:admin:resignation:approve";
    private static final String RESIGNATION_LIST = "talent:admin:resignation:list";

    private final LifecycleService lifecycleService;

    public TalentLifecycleController(LifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/onboarding")
    public ApiResponse<PageResult<OnboardingView>> onboardings(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ONBOARDING_LIST);
        return ApiResponse.success(lifecycleService.onboardings(status, page, size));
    }

    @GetMapping("/transfers")
    public ApiResponse<PageResult<TransferView>> transfers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, TRANSFER_LIST);
        return ApiResponse.success(lifecycleService.transfers(status, page, size));
    }

    @GetMapping("/resignations")
    public ApiResponse<PageResult<ResignationView>> resignations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, RESIGNATION_LIST);
        return ApiResponse.success(lifecycleService.resignations(status, page, size));
    }

    @PostMapping("/onboarding")
    public ApiResponse<OnboardingCreateResult> createOnboarding(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody OnboardingCreateRequest request) {
        PermissionGuard.require(permissions, ONBOARDING_CREATE);
        return ApiResponse.success(lifecycleService.createOnboarding(request));
    }

    @PostMapping("/onboarding/{id}/complete")
    public ApiResponse<OnboardingCompleteResult> completeOnboarding(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ONBOARDING_COMPLETE);
        return ApiResponse.success(lifecycleService.completeOnboarding(id));
    }

    @PostMapping("/probation/{id}/evaluate")
    public ApiResponse<ProbationEvaluateResult> evaluateProbation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ProbationEvaluateRequest request) {
        PermissionGuard.require(permissions, PROBATION_EVALUATE);
        return ApiResponse.success(lifecycleService.evaluateProbation(id, request));
    }

    @PostMapping("/transfers")
    public ApiResponse<TransferCreateResult> createTransfer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TransferCreateRequest request) {
        PermissionGuard.require(permissions, TRANSFER_CREATE);
        return ApiResponse.success(lifecycleService.createTransfer(request, permissions));
    }

    @PostMapping("/transfers/{id}/effective")
    public ApiResponse<TransferEffectiveResult> effectiveTransfer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, TRANSFER_EFFECTIVE);
        return ApiResponse.success(lifecycleService.createEffectiveTransfer(id));
    }

    @PostMapping("/transfers/{id}/approval")
    public ApiResponse<TransferApprovalResult> approveTransfer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        PermissionGuard.require(permissions, TRANSFER_APPROVE);
        return ApiResponse.success(lifecycleService.approveTransfer(id, request));
    }

    @PostMapping("/resignations")
    public ApiResponse<ResignationStatusResult> createResignation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ResignationCreateRequest request) {
        PermissionGuard.require(permissions, RESIGNATION_APPLY);
        return ApiResponse.success(lifecycleService.createResignation(request));
    }

    @PostMapping("/resignations/{id}/approve")
    public ApiResponse<ResignationStatusResult> approveResignation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ResignationApproveRequest request) {
        PermissionGuard.require(permissions, RESIGNATION_APPROVE);
        return ApiResponse.success(lifecycleService.approveResignation(id, request));
    }
}
