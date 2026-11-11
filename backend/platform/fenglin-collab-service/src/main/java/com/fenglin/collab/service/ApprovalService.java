package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.client.WorkflowClient;
import com.fenglin.collab.mapper.ApprovalMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.AddSignRequest;
import com.fenglin.collab.web.dto.CollabDtos.CcView;
import com.fenglin.collab.web.dto.CollabDtos.InstanceCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.InstanceView;
import com.fenglin.collab.web.dto.CollabDtos.TaskResult;
import com.fenglin.collab.web.dto.CollabDtos.TaskView;
import com.fenglin.collab.web.dto.CollabDtos.TemplateCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.TemplateUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.TemplateView;
import com.fenglin.collab.web.dto.CollabDtos.TransferRequest;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 审批主链：模板、发起（幂等）、我的待办、同意/驳回/转办/加签/撤销。
 *
 * <p>流程边界：工作流中心负责流程实例登记与流程编排留痕；本地任务表承载首版单节点/并签流转。
 * 复杂会签、条件分支与跨系统回调在收口阶段按 BPMN 扩展（已内置最小 BPMN）。</p>
 */
@Service
public class ApprovalService {

    private static final String NODE_KEY = "dept_leader_approve";
    private static final String NODE_NAME = "部门主管审批";

    private final ApprovalMapper approvalMapper;
    private final WorkflowClient workflowClient;
    private final WorkbenchService workbenchService;
    private final CollabCrypto crypto;
    private final CollabAudit audit;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalMapper approvalMapper, WorkflowClient workflowClient,
                           WorkbenchService workbenchService, CollabCrypto crypto, CollabAudit audit,
                           ObjectMapper objectMapper) {
        this.approvalMapper = approvalMapper;
        this.workflowClient = workflowClient;
        this.workbenchService = workbenchService;
        this.crypto = crypto;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TemplateView createTemplate(TemplateCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.code() == null || request.name() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "模板编码与名称不能为空");
        }
        Map<String, Object> existing = approvalMapper.findTemplateByCode(tenantId, request.code());
        if (existing != null) {
            return toTemplateView(existing);
        }
        String templateId = UUID.randomUUID().toString();
        String schema = "{}";
        if (request.formSchema() != null) {
            try {
                schema = objectMapper.writeValueAsString(request.formSchema());
            } catch (JsonProcessingException ex) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "表单结构非法");
            }
        }
        String deptScope = toDeptScope(request.deptScope());
        boolean enabled = request.enabled() == null || request.enabled();
        approvalMapper.insertTemplate(templateId, tenantId, request.code(), request.name(),
                request.category() == null ? "GENERAL" : request.category(), schema,
                request.processKey() == null ? "collab_approval" : request.processKey(),
                enabled ? 1 : 0, deptScope, userId);
        audit.publish("COLLAB_APPROVAL_TEMPLATE_CREATED", "CREATE", "collab_approval_template", templateId, "LOW");
        return new TemplateView(templateId, request.code(), request.name(),
                request.category() == null ? "GENERAL" : request.category(),
                request.processKey() == null ? "collab_approval" : request.processKey(), enabled,
                request.formSchema());
    }

    /** COL-01b：管理端模板列表（含已停用）。只读事务满足 RLS 拦截器要求。 */
    @Transactional(readOnly = true)
    public List<TemplateView> adminTemplates() {
        return approvalMapper.listAdminTemplates(CollabContext.tenantId()).stream()
                .map(this::toTemplateView)
                .toList();
    }

    /** COL-01b：编辑模板。code 不可修改，未传字段沿用原值。 */
    @Transactional
    public TemplateView updateTemplate(String templateId, TemplateUpdateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> current = approvalMapper.findTemplate(tenantId, templateId);
        if (current == null) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        String name = request == null || request.name() == null || request.name().isBlank()
                ? Rows.str(current, "name") : request.name().trim();
        String category = request == null || request.category() == null || request.category().isBlank()
                ? Rows.str(current, "category") : request.category().trim();
        String processKey = request == null || request.processKey() == null || request.processKey().isBlank()
                ? Rows.str(current, "processKey") : request.processKey().trim();
        int enabled = request == null || request.enabled() == null
                ? Rows.intVal(current, "enabled") : (request.enabled() ? 1 : 0);
        String schema;
        Map<String, Object> schemaView;
        if (request != null && request.formSchema() != null) {
            try {
                schema = objectMapper.writeValueAsString(request.formSchema());
            } catch (JsonProcessingException ex) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "表单结构非法");
            }
            schemaView = request.formSchema();
        } else {
            schema = Rows.str(current, "formSchema") == null ? "{}" : Rows.str(current, "formSchema");
            schemaView = parseSchema(schema);
        }
        String deptScope = request == null ? null : toDeptScope(request.deptScope());
        if (approvalMapper.updateTemplate(tenantId, templateId, name, category, schema, processKey,
                enabled, deptScope, userId) == 0) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        audit.publish("COLLAB_APPROVAL_TEMPLATE_UPDATED", "UPDATE", "collab_approval_template", templateId, "LOW");
        return new TemplateView(templateId, Rows.str(current, "code"), name, category, processKey,
                enabled == 1, schemaView);
    }

    /** COL-01b：启用/停用模板。 */
    @Transactional
    public TemplateView setTemplateEnabled(String templateId, boolean enabled) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (approvalMapper.setTemplateEnabled(tenantId, templateId, enabled ? 1 : 0, userId) == 0) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        audit.publish("COLLAB_APPROVAL_TEMPLATE_STATUS", "UPDATE", "collab_approval_template", templateId, "LOW");
        return toTemplateView(approvalMapper.findTemplate(tenantId, templateId));
    }

    /** COL-01b：软删除模板；存在运行中实例时拒绝，避免在途审批失去模板。 */
    @Transactional
    public void deleteTemplate(String templateId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (approvalMapper.findTemplate(tenantId, templateId) == null) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        if (approvalMapper.countRunningInstances(tenantId, templateId) > 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "该模板仍有运行中的审批实例，请先撤销或停用");
        }
        if (approvalMapper.softDeleteTemplate(tenantId, templateId, userId) == 0) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        audit.publish("COLLAB_APPROVAL_TEMPLATE_DELETED", "DELETE", "collab_approval_template", templateId, "MEDIUM");
    }

    private TemplateView toTemplateView(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new TemplateView(Rows.str(row, "templateId"), Rows.str(row, "code"), Rows.str(row, "name"),
                Rows.str(row, "category"), Rows.str(row, "processKey"), Rows.intVal(row, "enabled") == 1,
                parseSchema(Rows.str(row, "formSchema")));
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /**
     * COL-01a：按模板 form_schema 校验动态表单。
     * 约定结构：{"fields":[{"key":"amount","label":"金额","type":"number","required":true}, ...]}；
     * 未定义 fields 的旧模板按无约束处理。
     */
    private void validateForm(Map<String, Object> template, Map<String, Object> formData) {
        Map<String, Object> schema = parseSchema(Rows.str(template, "formSchema"));
        Object rawFields = schema.get("fields");
        if (!(rawFields instanceof List<?> fields)) {
            return;
        }
        for (Object item : fields) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            Object rawKey = field.get("key");
            if (rawKey == null || String.valueOf(rawKey).isBlank()) {
                continue;
            }
            String name = String.valueOf(rawKey);
            Object label = field.get("label");
            String display = label == null ? name : String.valueOf(label);
            Object value = formData.get(name);
            boolean blank = value == null || (value instanceof String text && text.isBlank());
            if (Boolean.TRUE.equals(field.get("required")) && blank) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "缺少必填项：" + display);
            }
            if (!blank && "number".equals(String.valueOf(field.get("type")))) {
                try {
                    new java.math.BigDecimal(String.valueOf(value).trim());
                } catch (NumberFormatException ex) {
                    throw new BizException(CollabErrorCode.PARAM_INVALID, "字段应为数字：" + display);
                }
            }
        }
    }

    private String toDeptScope(List<Long> deptScope) {
        if (deptScope == null || deptScope.isEmpty()) {
            return null;
        }
        return "{" + deptScope.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "}";
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        long tenantId = CollabContext.tenantId();
        return approvalMapper.listTemplates(tenantId).stream()
                .map(this::toTemplateView)
                .toList();
    }

    @Transactional
    public InstanceView submit(InstanceCreateRequest request, String permissions) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.templateId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "审批模板不能为空");
        }
        Map<String, Object> template = approvalMapper.findTemplate(tenantId, request.templateId());
        if (template == null || Rows.intVal(template, "enabled") != 1) {
            throw new BizException(CollabErrorCode.APV_TEMPLATE_NOT_FOUND);
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Map<String, Object> existing = approvalMapper.findByIdempotencyKey(tenantId, request.idempotencyKey());
            if (existing != null) {
                return detail(Rows.str(existing, "instanceId"));
            }
        }
        Map<String, Object> formData = request.formData() == null ? Map.of() : request.formData();
        validateForm(template, formData);
        String formJson;
        try {
            formJson = objectMapper.writeValueAsString(formData);
        } catch (JsonProcessingException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "表单数据非法");
        }
        String instanceId = UUID.randomUUID().toString();
        String instanceNo = approvalMapper.nextInstanceNo();
        String processKey = Rows.str(template, "processKey");
        String wfInstanceId = workflowClient.startApproval(permissions, null, processKey,
                "COLLAB_APPROVAL:" + instanceId, "COLLAB_APPROVAL",
                Rows.str(template, "name") + " " + instanceNo, formData);
        approvalMapper.insertInstance(instanceId, tenantId, request.templateId(), instanceNo, userId, formJson,
                crypto.encrypt(tenantId, "collab_approval_instance", "form_data", formJson),
                wfInstanceId, NODE_KEY, request.idempotencyKey());
        long approverId = request.approverId() == null || request.approverId() <= 0 ? userId : request.approverId();
        approvalMapper.insertTask(IdWorker.getId(), tenantId, instanceId, NODE_KEY, NODE_NAME, approverId,
                "USER", userId);
        insertCcUsers(tenantId, instanceId, instanceNo, template, request.ccUserIds(), userId, approverId);
        workbenchService.notifyUser(tenantId, approverId, "APPROVAL", "新的审批待办",
                Rows.str(template, "name") + " " + instanceNo, "COLLAB_APPROVAL", instanceId, userId);
        audit.publish("COLLAB_APPROVAL_SUBMITTED", "CREATE", "collab_approval_instance", instanceId, "LOW");
        return detail(instanceId);
    }

    /** COL-01c：写入抄送人（去重，排除发起人与审批人本人），并各发一条站内通知。 */
    private void insertCcUsers(long tenantId, String instanceId, String instanceNo, Map<String, Object> template,
                               List<Long> ccUserIds, long initiatorId, long approverId) {
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return;
        }
        Set<Long> seen = new java.util.LinkedHashSet<>();
        for (Long ccUserId : ccUserIds) {
            if (ccUserId == null || ccUserId <= 0 || ccUserId == initiatorId || ccUserId == approverId) {
                continue;
            }
            if (!seen.add(ccUserId)) {
                continue;
            }
            approvalMapper.insertCc(IdWorker.getId(), tenantId, instanceId, ccUserId, initiatorId);
            workbenchService.notifyUser(tenantId, ccUserId, "APPROVAL", "审批抄送",
                    Rows.str(template, "name") + " " + instanceNo, "COLLAB_APPROVAL", instanceId, initiatorId);
        }
    }

    @Transactional(readOnly = true)
    public InstanceView detail(String instanceId) {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> instance = approvalMapper.findInstance(tenantId, instanceId);
        if (instance == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "审批实例不存在");
        }
        List<TaskView> tasks = approvalMapper.listTasksByInstance(tenantId, instanceId).stream()
                .map(row -> new TaskView(Rows.lngVal(row, "taskId"), Rows.str(row, "instanceId"),
                        Rows.str(row, "nodeKey"), Rows.str(row, "nodeName"), Rows.lng(row, "assigneeId"),
                        Rows.str(row, "status"), Rows.str(row, "action"), Rows.instant(row, "handledAt")))
                .toList();
        return new InstanceView(instanceId, Rows.str(instance, "instanceNo"), Rows.str(instance, "templateId"),
                Rows.lngVal(instance, "initiatorId"), parseFormData(Rows.str(instance, "formData")),
                Rows.str(instance, "status"), Rows.str(instance, "currentNode"),
                Rows.instant(instance, "createdAt"), tasks);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myTasks() {
        long tenantId = CollabContext.tenantId();
        return approvalMapper.listMyTasks(tenantId, CollabContext.userId()).stream()
                .map(row -> {
                    // task_id 是雪花 ID，前端 JSON 按 number 解析会被舍入，办理时命中“任务不存在”，统一转字符串
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    Object taskId = copy.get("taskId");
                    if (taskId != null) {
                        copy.put("taskId", String.valueOf(taskId));
                    }
                    return copy;
                })
                .toList();
    }

    /** COL-01：我的已办（我处理过且已完成的任务），按办理时间倒序，附审批意见明文。 */
    @Transactional(readOnly = true)
    public Map<String, Object> doneTasks(String action, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        String normalized = action == null || action.isBlank() ? null : action.toUpperCase();
        List<Map<String, Object>> records = approvalMapper
                .listDoneTasks(tenantId, userId, normalized, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    Object taskId = copy.get("taskId");
                    if (taskId != null) {
                        copy.put("taskId", String.valueOf(taskId));
                    }
                    copy.put("comment", crypto.decrypt(tenantId, "collab_approval_task", "comment",
                            Rows.str(row, "commentCipher")));
                    copy.remove("commentCipher");
                    return copy;
                })
                .toList();
        return Map.of("records", records, "total", approvalMapper.countDoneTasks(tenantId, userId, normalized),
                "page", safePage, "size", safeSize);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myInstances(Integer page, Integer size) {
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        return approvalMapper.listMyInstances(CollabContext.tenantId(), CollabContext.userId(),
                (safePage - 1) * safeSize, safeSize);
    }

    /** COL-01c：抄送我的列表。 */
    @Transactional(readOnly = true)
    public Map<String, Object> myCc(Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<CcView> records = approvalMapper.listMyCc(tenantId, userId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new CcView(Rows.lngVal(row, "ccId"), Rows.str(row, "instanceId"),
                        Rows.str(row, "instanceNo"), Rows.str(row, "instanceStatus"),
                        Rows.lngVal(row, "initiatorId"), Rows.str(row, "templateName"),
                        parseFormData(Rows.str(row, "formData")), Rows.instant(row, "readAt"),
                        Rows.instant(row, "createdAt")))
                .toList();
        return Map.of("records", records, "total", approvalMapper.countMyCc(tenantId, userId),
                "page", safePage, "size", safeSize);
    }

    /** COL-01c：抄送人标记已读，仅本人记录可标记。 */
    @Transactional
    public void markCcRead(long ccId) {
        if (approvalMapper.markCcRead(CollabContext.tenantId(), ccId, CollabContext.userId()) == 0) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "抄送记录不存在");
        }
        audit.publish("COLLAB_APPROVAL_CC_READ", "UPDATE", "collab_approval_cc", ccId, "LOW");
    }

    @Transactional
    public TaskResult approve(long taskId, String comment) {
        return handle(taskId, "APPROVE", comment);
    }

    @Transactional
    public TaskResult reject(long taskId, String comment) {
        return handle(taskId, "REJECT", comment);
    }

    @Transactional
    public void transfer(long taskId, TransferRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> task = requirePendingTask(tenantId, taskId);
        requireAssignee(task, userId);
        if (request == null || request.assigneeId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "转办人不能为空");
        }
        if (approvalMapper.updateTaskAssignee(tenantId, taskId, request.assigneeId(), userId) == 0) {
            throw new BizException(CollabErrorCode.APV_TASK_HANDLED);
        }
        approvalMapper.updateTaskHandled(tenantId, taskId, "TRANSFER", "PENDING",
                crypto.encrypt(tenantId, "collab_approval_task", "comment", request.reason()), userId);
        workbenchService.notifyUser(tenantId, request.assigneeId(), "APPROVAL", "转办审批待办",
                "有一条审批转办给你处理", "COLLAB_APPROVAL", Rows.str(task, "instanceId"), userId);
        audit.publish("COLLAB_APPROVAL_TRANSFERRED", "UPDATE", "collab_approval_task", taskId, "MEDIUM");
    }

    @Transactional
    public void addSign(long taskId, AddSignRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> task = requirePendingTask(tenantId, taskId);
        requireAssignee(task, userId);
        if (request == null || request.assigneeId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "加签人不能为空");
        }
        approvalMapper.insertTask(IdWorker.getId(), tenantId, Rows.str(task, "instanceId"),
                Rows.str(task, "nodeKey"), Rows.str(task, "nodeName"), request.assigneeId(), "USER", userId);
        workbenchService.notifyUser(tenantId, request.assigneeId(), "APPROVAL", "加签审批待办",
                "有一条审批加签给你处理", "COLLAB_APPROVAL", Rows.str(task, "instanceId"), userId);
        audit.publish("COLLAB_APPROVAL_ADD_SIGNED", "UPDATE", "collab_approval_task", taskId, "MEDIUM");
    }

    @Transactional
    public void cancel(String instanceId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> instance = approvalMapper.findInstance(tenantId, instanceId);
        if (instance == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "审批实例不存在");
        }
        if (Rows.lngVal(instance, "initiatorId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅发起人可撤销审批");
        }
        if (!"RUNNING".equals(Rows.str(instance, "status"))) {
            throw new BizException(CollabErrorCode.APV_OPERATION_UNSUPPORTED, "仅进行中的审批可撤销");
        }
        approvalMapper.skipPendingTasks(tenantId, instanceId, userId);
        approvalMapper.updateInstanceStatus(tenantId, instanceId, "CANCELLED", null, null, userId);
        audit.publish("COLLAB_APPROVAL_CANCELLED", "UPDATE", "collab_approval_instance", instanceId, "MEDIUM");
    }

    private TaskResult handle(long taskId, String action, String comment) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> task = requirePendingTask(tenantId, taskId);
        requireAssignee(task, userId);
        String instanceId = Rows.str(task, "instanceId");
        String encryptedComment = crypto.encrypt(tenantId, "collab_approval_task", "comment", comment);
        if (approvalMapper.updateTaskHandled(tenantId, taskId, action, "DONE", encryptedComment, userId) == 0) {
            throw new BizException(CollabErrorCode.APV_TASK_HANDLED);
        }
        String instanceStatus;
        if ("REJECT".equals(action)) {
            approvalMapper.skipPendingTasks(tenantId, instanceId, userId);
            approvalMapper.updateInstanceStatus(tenantId, instanceId, "REJECTED", null,
                    OffsetDateTime.now(ZoneOffset.UTC), userId);
            instanceStatus = "REJECTED";
        } else if (approvalMapper.countPendingTasks(tenantId, instanceId) == 0) {
            approvalMapper.updateInstanceStatus(tenantId, instanceId, "APPROVED", null,
                    OffsetDateTime.now(ZoneOffset.UTC), userId);
            instanceStatus = "APPROVED";
        } else {
            instanceStatus = "RUNNING";
        }
        notifyInitiator(tenantId, instanceId, instanceStatus, userId);
        audit.publish("COLLAB_APPROVAL_" + action, "UPDATE", "collab_approval_task", taskId, "MEDIUM");
        return new TaskResult("DONE", instanceStatus);
    }

    private void notifyInitiator(long tenantId, String instanceId, String status, long userId) {
        Map<String, Object> instance = approvalMapper.findInstance(tenantId, instanceId);
        if (instance == null) {
            return;
        }
        long initiatorId = Rows.lngVal(instance, "initiatorId");
        if (initiatorId == userId) {
            return;
        }
        workbenchService.notifyUser(tenantId, initiatorId, "APPROVAL", "审批状态更新",
                "审批单 " + Rows.str(instance, "instanceNo") + " 状态：" + status,
                "COLLAB_APPROVAL", instanceId, userId);
    }

    private Map<String, Object> requirePendingTask(long tenantId, long taskId) {
        Map<String, Object> task = approvalMapper.findTask(tenantId, taskId);
        if (task == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "审批任务不存在");
        }
        if (!"PENDING".equals(Rows.str(task, "status"))) {
            throw new BizException(CollabErrorCode.APV_TASK_HANDLED);
        }
        return task;
    }

    private void requireAssignee(Map<String, Object> task, long userId) {
        Long assigneeId = Rows.lng(task, "assigneeId");
        if (assigneeId == null || assigneeId != userId) {
            throw new BizException(CollabErrorCode.APV_NOT_ASSIGNEE);
        }
    }

    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
