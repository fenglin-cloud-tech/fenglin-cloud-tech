package com.fenglin.collab.web;

import com.fenglin.collab.service.OnaService;
import com.fenglin.collab.web.dto.CollabDtos.DeptNetworkView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingEfficiencyView;
import com.fenglin.collab.web.dto.CollabDtos.OnaGraphView;
import com.fenglin.collab.web.dto.CollabDtos.OnaKeyConnectorView;
import com.fenglin.collab.web.dto.CollabDtos.OnaMetricView;
import com.fenglin.collab.web.dto.CollabDtos.OnaSnapshotView;
import com.fenglin.collab.web.dto.CollabDtos.SelfProfileView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织网络分析接口（接口文档 4.8 节 API-ONA-001 ~ 007）。
 */
@RestController
@RequestMapping("/api/collab/v1/ona")
public class CollabOnaController {

    private static final String DEPT = "tenant:collab:ona:dept";
    private static final String TENANT = "tenant:collab:ona:tenant";
    private static final String SELF = "biz:collab:ona:self";

    private final OnaService onaService;

    public CollabOnaController(OnaService onaService) {
        this.onaService = onaService;
    }

    @GetMapping("/graph")
    public ApiResponse<OnaGraphView> graph(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "graphId", required = false) String graphId,
            @RequestParam(value = "scopeType", required = false) String scopeType,
            @RequestParam(value = "scopeDeptId", required = false) Long scopeDeptId) {
        PermissionGuard.require(permissions, DEPT);
        return ApiResponse.success(onaService.graph(graphId, scopeType, scopeDeptId));
    }

    @GetMapping("/metrics")
    public ApiResponse<OnaMetricView> metrics(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "snapshotId", required = false) String snapshotId,
            @RequestParam(value = "nodeType", required = false) String nodeType,
            @RequestParam(value = "nodeId", required = false) Long nodeId) {
        PermissionGuard.require(permissions, DEPT);
        return ApiResponse.success(onaService.metric(snapshotId, nodeType, nodeId));
    }

    @GetMapping("/key-connectors")
    public ApiResponse<List<OnaKeyConnectorView>> keyConnectors(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "snapshotId", required = false) String snapshotId,
            @RequestParam(value = "topN", required = false) Integer topN) {
        PermissionGuard.require(permissions, DEPT);
        return ApiResponse.success(onaService.keyConnectors(snapshotId, topN));
    }

    @GetMapping("/snapshots")
    public ApiResponse<PageResult<OnaSnapshotView>> snapshots(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, DEPT);
        return ApiResponse.success(onaService.snapshots(periodType, page, size));
    }

    @GetMapping("/meeting-efficiency")
    public ApiResponse<MeetingEfficiencyView> meetingEfficiency(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodKey", required = false) String periodKey,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        PermissionGuard.require(permissions, DEPT);
        return ApiResponse.success(onaService.meetingEfficiency(periodKey, deptId));
    }

    @GetMapping("/dept-network")
    public ApiResponse<DeptNetworkView> deptNetwork(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "level", required = false) String level) {
        PermissionGuard.require(permissions, TENANT);
        return ApiResponse.success(onaService.deptNetwork(deptId, level));
    }

    @GetMapping("/self-profile")
    public ApiResponse<SelfProfileView> selfProfile(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "userId", required = false) String userId) {
        PermissionGuard.require(permissions, SELF);
        return ApiResponse.success(onaService.selfProfile(permissions, userId));
    }
}
