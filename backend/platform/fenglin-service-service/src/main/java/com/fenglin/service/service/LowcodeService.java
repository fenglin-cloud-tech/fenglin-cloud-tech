package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.client.WorkflowClient;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.mapper.LowcodeMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 低代码：应用/表单/流程/模型/页面/发布/脚本与运行时。
 */
@Service
@Transactional
public class LowcodeService {

    private static final Set<String> FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "SELECT", "MULTI",
            "ATTACHMENT", "USER", "DEPT", "RELATION");
    private static final Set<String> HIGH_RISK = Set.of("eval(", "exec(", "child_process", "require(",
            "system(", "Runtime.getRuntime", "document.cookie", "localStorage");

    private final LowcodeMapper lowcodeMapper;
    private final ConfigMapper configMapper;
    private final CommonsMapper commonsMapper;
    private final WorkflowClient workflowClient;
    private final ServiceAudit audit;

    public LowcodeService(LowcodeMapper lowcodeMapper, ConfigMapper configMapper, CommonsMapper commonsMapper,
                          WorkflowClient workflowClient, ServiceAudit audit) {
        this.lowcodeMapper = lowcodeMapper;
        this.configMapper = configMapper;
        this.commonsMapper = commonsMapper;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    public Map<String, Object> createApp(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = IdWorker.getId();
        String appCode = ServiceContext.string(body.get("app_code")) == null
                ? "APP" + String.format("%06d", commonsMapper.nextAppCode()) : ServiceContext.string(body.get("app_code"));
        lowcodeMapper.insertApp(id, tenantId, appCode,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                ServiceContext.string(body.get("icon")), ServiceContext.string(body.get("description")),
                body.get("owner_id") == null ? userId : ServiceContext.requireLong(body.get("owner_id")),
                body.get("dept_id") == null ? null : ServiceContext.requireLong(body.get("dept_id")),
                ServiceMaps.json(body.get("visible_scope")), null, userId);
        audit.low("app.create", "app", id);
        return app(id);
    }

    public PageResult<Map<String, Object>> listApps(String status, String keyword, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = lowcodeMapper.listApps(tenantId, blank(status), blank(keyword), limit, offset);
        long total = lowcodeMapper.countApps(tenantId, blank(status), blank(keyword));
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    public Map<String, Object> app(long id) {
        Map<String, Object> app = lowcodeMapper.findApp(ServiceContext.tenantId(), id);
        if (app == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(app, "visible_scope");
    }

    public Map<String, Object> copyApp(long id) {
        Map<String, Object> source = app(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Rows.str(source, "name") + "（副本）");
        body.put("icon", Rows.str(source, "icon"));
        body.put("description", Rows.str(source, "description"));
        return createApp(body);
    }

    public Map<String, Object> archiveApp(long id) {
        int updated = lowcodeMapper.updateAppStatus(ServiceContext.tenantId(), id, "ARCHIVED", "DRAFT",
                ServiceContext.userId());
        if (updated == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return app(id);
    }

    @Transactional
    public Map<String, Object> saveForm(long formId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long appId = ServiceContext.requireLong(body.get("app_id"));
        String formKey = ServiceContext.requireText(ServiceContext.string(body.get("form_key")));
        List<Map<String, Object>> fields = fieldList(body.get("fields"));
        validateFields(fields);
        long id = formId > 0 ? formId : IdWorker.getId();
        int versionNo = body.get("version_no") == null ? 1
                : (int) ServiceContext.requireLong(body.get("version_no"));
        lowcodeMapper.insertForm(id, tenantId, appId, formKey,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))), versionNo,
                ServiceMaps.json(body.get("schema_json")), ServiceContext.string(body.get("engine_ref")),
                body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")), userId);
        int sort = 0;
        for (Map<String, Object> field : fields) {
            lowcodeMapper.insertField(IdWorker.getId(), tenantId, id,
                    ServiceContext.requireText(ServiceContext.string(field.get("field_key"))),
                    ServiceContext.requireText(ServiceContext.string(field.get("label"))),
                    ServiceContext.requireText(ServiceContext.string(field.get("field_type"))).toUpperCase(),
                    Boolean.TRUE.equals(field.get("required")),
                    ServiceMaps.json(field.get("options")), ServiceMaps.json(field.get("validators")),
                    ServiceMaps.json(field.get("linkage")), ServiceMaps.json(field.get("perm")), sort++, userId);
        }
        audit.low("form.save", "form", id);
        return form(id);
    }

    public Map<String, Object> form(long id) {
        Map<String, Object> form = lowcodeMapper.findForm(ServiceContext.tenantId(), id);
        if (form == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> result = ServiceMaps.parse(form, "schema_json");
        result.put("fields", ServiceMaps.parseAll(lowcodeMapper.listFields(ServiceContext.tenantId(), id),
                "options", "validators", "linkage", "perm_json"));
        return result;
    }

    public List<Map<String, Object>> formVersions(long id) {
        Map<String, Object> form = lowcodeMapper.findForm(ServiceContext.tenantId(), id);
        if (form == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return lowcodeMapper.listForms(ServiceContext.tenantId(), Rows.lngVal(form, "app_id"));
    }

    public Map<String, Object> validateForm(long id, Map<String, Object> body) {
        List<Map<String, Object>> fields = body.get("fields") == null
                ? ServiceMaps.parseAll(lowcodeMapper.listFields(ServiceContext.tenantId(), id), "options")
                : fieldList(body.get("fields"));
        validateFields(fields);
        return ServiceMaps.of("valid", true, "field_count", fields.size());
    }

    @Transactional
    public Map<String, Object> saveFlow(long flowId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Object bpmn = body.get("bpmn_json");
        validateFlowGraph(bpmn);
        long id = flowId > 0 ? flowId : IdWorker.getId();
        long appId = ServiceContext.requireLong(body.get("app_id"));
        int versionNo = body.get("version_no") == null ? 1
                : (int) ServiceContext.requireLong(body.get("version_no"));
        lowcodeMapper.insertFlowDef(id, tenantId, appId,
                ServiceContext.requireText(ServiceContext.string(body.get("flow_key"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))), versionNo,
                ServiceMaps.json(bpmn),
                body.get("form_id") == null ? null : ServiceContext.requireLong(body.get("form_id")),
                null, body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")), userId);
        return flowDef(id);
    }

    public Map<String, Object> flowDef(long id) {
        Map<String, Object> flow = lowcodeMapper.findFlowDef(ServiceContext.tenantId(), id);
        if (flow == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(flow, "bpmn_json");
    }

    public List<Map<String, Object>> flowVersions(long id) {
        Map<String, Object> flow = lowcodeMapper.findFlowDef(ServiceContext.tenantId(), id);
        if (flow == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return lowcodeMapper.listFlowDefs(ServiceContext.tenantId(), Rows.lngVal(flow, "app_id"));
    }

    public Map<String, Object> validateFlow(long id, Map<String, Object> body) {
        Object bpmn = body == null || body.get("bpmn_json") == null
                ? (flowDef(id).get("bpmn_json")) : body.get("bpmn_json");
        validateFlowGraph(bpmn);
        return ServiceMaps.of("valid", true);
    }

    public Map<String, Object> simulateFlow(long id, Map<String, Object> body) {
        Map<String, Object> flow = flowDef(id);
        JsonNode bpmn = ServiceMaps.node(flow.get("bpmn_json"));
        List<String> path = new ArrayList<>();
        JsonNode nodes = bpmn.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                path.add(node.path("key").asText(node.path("name").asText("node")));
            }
        }
        return ServiceMaps.of("flow_id", id, "simulated_path", path,
                "variables", body == null ? Map.of() : body);
    }

    @Transactional
    public Map<String, Object> saveModel(long modelId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long appId = ServiceContext.requireLong(body.get("app_id"));
        List<Map<String, Object>> fields = fieldList(body.get("fields"));
        int versionNo = body.get("version_no") == null ? 1
                : (int) ServiceContext.requireLong(body.get("version_no"));
        if (modelId > 0) {
            Map<String, Object> existing = lowcodeMapper.findModel(tenantId, modelId);
            if (existing == null) {
                throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
            }
            int currentVersion = Rows.intVal(existing, "version_no");
            List<Map<String, Object>> oldFields = lowcodeMapper.listModelFields(tenantId, modelId);
            boolean broken = incompatible(oldFields, fields);
            if (broken && versionNo <= currentVersion) {
                throw new BizException(ServiceErrorCode.MODEL_COMPAT_BROKEN);
            }
        }
        long id = modelId > 0 ? modelId : IdWorker.getId();
        lowcodeMapper.insertModel(id, tenantId, appId,
                ServiceContext.requireText(ServiceContext.string(body.get("model_key"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))), versionNo,
                ServiceContext.string(body.get("engine_ref")),
                body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")), userId);
        int sort = 0;
        for (Map<String, Object> field : fields) {
            lowcodeMapper.insertModelField(IdWorker.getId(), tenantId, id,
                    ServiceContext.requireText(ServiceContext.string(field.get("field_key"))),
                    ServiceContext.requireText(ServiceContext.string(field.get("name"))),
                    ServiceContext.requireText(ServiceContext.string(field.get("data_type"))).toUpperCase(),
                    field.get("length") == null ? null : (int) ServiceContext.requireLong(field.get("length")),
                    field.get("nullable") == null || Boolean.TRUE.equals(field.get("nullable")),
                    Boolean.TRUE.equals(field.get("unique")), Boolean.TRUE.equals(field.get("indexed")),
                    field.get("ref_model_id") == null ? null : ServiceContext.requireLong(field.get("ref_model_id")),
                    ServiceContext.string(field.get("ref_type")), ServiceContext.string(field.get("calc_expr")),
                    sort++, userId);
        }
        audit.low("model.save", "model", id);
        return model(id);
    }

    public Map<String, Object> model(long id) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> model = lowcodeMapper.findModel(tenantId, id);
        if (model == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        model.put("fields", lowcodeMapper.listModelFields(tenantId, id));
        return model;
    }

    public Map<String, Object> modelDiff(long id) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> model = model(id);
        List<Map<String, Object>> fields = lowcodeMapper.listModelFields(tenantId, id);
        return ServiceMaps.of("model_id", id, "version_no", Rows.intVal(model, "version_no"),
                "fields", fields, "breaking", false);
    }

    @Transactional
    public Map<String, Object> savePage(long pageId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = pageId > 0 ? pageId : IdWorker.getId();
        int versionNo = body.get("version_no") == null ? 1
                : (int) ServiceContext.requireLong(body.get("version_no"));
        lowcodeMapper.insertPage(id, tenantId, ServiceContext.requireLong(body.get("app_id")),
                ServiceContext.requireText(ServiceContext.string(body.get("page_key"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("page_type") == null ? "LIST" : ServiceContext.string(body.get("page_type")),
                ServiceMaps.json(body.get("schema_json")), ServiceMaps.json(body.get("data_binding")),
                ServiceMaps.json(body.get("perm")), versionNo,
                body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")), userId);
        return page(id);
    }

    public Map<String, Object> page(long id) {
        Map<String, Object> page = lowcodeMapper.findPage(ServiceContext.tenantId(), id);
        if (page == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(page, "schema_json", "data_binding", "perm_json");
    }

    public Map<String, Object> previewPage(long id, Map<String, Object> body) {
        Map<String, Object> page = page(id);
        return ServiceMaps.of("page", page, "preview_data", body == null ? Map.of() : body);
    }

    @Transactional
    public Map<String, Object> submitRelease(long appId, Map<String, Object> body, String permissions,
                                             String requestId) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> app = app(appId);
        List<Map<String, Object>> forms = lowcodeMapper.listForms(tenantId, appId);
        List<Map<String, Object>> flows = lowcodeMapper.listFlowDefs(tenantId, appId);
        List<Map<String, Object>> models = lowcodeMapper.listModels(tenantId, appId);
        List<Map<String, Object>> pages = lowcodeMapper.listPages(tenantId, appId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("forms", forms);
        snapshot.put("flows", flows);
        snapshot.put("models", models);
        snapshot.put("pages", pages);
        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("app_code", Rows.str(app, "app_code"));
        long id = IdWorker.getId();
        String releaseNo = "REL" + String.format("%010d", commonsMapper.nextReleaseNo());
        lowcodeMapper.insertRelease(id, tenantId, appId, releaseNo,
                Rows.str(app, "env"), body.get("to_env") == null ? "PROD" : ServiceContext.string(body.get("to_env")),
                ServiceMaps.json(snapshot), ServiceContext.string(body.get("release_note")),
                ServiceMaps.json(scan), ServiceContext.userId());
        String instance = workflowClient.startApproval(permissions, requestId, "svcLowcodeAppReleaseApproval",
                String.valueOf(id), "SERVICE_LOWCODE_RELEASE", "应用发布审批：" + releaseNo,
                Map.of("app_id", appId, "release_no", releaseNo));
        audit.high("app.release", "app", appId);
        Map<String, Object> release = release(id);
        release.put("approval_instance_no", instance);
        return release;
    }

    public Map<String, Object> release(long id) {
        Map<String, Object> release = lowcodeMapper.findRelease(ServiceContext.tenantId(), id);
        if (release == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(release, "snapshot_json", "script_scan_json");
    }

    public PageResult<Map<String, Object>> listReleases(Long appId, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = lowcodeMapper.listReleases(tenantId, appId, limit, offset);
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    @Transactional
    public Map<String, Object> approveRelease(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> release = lowcodeMapper.findRelease(tenantId, id);
        if (release == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(Rows.str(release, "approval_status"))) {
            throw new BizException(ServiceErrorCode.STATE_CONFLICT);
        }
        boolean approved = !Boolean.FALSE.equals(body.get("approved"));
        lowcodeMapper.approveRelease(tenantId, id, approved ? "APPROVED" : "REJECTED",
                approved ? "RELEASED" : "FAILED", userId);
        if (approved) {
            lowcodeMapper.updateAppStatus(tenantId, Rows.lngVal(release, "app_id"), "PUBLISHED",
                    Rows.str(release, "to_env"), userId);
        }
        audit.high("app.release.approve", "app_release", id);
        return release(id);
    }

    public Map<String, Object> freezeApp(long appId) {
        int updated = lowcodeMapper.updateAppStatus(ServiceContext.tenantId(), appId, "FROZEN", "PROD",
                ServiceContext.userId());
        if (updated == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        audit.high("app.freeze", "app", appId);
        return app(appId);
    }

    // ---------- 运行时 ----------
    @Transactional
    public Map<String, Object> runtimeSubmit(long flowId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> flow = lowcodeMapper.findFlowDef(tenantId, flowId);
        if (flow == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PUBLISHED".equals(Rows.str(flow, "status"))) {
            throw new BizException(ServiceErrorCode.FLOW_GRAPH_INVALID);
        }
        long instanceId = IdWorker.getId();
        lowcodeMapper.insertFlowInstance(instanceId, tenantId, flowId, Rows.str(flow, "flow_key"),
                ServiceContext.string(body.get("business_key")), ServiceContext.string(body.get("title")),
                Rows.lng(flow, "app_id"), "FIRST_NODE", null, userId);
        long taskId = IdWorker.getId();
        lowcodeMapper.insertTask(taskId, tenantId, instanceId, "first_approve", "首节点审批", "APPROVE",
                body.get("approver_id") == null ? userId : ServiceContext.requireLong(body.get("approver_id")),
                ServiceMaps.json(body.get("candidates")), userId);
        return ServiceMaps.of("instance_id", instanceId, "task_id", taskId, "status", "RUNNING");
    }

    @Transactional
    public Map<String, Object> completeTask(long instanceId, long taskId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> task = lowcodeMapper.findTask(tenantId, taskId);
        if (task == null || !"PENDING".equals(Rows.str(task, "status"))) {
            throw new BizException(ServiceErrorCode.TASK_ACTION_INVALID);
        }
        boolean approved = !Boolean.FALSE.equals(body.get("approved"));
        lowcodeMapper.completeTask(tenantId, taskId, approved ? "APPROVED" : "REJECTED",
                approved ? "APPROVE" : "REJECT", ServiceContext.string(body.get("comment")), userId);
        Long nextAssignee = body.get("next_assignee_id") == null ? null
                : ServiceContext.requireLong(body.get("next_assignee_id"));
        if (approved && nextAssignee != null) {
            lowcodeMapper.insertTask(IdWorker.getId(), tenantId, instanceId, "next_approve", "下一节点审批",
                    "APPROVE", nextAssignee, "{}", userId);
            lowcodeMapper.updateInstance(tenantId, instanceId, "RUNNING", "next_approve", userId);
            return ServiceMaps.of("instance_id", instanceId, "status", "RUNNING");
        }
        lowcodeMapper.updateInstance(tenantId, instanceId, approved ? "COMPLETED" : "TERMINATED",
                approved ? "END" : "REJECTED", userId);
        return ServiceMaps.of("instance_id", instanceId, "status", approved ? "COMPLETED" : "TERMINATED");
    }

    public PageResult<Map<String, Object>> runtimeTasks(Long assigneeId, String status, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = lowcodeMapper.listTasks(tenantId,
                assigneeId == null ? ServiceContext.userId() : assigneeId, blank(status), limit, offset);
        return PageResult.of(ServiceMaps.parseAll(rows, "candidate_json"), rows.size(),
                ServiceContext.page(page), limit);
    }

    public List<Map<String, Object>> instances(String status) {
        return lowcodeMapper.listFlowInstances(ServiceContext.tenantId(), blank(status), 200, 0);
    }

    // ---------- 脚本与权限 ----------
    @Transactional
    public Map<String, Object> saveScript(long appId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        String content = ServiceContext.requireText(ServiceContext.string(body.get("content")));
        String risk = scanRisk(content);
        long id = IdWorker.getId();
        lowcodeMapper.insertScript(id, tenantId, appId,
                ServiceContext.requireText(ServiceContext.string(body.get("script_key"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("language") == null ? "JS" : ServiceContext.string(body.get("language")),
                content, body.get("version_no") == null ? 1
                        : (int) ServiceContext.requireLong(body.get("version_no")),
                risk, ServiceMaps.json(Map.of("risk_level", risk, "hits", scanHits(content))),
                body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")),
                ServiceContext.userId());
        audit.high("script.save", "script", id);
        return script(id);
    }

    public Map<String, Object> script(long id) {
        Map<String, Object> script = lowcodeMapper.findScript(ServiceContext.tenantId(), id);
        if (script == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(script, "scan_result");
    }

    public Map<String, Object> scanScript(long id) {
        Map<String, Object> script = script(id);
        String risk = scanRisk(Rows.str(script, "content"));
        if ("HIGH".equals(risk)) {
            throw new BizException(ServiceErrorCode.SCRIPT_SCAN_BLOCKED);
        }
        return ServiceMaps.of("risk_level", risk, "hits", scanHits(Rows.str(script, "content")));
    }

    public Map<String, Object> savePermission(long appId, Map<String, Object> body, String type) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        configMapper.upsertConfig(IdWorker.getId(), tenantId, type, String.valueOf(appId),
                ServiceMaps.json(body), "ACTIVE", userId);
        return ServiceMaps.of("app_id", appId, "config_type", type, "saved", true);
    }

    public List<Map<String, Object>> templates() {
        return List.of(
                ServiceMaps.of("code", "IT_HELP", "name", "IT 服务台", "description", "事件/请求/SLA/知识库"),
                ServiceMaps.of("code", "ASSET_MGMT", "name", "资产管理", "description", "资产台账/借用/提醒"),
                ServiceMaps.of("code", "APPROVAL", "name", "通用审批", "description", "表单+流程+发布"));
    }

    // ---------- 兼容性 / 页面数据源 / 运行时按编码寻址 ----------
    public Map<String, Object> compatCheck(long id, Map<String, Object> body) {
        Map<String, Object> model = model(id);
        List<Map<String, Object>> oldFields = lowcodeMapper.listModelFields(ServiceContext.tenantId(), id);
        List<Map<String, Object>> newFields = fieldList(body.get("fields"));
        boolean broken = incompatible(oldFields, newFields);
        return ServiceMaps.of("model_id", id, "version_no", Rows.intVal(model, "version_no"),
                "compatible", !broken, "breaking", broken);
    }

    public Map<String, Object> updatePageDatasource(long id, Map<String, Object> body) {
        Object binding = body == null ? null : body.get("data_binding");
        lowcodeMapper.updatePageDatasource(ServiceContext.tenantId(), id,
                ServiceMaps.json(binding == null ? body : binding), ServiceContext.userId());
        return page(id);
    }

    public Map<String, Object> runtimePageSubmit(String appCode, String pageKey, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> app = lowcodeMapper.findAppByCode(tenantId, appCode);
        if (app == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> page = lowcodeMapper.findPageByKey(tenantId, Rows.lngVal(app, "id"), pageKey);
        if (page == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PUBLISHED".equals(Rows.str(app, "status"))) {
            throw new BizException(ServiceErrorCode.APP_STATE_INVALID);
        }
        audit.low("app.runtime.submit", "app", Rows.lng(app, "id"));
        return ServiceMaps.of("app_code", appCode, "page_key", pageKey, "submitted", true,
                "data", body == null ? Map.of() : body);
    }

    public Map<String, Object> runtimeStartFlow(String appCode, String flowKey, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> app = lowcodeMapper.findAppByCode(tenantId, appCode);
        if (app == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> flow = lowcodeMapper.findFlowByKey(tenantId, Rows.lngVal(app, "id"), flowKey);
        if (flow == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return runtimeSubmit(Rows.lngVal(flow, "id"), body);
    }

    // ---------- 校验 ----------
    private void validateFields(List<Map<String, Object>> fields) {
        if (fields.isEmpty()) {
            throw new BizException(ServiceErrorCode.FORM_FIELD_INVALID);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            String key = ServiceContext.string(field.get("field_key"));
            String type = ServiceContext.string(field.get("field_type"));
            if (key == null || type == null || !FIELD_TYPES.contains(type.toUpperCase())) {
                throw new BizException(ServiceErrorCode.FORM_FIELD_INVALID);
            }
            if (!keys.add(key)) {
                throw new BizException(ServiceErrorCode.FORM_FIELD_INVALID);
            }
            if (("SELECT".equalsIgnoreCase(type) || "MULTI".equalsIgnoreCase(type))
                    && (field.get("options") == null || String.valueOf(field.get("options")).equals("[]"))) {
                throw new BizException(ServiceErrorCode.FORM_FIELD_INVALID);
            }
        }
    }

    private void validateFlowGraph(Object bpmn) {
        JsonNode root = ServiceMaps.node(bpmn);
        JsonNode nodes = root.path("nodes");
        JsonNode edges = root.path("edges");
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new BizException(ServiceErrorCode.FLOW_GRAPH_INVALID);
        }
        Set<String> keys = new LinkedHashSet<>();
        boolean hasEnd = false;
        for (JsonNode node : nodes) {
            String key = node.path("key").asText(null);
            if (key == null) {
                throw new BizException(ServiceErrorCode.FLOW_GRAPH_INVALID);
            }
            keys.add(key);
            if ("end".equalsIgnoreCase(node.path("type").asText())) {
                hasEnd = true;
            }
        }
        if (!hasEnd) {
            throw new BizException(ServiceErrorCode.FLOW_GRAPH_INVALID);
        }
        if (edges.isArray()) {
            for (JsonNode edge : edges) {
                if (!keys.contains(edge.path("from").asText()) || !keys.contains(edge.path("to").asText())) {
                    throw new BizException(ServiceErrorCode.FLOW_GRAPH_INVALID);
                }
            }
        }
    }

    private boolean incompatible(List<Map<String, Object>> oldFields, List<Map<String, Object>> newFields) {
        Map<String, Map<String, Object>> incoming = new LinkedHashMap<>();
        for (Map<String, Object> field : newFields) {
            incoming.put(ServiceContext.string(field.get("field_key")), field);
        }
        for (Map<String, Object> oldField : oldFields) {
            String key = Rows.str(oldField, "field_key");
            Map<String, Object> next = incoming.get(key);
            if (next == null) {
                return true;
            }
            String oldType = Rows.str(oldField, "data_type");
            String newType = ServiceContext.string(next.get("data_type"));
            if (oldType != null && !oldType.equalsIgnoreCase(newType)) {
                return true;
            }
            boolean oldNullable = Rows.bool(oldField, "nullable");
            boolean newNullable = next.get("nullable") == null || Boolean.TRUE.equals(next.get("nullable"));
            if (!oldNullable && newNullable) {
                return true;
            }
        }
        return false;
    }

    private String scanRisk(String content) {
        if (content == null) {
            return "LOW";
        }
        return scanHits(content).isEmpty() ? "LOW" : "HIGH";
    }

    private List<String> scanHits(String content) {
        List<String> hits = new ArrayList<>();
        if (content == null) {
            return hits;
        }
        for (String keyword : HIGH_RISK) {
            if (content.contains(keyword)) {
                hits.add(keyword);
            }
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object each : list) {
            if (each instanceof Map<?, ?> map) {
                fields.add((Map<String, Object>) map);
            }
        }
        return fields;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
