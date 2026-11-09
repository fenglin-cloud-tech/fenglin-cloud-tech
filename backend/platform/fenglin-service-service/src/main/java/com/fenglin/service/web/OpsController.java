package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.MiningService;
import com.fenglin.service.service.RpaService;
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
 * 流程挖掘与 RPA：事件接入、模型发现、分析、机器人/凭据/编排/调度/任务。
 */
@RestController
@RequestMapping("/api/service/v1")
public class OpsController {

    private static final String MINING_INGEST = "service:mining:log:ingest";
    private static final String MINING_MODEL = "service:mining:model:create";
    private static final String MINING_VIEW = "service:mining:view";
    private static final String MINING_CONFORMANCE = "service:mining:conformance:view";
    private static final String MINING_ANALYZE = "service:mining:analyze";
    private static final String MINING_AUTOMATION = "service:mining:to:automation";
    private static final String ROBOT = "service:rpa:robot:manage";
    private static final String CREDENTIAL = "tenant:service:rpa:credential:config";
    private static final String AUTOMATION = "service:automation:flow:design";
    private static final String DISPATCH = "service:rpa:task:dispatch";
    private static final String TASK = "service:rpa:task:manage";

    private final MiningService miningService;
    private final RpaService rpaService;

    public OpsController(MiningService miningService, RpaService rpaService) {
        this.miningService = miningService;
        this.rpaService = rpaService;
    }

