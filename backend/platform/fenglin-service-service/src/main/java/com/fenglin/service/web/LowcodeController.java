package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.LowcodeService;
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
 * 低代码：应用/表单/流程/模型/页面/发布/运行时/脚本治理。
 */
@RestController
@RequestMapping("/api/service/v1")
public class LowcodeController {

    private static final String APP_CREATE = "service:lowcode:app:create";
    private static final String FORM_DESIGN = "service:lowcode:form:design";
    private static final String FLOW_DESIGN = "service:lowcode:flow:design";
    private static final String MODEL_DESIGN = "service:lowcode:model:design";
    private static final String PAGE_DESIGN = "service:lowcode:page:design";
    private static final String APP_PUBLISH = "service:lowcode:app:publish";
    private static final String APP_APPROVE = "tenant:service:app:approve";
    private static final String APP_GOVERN = "tenant:service:app:govern";
    private static final String RUNTIME = "service:lowcode:runtime:use";
    private static final String SCRIPT_EDIT = "service:lowcode:script:edit";
    private static final String SCRIPT_MANAGE = "service:lowcode:script:manage";
    private static final String PERM_CONFIG = "service:lowcode:app:perm:config";
    private static final String TEMPLATE = "service:lowcode:template:use";

    private final LowcodeService lowcodeService;

    public LowcodeController(LowcodeService lowcodeService) {
        this.lowcodeService = lowcodeService;
    }

