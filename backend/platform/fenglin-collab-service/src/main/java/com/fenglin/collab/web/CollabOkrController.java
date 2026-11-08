package com.fenglin.collab.web;

import com.fenglin.collab.service.OkrService;
import com.fenglin.collab.web.dto.CollabDtos.AlignRequest;
import com.fenglin.collab.web.dto.CollabDtos.AlignResult;
import com.fenglin.collab.web.dto.CollabDtos.AlignmentGraph;
import com.fenglin.collab.web.dto.CollabDtos.KrCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.KrView;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveView;
import com.fenglin.collab.web.dto.CollabDtos.PeriodView;
import com.fenglin.collab.web.dto.CollabDtos.ProgressRequest;
import com.fenglin.collab.web.dto.CollabDtos.ProgressResult;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
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
 * OKR 接口（API-OKR-001 ~ 006 / 050）。
 */
@RestController
@RequestMapping("/api/collab/v1/okr")
public class CollabOkrController {

    private static final String READ = "biz:collab:okr:read";
    private static final String MANAGE = "biz:collab:okr:manage";
    private static final String PERIOD_ADMIN = "tenant:collab:okr:period:manage";

    private final OkrService okrService;

    public CollabOkrController(OkrService okrService) {
        this.okrService = okrService;
    }

    @GetMapping("/periods")
    public ApiResponse<List<PeriodView>> periods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.listPeriods());
    }

    @PostMapping("/periods/{periodId}/close")
    public ApiResponse<Void> closePeriod(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("periodId") String periodId) {
        PermissionGuard.require(permissions, PERIOD_ADMIN);
        okrService.closePeriod(periodId);
        return ApiResponse.success(null);
    }

    @PostMapping("/objectives")
    public ApiResponse<ObjectiveView> createObjective(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ObjectiveCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.createObjective(request));
    }

    @GetMapping("/objectives")
    public ApiResponse<List<ObjectiveView>> objectives(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) String periodId,
            @RequestParam(value = "ownerId", required = false) Long ownerId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.listObjectives(periodId, ownerId));
    }

    @PostMapping("/objectives/{objectiveId}/key-results")
    public ApiResponse<KrView> createKr(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId,
            @RequestBody KrCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.createKr(objectiveId, request));
    }

    @PostMapping("/objectives/{objectiveId}/align")
    public ApiResponse<AlignResult> align(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId,
            @RequestBody AlignRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.align(objectiveId, request));
    }

    @PostMapping("/key-results/{krId}/progress")
    public ApiResponse<ProgressResult> progress(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("krId") String krId,
            @RequestBody ProgressRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.updateProgress(krId, request));
    }

    @GetMapping("/alignment-graph")
    public ApiResponse<AlignmentGraph> alignmentGraph(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) String periodId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.alignmentGraph(periodId));
    }
}