    // ---------- 流程挖掘 ----------
    @PostMapping("/mining/events")
    public ApiResponse<Map<String, Object>> importEvents(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, MINING_INGEST);
        return ApiResponse.success(miningService.importEvents(body));
    }

    @PostMapping("/mining/models/discover")
    public ApiResponse<Map<String, Object>> discover(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, MINING_MODEL);
        return ApiResponse.success(miningService.discover(body == null ? Map.of() : body));
    }

    @GetMapping("/mining/models")
    public ApiResponse<PageResult<Map<String, Object>>> models(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "resultType", required = false) String resultType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, MINING_VIEW);
        return ApiResponse.success(miningService.listResults(resultType, page, size));
    }

    @GetMapping("/mining/models/{id}")
    public ApiResponse<Map<String, Object>> model(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_VIEW);
        return ApiResponse.success(miningService.result(id));
    }

    @PostMapping("/mining/models/{id}/conformance")
    public ApiResponse<Map<String, Object>> conformance(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_CONFORMANCE);
        return ApiResponse.success(miningService.conformance(id));
    }

    @GetMapping("/mining/models/{id}/bottlenecks")
    public ApiResponse<Map<String, Object>> bottlenecks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_ANALYZE);
        return ApiResponse.success(miningService.bottleneck(id));
    }

    @GetMapping("/mining/models/{id}/resource-load")
    public ApiResponse<Map<String, Object>> resourceLoad(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_ANALYZE);
        return ApiResponse.success(miningService.resourceLoad(id));
    }

    @GetMapping("/mining/models/{id}/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_VIEW);
        return ApiResponse.success(miningService.candidates(id));
    }

    @PostMapping("/mining/candidates/{id}/to-automation")
    public ApiResponse<Map<String, Object>> toAutomation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, MINING_AUTOMATION);
        return ApiResponse.success(miningService.toAutomation(id, body == null ? Map.of() : body));
    }

    @PostMapping("/mining/models/{id}/archive")
    public ApiResponse<Map<String, Object>> archive(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MINING_MODEL);
        return ApiResponse.success(miningService.archive(id));
    }

    // ---------- 机器人 ----------
    @PostMapping("/rpa/robots")
    public ApiResponse<Map<String, Object>> registerRobot(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ROBOT);
        return ApiResponse.success(rpaService.registerRobot(body));
    }

    @GetMapping("/rpa/robots")
    public ApiResponse<PageResult<Map<String, Object>>> robots(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ROBOT);
        return ApiResponse.success(rpaService.listRobots(page, size));
    }

    @PostMapping("/rpa/robots/govern")
    public ApiResponse<Map<String, Object>> governRobot(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ROBOT);
        return ApiResponse.success(rpaService.govern(body));
    }

    // ---------- 凭据 ----------
    @PostMapping("/rpa/credentials")
    public ApiResponse<Map<String, Object>> saveCredential(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CREDENTIAL);
        return ApiResponse.success(rpaService.saveCredential(body));
    }

    @GetMapping("/rpa/credentials")
    public ApiResponse<List<Map<String, Object>>> credentials(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, CREDENTIAL);
        return ApiResponse.success(rpaService.listCredentials());
    }

    @PostMapping("/rpa/credentials/{id}/token")
    public ApiResponse<Map<String, Object>> credentialToken(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CREDENTIAL);
        return ApiResponse.success(rpaService.credentialToken(id));
    }

    @PostMapping("/internal/rpa/credentials/redeem")
    public ApiResponse<Map<String, Object>> redeemToken(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(rpaService.redeemToken(
                body == null ? null : (String) body.get("token")));
    }

    // ---------- 自动化编排 ----------
    @PostMapping("/rpa/automations")
    public ApiResponse<Map<String, Object>> saveAutomation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.saveAutomation(body));
    }

    @GetMapping("/rpa/automations")
    public ApiResponse<PageResult<Map<String, Object>>> automations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.listAutomations(status, page, size));
    }

    @GetMapping("/rpa/automations/{id}")
    public ApiResponse<Map<String, Object>> automation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.automation(
                com.fenglin.service.support.ServiceContext.tenantId(), id));
    }

    @PostMapping("/rpa/automations/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.publish(id));
    }

    @PutMapping("/rpa/automations/{id}/whitelist")
    public ApiResponse<Map<String, Object>> whitelist(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.updateWhitelist(id, body));
    }

    // ---------- 调度 ----------
    @PostMapping("/rpa/schedules")
    public ApiResponse<Map<String, Object>> createSchedule(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.createSchedule(body));
    }

    @GetMapping("/rpa/schedules")
    public ApiResponse<List<Map<String, Object>>> schedules(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, AUTOMATION);
        return ApiResponse.success(rpaService.listSchedules());
    }

    // ---------- 任务 ----------
    @PostMapping("/rpa/tasks")
    public ApiResponse<Map<String, Object>> triggerTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DISPATCH);
        return ApiResponse.success(rpaService.triggerTask(body,
                com.fenglin.service.support.ServiceContext.idempotencyKey(idemKey)));
    }

    @PostMapping("/rpa/tasks/batch")
    public ApiResponse<Map<String, Object>> batchDispatch(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DISPATCH);
        return ApiResponse.success(rpaService.batchDispatch(body));
    }

    @GetMapping("/rpa/tasks")
    public ApiResponse<PageResult<Map<String, Object>>> tasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "robotId", required = false) Long robotId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, TASK);
        return ApiResponse.success(rpaService.listTasks(status, robotId, page, size));
    }

    @GetMapping("/rpa/tasks/{id}")
    public ApiResponse<Map<String, Object>> task(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, TASK);
        return ApiResponse.success(rpaService.task(id));
    }

    @GetMapping("/rpa/tasks/{id}/logs")
    public ApiResponse<List<Map<String, Object>>> taskLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, TASK);
        return ApiResponse.success(rpaService.taskLogs(id));
    }

    @PostMapping("/rpa/tasks/{id}/actions")
    public ApiResponse<Map<String, Object>> taskAction(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, TASK);
        return ApiResponse.success(rpaService.taskAction(id, body));
    }

    @PostMapping("/rpa/tasks/{id}/retry")
    public ApiResponse<Map<String, Object>> retry(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, TASK);
        return ApiResponse.success(rpaService.retry(id));
    }

    @PostMapping("/internal/rpa/tasks/{id}/callback")
    public ApiResponse<Map<String, Object>> callback(
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(rpaService.callback(id, body));
    }
}
