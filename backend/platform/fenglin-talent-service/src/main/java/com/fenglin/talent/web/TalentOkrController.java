package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.OkrService;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.AlignmentTree;
import com.fenglin.talent.web.dto.OkrDtos.KrCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.KrCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.KrProgressRequest;
import com.fenglin.talent.web.dto.OkrDtos.KrProgressResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveReviewRequest;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveReviewResult;
import com.fenglin.talent.web.dto.OkrDtos.ObjectiveView;
import com.fenglin.talent.web.dto.OkrDtos.PeriodCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.PeriodCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.PeriodView;
import com.fenglin.talent.web.dto.OkrDtos.ProposalCreateRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalCreateResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalListItem;
import com.fenglin.talent.web.dto.OkrDtos.ProposalReviewRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalReviewResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalStatusResult;
import com.fenglin.talent.web.dto.OkrDtos.ProposalVoteRequest;
import com.fenglin.talent.web.dto.OkrDtos.ProposalVoteResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C5 OKR 与创新接口（API-300 ~ API-362）。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentOkrController {

    private static final String PERIOD = "talent:admin:okr:period";
    private static final String OKR_CREATE = "talent:okr:create";
    private static final String OKR_LIST = "talent:okr:list";
    private static final String KR_CREATE = "talent:okr:kr:create";
    private static final String KR_UPDATE = "talent:okr:kr:update";
    private static final String ALIGN = "talent:okr:align";
    private static final String ALIGN_VIEW = "talent:okr:align:view";
    private static final String REVIEW = "talent:okr:review";
    private static final String PROPOSAL_SUBMIT = "biz:talent:innovation:submit";
    private static final String PROPOSAL_LIST = "talent:innovation:list";
    private static final String PROPOSAL_REVIEW = "talent:innovation:review";
    private static final String PROPOSAL_VOTE = "biz:talent:innovation:vote";
    private static final String PROPOSAL_INCUBATE = "talent:admin:innovation:incubate";

    private final OkrService okrService;

    public TalentOkrController(OkrService okrService) {
        this.okrService = okrService;
    }

    // ---------- 周期 / 目标 ----------

    @PostMapping("/okr/periods")
    public ApiResponse<PeriodCreateResult> createPeriod(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PeriodCreateRequest request) {
        PermissionGuard.require(permissions, PERIOD);
        return ApiResponse.success(okrService.createPeriod(request));
    }

    @GetMapping("/okr/periods")
    public ApiResponse<List<PeriodView>> periods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, OKR_LIST);
        return ApiResponse.success(okrService.listPeriods());
    }

    @PostMapping("/okr/objectives")
    public ApiResponse<ObjectiveCreateResult> createObjective(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ObjectiveCreateRequest request) {
        PermissionGuard.require(permissions, OKR_CREATE);
        return ApiResponse.success(okrService.createObjective(request));
    }

    @GetMapping("/okr/objectives")
    public ApiResponse<List<ObjectiveView>> objectives(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "objLevel", required = false) String objLevel,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "tree", required = false) Boolean tree,
            @RequestParam(value = "rootObjectiveId", required = false) Long rootObjectiveId) {
        PermissionGuard.require(permissions, OKR_LIST);
        return ApiResponse.success(okrService.listObjectives(periodId, orgId, objLevel, ownerId, tree,
                rootObjectiveId));
    }

    @PostMapping("/okr/objectives/{id}/key-results")
    public ApiResponse<KrCreateResult> createKr(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody KrCreateRequest request) {
        PermissionGuard.require(permissions, KR_CREATE);
        return ApiResponse.success(okrService.createKr(id, request));
    }

    @PutMapping("/okr/key-results/{id}/progress")
    public ApiResponse<KrProgressResult> updateKrProgress(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody KrProgressRequest request) {
        PermissionGuard.require(permissions, KR_UPDATE);
        return ApiResponse.success(okrService.updateKrProgress(id, request));
    }

    @PostMapping("/okr/alignments")
    public ApiResponse<AlignmentCreateResult> align(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody AlignmentCreateRequest request) {
        PermissionGuard.require(permissions, ALIGN);
        return ApiResponse.success(okrService.align(request));
    }

    @GetMapping("/okr/alignments/tree")
    public ApiResponse<AlignmentTree> alignmentTree(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "rootObjectiveId", required = false) Long rootObjectiveId) {
        PermissionGuard.require(permissions, ALIGN_VIEW);
        return ApiResponse.success(okrService.alignmentTree(periodId, rootObjectiveId));
    }

    @PostMapping("/okr/objectives/{id}/review")
    public ApiResponse<ObjectiveReviewResult> review(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ObjectiveReviewRequest request) {
        PermissionGuard.require(permissions, REVIEW);
        return ApiResponse.success(okrService.review(id, request));
    }

    // ---------- 创新提案 ----------

    @PostMapping("/innovation/proposals")
    public ApiResponse<ProposalCreateResult> createProposal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ProposalCreateRequest request) {
        PermissionGuard.require(permissions, PROPOSAL_SUBMIT);
        return ApiResponse.success(okrService.createProposal(request));
    }

    @GetMapping("/innovation/proposals")
    public ApiResponse<PageResult<ProposalListItem>> proposals(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "propStatus", required = false) String propStatus,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, PROPOSAL_LIST);
        return ApiResponse.success(okrService.listProposals(propStatus, category, keyword, page, size));
    }

    @PostMapping("/innovation/proposals/{id}/reviews")
    public ApiResponse<ProposalReviewResult> reviewProposal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ProposalReviewRequest request) {
        PermissionGuard.require(permissions, PROPOSAL_REVIEW);
        return ApiResponse.success(okrService.reviewProposal(id, request));
    }

    @PostMapping("/innovation/proposals/{id}/vote")
    public ApiResponse<ProposalVoteResult> vote(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ProposalVoteRequest request) {
        PermissionGuard.require(permissions, PROPOSAL_VOTE);
        return ApiResponse.success(okrService.vote(id, request));
    }

    @PostMapping("/innovation/proposals/{id}/incubate")
    public ApiResponse<ProposalStatusResult> incubate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROPOSAL_INCUBATE);
        return ApiResponse.success(okrService.incubate(id));
    }
}
