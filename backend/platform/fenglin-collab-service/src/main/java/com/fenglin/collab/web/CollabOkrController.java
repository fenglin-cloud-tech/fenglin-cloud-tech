package com.fenglin.collab.web;

import com.fenglin.collab.service.OkrService;
import com.fenglin.collab.web.dto.CollabDtos.AlignRequest;
import com.fenglin.collab.web.dto.CollabDtos.AlignResult;
import com.fenglin.collab.web.dto.CollabDtos.AlignmentGraph;
import com.fenglin.collab.web.dto.CollabDtos.KrCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.KrUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.KrView;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ObjectiveView;
import com.fenglin.collab.web.dto.CollabDtos.PeriodView;
import com.fenglin.collab.web.dto.CollabDtos.ProgressRequest;
import com.fenglin.collab.web.dto.CollabDtos.ProgressResult;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Map;

/**
 * OKR 接口（API-OKR-001 ~ 006 / 050）。
 */
@RestController
@RequestMapping("/api/collab/v1/okr")
public class CollabOkrController {

    private static final String READ = "biz:collab:okr:read";
    private static final String SELF_READ = "biz:collab:okr:self:read";
    private static final String MANAGE = "biz:collab:okr:manage";
    private static final String SELF_CREATE = "biz:collab:okr:self:create";
    private static final String PROGRESS = "biz:collab:okr:progress";
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

    /**
     * 员工自助：OKR 周期属于租户公共配置，供“我的 OKR”筛选使用。
     */
    @GetMapping("/periods/my")
    public ApiResponse<List<PeriodView>> myPeriods(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, SELF_READ);
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

    /** 重开已关闭周期（只改状态，不改历史数据）。 */
    @PostMapping("/periods/{periodId}/reopen")
    public ApiResponse<PeriodView> reopenPeriod(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("periodId") String periodId) {
        PermissionGuard.require(permissions, PERIOD_ADMIN);
        return ApiResponse.success(okrService.reopenPeriod(periodId));
    }

    /** 跨周期汇总：目标数 / KR 数 / 平均进度，可按负责人过滤。 */
    @GetMapping("/summary")
    public ApiResponse<List<Map<String, Object>>> summary(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "ownerId", required = false) Long ownerId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.periodSummary(ownerId));
    }

    @PutMapping("/objectives/{objectiveId}")
    public ApiResponse<ObjectiveView> updateObjective(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId,
            @RequestBody ObjectiveUpdateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.updateObjective(objectiveId, request));
    }

    @DeleteMapping("/objectives/{objectiveId}")
    public ApiResponse<Void> deleteObjective(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId) {
        PermissionGuard.require(permissions, MANAGE);
        okrService.deleteObjective(objectiveId);
        return ApiResponse.success(null);
    }

    @PutMapping("/key-results/{krId}")
    public ApiResponse<KrView> updateKr(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("krId") String krId,
            @RequestBody KrUpdateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.updateKr(krId, request));
    }

    @DeleteMapping("/key-results/{krId}")
    public ApiResponse<Void> deleteKr(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("krId") String krId) {
        PermissionGuard.require(permissions, MANAGE);
        okrService.deleteKr(krId);
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
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.listObjectives(periodId, ownerId, deptId));
    }

    /** 部门树：云才不可用时返回 degraded=true + 空数组，页面给出降级提示而不是 500。 */
    @GetMapping("/dept-tree")
    public ApiResponse<Map<String, Object>> deptTree(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.deptTree());
    }

    /**
     * 员工自助：只返回当前登录用户的目标，owner 由服务端固定。
     */
    @GetMapping("/objectives/my")
    public ApiResponse<List<ObjectiveView>> myObjectives(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) String periodId) {
        PermissionGuard.require(permissions, SELF_READ);
        return ApiResponse.success(okrService.myObjectives(periodId));
    }

    @PostMapping("/objectives/{objectiveId}/key-results")
    public ApiResponse<KrView> createKr(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId,
            @RequestBody KrCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(okrService.createKr(objectiveId, request));
    }

    /**
     * 员工自助：只能在本人名下的目标下创建 KR。归属由服务端取 X-User-Id，
     * 服务层校验 objective.owner_id == 当前用户，否则 403（NO_PERMISSION）。
     */
    @PostMapping("/objectives/{objectiveId}/key-results/self")
    public ApiResponse<KrView> createKrSelf(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("objectiveId") String objectiveId,
            @RequestBody KrCreateRequest request) {
        PermissionGuard.require(permissions, SELF_CREATE);
        return ApiResponse.success(okrService.createKrSelf(objectiveId, request));
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
        PermissionGuard.require(permissions, PROGRESS);
        return ApiResponse.success(okrService.updateProgress(krId, request, permissions));
    }

    @GetMapping("/alignment-graph")
    public ApiResponse<AlignmentGraph> alignmentGraph(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodId", required = false) String periodId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(okrService.alignmentGraph(periodId));
    }
}
