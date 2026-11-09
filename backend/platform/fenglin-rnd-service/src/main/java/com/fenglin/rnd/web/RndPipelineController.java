package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.PipelineService;
import com.fenglin.rnd.web.dto.RndDtos.PipelineCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.PipelineView;
import com.fenglin.rnd.web.dto.RndDtos.RunFinishRequest;
import com.fenglin.rnd.web.dto.RndDtos.RunView;
import com.fenglin.rnd.web.dto.RndDtos.TriggerRequest;
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
 * E1 流水线：配置、触发与运行回调接口。
 */
@RestController
@RequestMapping("/api/rnd/v1")
public class RndPipelineController {

    private static final String MANAGE = "biz:devops:pipeline:manage";
    private static final String TRIGGER = "biz:devops:pipeline:trigger";
    private static final String VIEW = "biz:devops:pipeline:view";

    private final PipelineService pipelineService;

    public RndPipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/pipelines")
    public ApiResponse<PipelineView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PipelineCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(pipelineService.create(request));
    }

    @GetMapping("/pipelines")
    public ApiResponse<List<PipelineView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(pipelineService.list(productId));
    }

    @GetMapping("/pipelines/{id}")
    public ApiResponse<PipelineView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(pipelineService.get(id));
    }

    @PostMapping("/pipelines/{id}/runs")
    public ApiResponse<RunView> trigger(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) TriggerRequest request) {
        PermissionGuard.require(permissions, TRIGGER);
        return ApiResponse.success(pipelineService.trigger(id, request == null ? null : request.triggerType()));
    }

    @GetMapping("/pipelines/{id}/runs")
    public ApiResponse<List<RunView>> listRuns(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(pipelineService.listRuns(id, limit));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<RunView> getRun(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(pipelineService.getRun(id));
    }

    @PostMapping("/runs/{id}/finish")
    public ApiResponse<RunView> finish(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody(required = false) RunFinishRequest request) {
        PermissionGuard.require(permissions, TRIGGER);
        return ApiResponse.success(pipelineService.finish(id, request));
    }
}
