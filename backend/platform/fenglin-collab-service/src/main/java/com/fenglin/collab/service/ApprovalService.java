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
import com.fenglin.collab.web.dto.CollabDtos.InstanceCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.InstanceView;
import com.fenglin.collab.web.dto.CollabDtos.TaskResult;
import com.fenglin.collab.web.dto.CollabDtos.TaskView;
import com.fenglin.collab.web.dto.CollabDtos.TemplateCreateRequest;
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
        if (approvalMapper.findTemplateByCode(tenantId, request.code()) != null) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "审批模板编码已存在");
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
        String deptScope = request.deptScope() == null || request.deptScope().isEmpty() ? null
                : "{" + request.deptScope().stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("") + "}";
        approvalMapper.insertTemplate(templateId, tenantId, request.code(), request.name(),
                request.category() == null ? "GENERAL" : request.category(), schema,
                request.processKey() == null ? "collab_approval" : request.processKey(),
                request.enabled() == null || request.enabled() ? 1 : 0, deptScope, userId);
        audit.publish("COLLAB_APPROVAL_TEMPLATE_CREATED", "CREATE", "collab_approval_template", templateId, "LOW");
        return new TemplateView(templateId, request.code(), request.name(),
                request.category() == null ? "GENERAL" : request.category(),
                request.processKey() == null ? "collab_approval" : request.processKey(), true);
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        long tenantId = CollabContext.tenantId();
        return approvalMapper.listTemplates(tenantId).stream()
                .map(row -> new TemplateView(Rows.str(row, "templateId"), Rows.str(row, "code"),
                        Rows.str(row, "name"), Rows.str(row, "category"), Rows.str(row, "processKey"),
                        Rows.intVal(row, "enabled") == 1))
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
        workbenchService.notifyUser(tenantId, approverId, "APPROVAL", "新的审批待办",
                Rows.str(template, "name") + " " + instanceNo, "COLLAB_APPROVAL", instanceId, userId);
        audit.publish("COLLAB_APPROVAL_SUBMITTED", "CREATE", "collab_approval_instance", instanceId, "LOW");
        return detail(instanceId);
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
        return approvalMapper.listMyTasks(tenantId, CollabContext.userId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myInstances(Integer page, Integer size) {
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        return approvalMapper.listMyInstances(CollabContext.tenantId(), CollabContext.userId(),
                (safePage - 1) * safeSize, safeSize);
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