    // ---------- 应用 ----------
    @PostMapping("/lowcode/apps")
    public ApiResponse<Map<String, Object>> createApp(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, APP_CREATE);
        return ApiResponse.success(lowcodeService.createApp(body));
    }

    @GetMapping("/lowcode/apps")
    public ApiResponse<PageResult<Map<String, Object>>> apps(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, APP_CREATE);
        return ApiResponse.success(lowcodeService.listApps(status, keyword, page, size));
    }

    @GetMapping("/lowcode/apps/{id}")
    public ApiResponse<Map<String, Object>> app(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APP_CREATE);
        return ApiResponse.success(lowcodeService.app(id));
    }

    @PostMapping("/lowcode/apps/{id}/copy")
    public ApiResponse<Map<String, Object>> copyApp(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APP_CREATE);
        return ApiResponse.success(lowcodeService.copyApp(id));
    }

    @PostMapping("/lowcode/apps/{id}/archive")
    public ApiResponse<Map<String, Object>> archiveApp(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APP_CREATE);
        return ApiResponse.success(lowcodeService.archiveApp(id));
    }

    @PostMapping("/lowcode/apps/{id}/freeze")
    public ApiResponse<Map<String, Object>> freezeApp(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APP_GOVERN);
        return ApiResponse.success(lowcodeService.freezeApp(id));
    }

    @GetMapping("/admin/lowcode/apps")
    public ApiResponse<PageResult<Map<String, Object>>> governApps(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, APP_GOVERN);
        return ApiResponse.success(lowcodeService.listApps(status, keyword, page, size));
    }

    @PutMapping("/lowcode/apps/{id}/permissions")
    public ApiResponse<Map<String, Object>> appPermissions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PERM_CONFIG);
        return ApiResponse.success(lowcodeService.savePermission(id, body, "APP"));
    }

    @PutMapping("/lowcode/apps/{id}/data-permissions")
    public ApiResponse<Map<String, Object>> dataPermissions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PERM_CONFIG);
        return ApiResponse.success(lowcodeService.savePermission(id, body, "DATA"));
    }

    // ---------- 表单 ----------
    @PostMapping("/lowcode/apps/{appId}/forms")
    public ApiResponse<Map<String, Object>> saveForm(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        body.put("app_id", appId);
        return ApiResponse.success(lowcodeService.saveForm(0, body));
    }

    @PutMapping("/lowcode/forms/{id}")
    public ApiResponse<Map<String, Object>> updateForm(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        return ApiResponse.success(lowcodeService.saveForm(id, body));
    }

    @GetMapping("/lowcode/forms/{id}")
    public ApiResponse<Map<String, Object>> form(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        return ApiResponse.success(lowcodeService.form(id));
    }

    @GetMapping("/lowcode/forms/{id}/versions")
    public ApiResponse<List<Map<String, Object>>> formVersions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        return ApiResponse.success(lowcodeService.formVersions(id));
    }

    @PostMapping("/lowcode/forms/{id}/validate")
    public ApiResponse<Map<String, Object>> validateForm(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        return ApiResponse.success(lowcodeService.validateForm(id, body));
    }

    @PostMapping("/lowcode/forms/{id}/preview")
    public ApiResponse<Map<String, Object>> previewForm(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, FORM_DESIGN);
        return ApiResponse.success(lowcodeService.form(id));
    }

    // ---------- 流程 ----------
    @PostMapping("/lowcode/apps/{appId}/flows")
    public ApiResponse<Map<String, Object>> saveFlow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        body.put("app_id", appId);
        return ApiResponse.success(lowcodeService.saveFlow(0, body));
    }

    @PutMapping("/lowcode/flows/{id}")
    public ApiResponse<Map<String, Object>> updateFlow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        return ApiResponse.success(lowcodeService.saveFlow(id, body));
    }

    @GetMapping("/lowcode/flows/{id}")
    public ApiResponse<Map<String, Object>> flow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        return ApiResponse.success(lowcodeService.flowDef(id));
    }

    @GetMapping("/lowcode/flows/{id}/versions")
    public ApiResponse<List<Map<String, Object>>> flowVersions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        return ApiResponse.success(lowcodeService.flowVersions(id));
    }

    @PostMapping("/lowcode/flows/{id}/validate")
    public ApiResponse<Map<String, Object>> validateFlow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        return ApiResponse.success(lowcodeService.validateFlow(id, body));
    }

    @PostMapping("/lowcode/flows/{id}/simulate")
    public ApiResponse<Map<String, Object>> simulateFlow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, FLOW_DESIGN);
        return ApiResponse.success(lowcodeService.simulateFlow(id, body));
    }

    // ---------- 数据模型 ----------
    @PostMapping("/lowcode/apps/{appId}/models")
    public ApiResponse<Map<String, Object>> saveModel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, MODEL_DESIGN);
        body.put("app_id", appId);
        return ApiResponse.success(lowcodeService.saveModel(0, body));
    }

    @PutMapping("/lowcode/models/{id}")
    public ApiResponse<Map<String, Object>> updateModel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, MODEL_DESIGN);
        return ApiResponse.success(lowcodeService.saveModel(id, body));
    }

    @GetMapping("/lowcode/models/{id}")
    public ApiResponse<Map<String, Object>> model(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MODEL_DESIGN);
        return ApiResponse.success(lowcodeService.model(id));
    }

    @GetMapping("/lowcode/models/{id}/diff")
    public ApiResponse<Map<String, Object>> modelDiff(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, MODEL_DESIGN);
        return ApiResponse.success(lowcodeService.modelDiff(id));
    }

    @PostMapping("/lowcode/models/{id}/compat-check")
    public ApiResponse<Map<String, Object>> compatCheck(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, MODEL_DESIGN);
        return ApiResponse.success(lowcodeService.compatCheck(id, body));
    }

    // ---------- 页面 ----------
    @PostMapping("/lowcode/apps/{appId}/pages")
    public ApiResponse<Map<String, Object>> savePage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PAGE_DESIGN);
        body.put("app_id", appId);
        return ApiResponse.success(lowcodeService.savePage(0, body));
    }

    @PutMapping("/lowcode/pages/{id}")
    public ApiResponse<Map<String, Object>> updatePage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PAGE_DESIGN);
        return ApiResponse.success(lowcodeService.savePage(id, body));
    }

    @GetMapping("/lowcode/pages/{id}")
    public ApiResponse<Map<String, Object>> page(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PAGE_DESIGN);
        return ApiResponse.success(lowcodeService.page(id));
    }

    @PutMapping("/lowcode/pages/{id}/datasource")
    public ApiResponse<Map<String, Object>> pageDatasource(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PAGE_DESIGN);
        return ApiResponse.success(lowcodeService.updatePageDatasource(id, body));
    }

    @PostMapping("/lowcode/pages/{id}/preview")
    public ApiResponse<Map<String, Object>> previewPage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, PAGE_DESIGN);
        return ApiResponse.success(lowcodeService.previewPage(id, body));
    }

    // ---------- 发布 ----------
    @PostMapping("/lowcode/apps/{appId}/releases")
    public ApiResponse<Map<String, Object>> submitRelease(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, APP_PUBLISH);
        return ApiResponse.success(lowcodeService.submitRelease(appId, body, permissions, requestId));
    }

    @GetMapping("/lowcode/releases")
    public ApiResponse<PageResult<Map<String, Object>>> releases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "appId", required = false) Long appId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, APP_PUBLISH);
        return ApiResponse.success(lowcodeService.listReleases(appId, page, size));
    }

    @GetMapping("/lowcode/releases/{id}")
    public ApiResponse<Map<String, Object>> release(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, APP_PUBLISH);
        return ApiResponse.success(lowcodeService.release(id));
    }

    @PostMapping("/lowcode/releases/{id}/approve")
    public ApiResponse<Map<String, Object>> approveRelease(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, APP_APPROVE);
        return ApiResponse.success(lowcodeService.approveRelease(id, body));
    }

    // ---------- 运行时 ----------
    @PostMapping("/runtime/flows/{flowId}/submit")
    public ApiResponse<Map<String, Object>> runtimeSubmit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("flowId") long flowId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.runtimeSubmit(flowId, body));
    }

    @PostMapping("/runtime/apps/{appCode}/pages/{pageKey}/submit")
    public ApiResponse<Map<String, Object>> runtimePageSubmit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appCode") String appCode, @PathVariable("pageKey") String pageKey,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.runtimePageSubmit(appCode, pageKey, body));
    }

    @PostMapping("/runtime/apps/{appCode}/flows/{flowKey}/start")
    public ApiResponse<Map<String, Object>> runtimeStartFlow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appCode") String appCode, @PathVariable("flowKey") String flowKey,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.runtimeStartFlow(appCode, flowKey, body));
    }

    @PostMapping("/runtime/instances/{instanceId}/tasks/{taskId}/complete")
    public ApiResponse<Map<String, Object>> completeTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("instanceId") long instanceId, @PathVariable("taskId") long taskId,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.completeTask(instanceId, taskId, body));
    }

    @GetMapping("/runtime/tasks")
    public ApiResponse<PageResult<Map<String, Object>>> runtimeTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.runtimeTasks(assigneeId, status, page, size));
    }

    @GetMapping("/runtime/instances")
    public ApiResponse<List<Map<String, Object>>> runtimeInstances(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status) {
        PermissionGuard.require(permissions, RUNTIME);
        return ApiResponse.success(lowcodeService.instances(status));
    }

    // ---------- 脚本 ----------
    @PostMapping("/lowcode/apps/{appId}/scripts")
    public ApiResponse<Map<String, Object>> saveScript(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("appId") long appId, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, SCRIPT_EDIT);
        return ApiResponse.success(lowcodeService.saveScript(appId, body));
    }

    @GetMapping("/lowcode/scripts/{id}")
    public ApiResponse<Map<String, Object>> script(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SCRIPT_MANAGE);
        return ApiResponse.success(lowcodeService.script(id));
    }

    @PostMapping("/lowcode/scripts/{id}/scan")
    public ApiResponse<Map<String, Object>> scanScript(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SCRIPT_MANAGE);
        return ApiResponse.success(lowcodeService.scanScript(id));
    }

    // ---------- 模板 ----------
    @GetMapping("/lowcode/templates")
    public ApiResponse<List<Map<String, Object>>> templates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, TEMPLATE);
        return ApiResponse.success(lowcodeService.templates());
    }
}
