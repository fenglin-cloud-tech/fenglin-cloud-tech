package com.fenglin.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.workflow.mapper.WorkflowInstanceMapper;
import com.fenglin.workflow.mapper.WorkflowTaskMapper;
import com.fenglin.workflow.web.dto.HandleTaskRequest;
import com.fenglin.workflow.web.dto.HandleTaskResult;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowTaskService {

    private static final Set<String> ACTIONS = Set.of("APPROVE", "REJECT", "TRANSFER", "ADD_SIGN", "DELEGATE");

    private final WorkflowTaskMapper taskMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowAuditor auditor;
    private final WorkflowInstanceService instanceService;

    public WorkflowTaskService(WorkflowTaskMapper taskMapper,
                               WorkflowInstanceMapper instanceMapper,
                               TaskService taskService,
                               RuntimeService runtimeService,
                               ApplicationEventPublisher eventPublisher,
                               WorkflowAuditor auditor,
                               WorkflowInstanceService instanceService) {
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.eventPublisher = eventPublisher;
        this.auditor = auditor;
        this.instanceService = instanceService;
    }

    @Transactional
    public HandleTaskResult handle(long taskId, HandleTaskRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Long userId = currentUserId();
        if (userId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "缺少用户上下文");
        }
        String action = request == null || request.action() == null ? "" : request.action().toUpperCase();
        if (!ACTIONS.contains(action)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "action 仅支持 APPROVE/REJECT/TRANSFER/ADD_SIGN/DELEGATE");
        }
        Map<String, Object> taskRow = taskMapper.findById(tenantId, taskId);
        if (taskRow == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        if (!"PENDING".equals(taskRow.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 任务已被处理: " + taskId);
        }
        long instanceId = ((Number) taskRow.get("instance_id")).longValue();
        Map<String, Object> instance = instanceMapper.findById(tenantId, instanceId);
        if (instance == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        if (!"RUNNING".equals(instance.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 流程实例已结束，任务不可办理");
        }
        String flowableTaskId = (String) taskRow.get("flowable_task_id");
        Task flowableTask;
        try {
            flowableTask = taskService.createTaskQuery().taskId(flowableTaskId)
                    .taskTenantId(String.valueOf(tenantId)).singleResult();
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 任务已被处理: " + taskId);
        }
        if (flowableTask == null) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 任务已被处理: " + taskId);
        }
        if (!canHandle(flowableTask, userId)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前用户无权办理该任务");
        }
        String comment = request.comment();
        String instanceNo = (String) instance.get("instance_no");
        String flowableInstanceId = (String) instance.get("flowable_instance_id");

        return switch (action) {
            case "APPROVE" -> approve(tenantId, userId, taskId, flowableTask, comment, instanceId,
                    instanceNo, flowableInstanceId);
            case "REJECT" -> reject(tenantId, userId, taskId, flowableTask, comment, instanceId,
                    instanceNo, flowableInstanceId);
            case "TRANSFER" -> transfer(tenantId, userId, taskId, flowableTask, comment, request.assigneeId());
            case "ADD_SIGN" -> addSign(tenantId, userId, taskId, flowableTask, comment, request.assigneeId());
            case "DELEGATE" -> delegate(tenantId, userId, taskId, flowableTask, comment, request.assigneeId());
            default -> throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的办理动作: " + action);
        };
    }

    private HandleTaskResult approve(long tenantId, long userId, long taskId, Task flowableTask, String comment,
                                     long instanceId, String instanceNo, String flowableInstanceId) {
        try {
            addComment(flowableTask, flowableInstanceId, comment);
            taskService.complete(flowableTask.getId(), Map.of("wfAction", "APPROVE"));
        } catch (FlowableObjectNotFoundException ex) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 任务已被处理: " + taskId);
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "任务办理失败: " + rootMessage(ex));
        }
        taskMapper.markDone(tenantId, taskId, "APPROVE", comment, userId);
        List<Long> nextIds = instanceService.syncActiveTasks(tenantId, instanceId, flowableInstanceId, userId);
        auditor.publish(tenantId, "WF_TASK_APPROVED", "WORKFLOW_TASK", String.valueOf(taskId), "MEDIUM");
        boolean ended = !instanceRunning(flowableInstanceId);
        if (ended) {
            instanceMapper.markCompleted(tenantId, instanceId, "APPROVED", userId);
            auditor.publish(tenantId, "WF_INSTANCE_COMPLETED", "WORKFLOW_INSTANCE", instanceNo, "MEDIUM");
            eventPublisher.publishEvent(new WorkflowStateChangedEvent(tenantId, instanceId, "PROCESS_COMPLETED"));
        }
        return new HandleTaskResult(taskId, "DONE", ended ? "COMPLETED" : "RUNNING",
                nextIds.stream().map(String::valueOf).toList());
    }

    private HandleTaskResult reject(long tenantId, long userId, long taskId, Task flowableTask, String comment,
                                    long instanceId, String instanceNo, String flowableInstanceId) {
        try {
            addComment(flowableTask, flowableInstanceId, comment);
            if (instanceRunning(flowableInstanceId)) {
                runtimeService.deleteProcessInstance(flowableInstanceId,
                        "REJECTED:" + (comment == null ? "" : comment));
            }
        } catch (FlowableObjectNotFoundException ex) {
            throw new BizException(CommonErrorCode.CONFLICT, "0004002 任务已被处理: " + taskId);
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "任务驳回失败: " + rootMessage(ex));
        }
        taskMapper.markDone(tenantId, taskId, "REJECT", comment, userId);
        taskMapper.cancelPendingByInstance(tenantId, instanceId, userId);
        instanceMapper.markCompleted(tenantId, instanceId, "REJECTED", userId);
        auditor.publish(tenantId, "WF_TASK_REJECTED", "WORKFLOW_TASK", String.valueOf(taskId), "HIGH");
        auditor.publish(tenantId, "WF_INSTANCE_COMPLETED", "WORKFLOW_INSTANCE", instanceNo, "HIGH");
        eventPublisher.publishEvent(new WorkflowStateChangedEvent(tenantId, instanceId, "PROCESS_COMPLETED"));
        return new HandleTaskResult(taskId, "DONE", "COMPLETED", List.of());
    }

    private HandleTaskResult transfer(long tenantId, long userId, long taskId, Task flowableTask, String comment,
                                      Long assigneeId) {
        requireAssignee(assigneeId);
        try {
            taskService.setAssignee(flowableTask.getId(), String.valueOf(assigneeId));
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "任务转办失败: " + rootMessage(ex));
        }
        taskMapper.updatePendingAction(tenantId, taskId, "TRANSFER", assigneeId,
                candidateIds(flowableTask.getId()), comment, userId);
        auditor.publish(tenantId, "WF_TASK_TRANSFERRED", "WORKFLOW_TASK", String.valueOf(taskId), "MEDIUM");
        return new HandleTaskResult(taskId, "PENDING", "RUNNING", List.of(String.valueOf(taskId)));
    }

    private HandleTaskResult addSign(long tenantId, long userId, long taskId, Task flowableTask, String comment,
                                     Long assigneeId) {
        requireAssignee(assigneeId);
        try {
            taskService.addCandidateUser(flowableTask.getId(), String.valueOf(assigneeId));
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "任务加签失败: " + rootMessage(ex));
        }
        Set<String> merged = new LinkedHashSet<>();
        String existing = candidateIds(flowableTask.getId());
        if (existing != null && !existing.isBlank()) {
            merged.addAll(List.of(existing.split(",")));
        }
        merged.add(String.valueOf(assigneeId));
        Long assignee = parseLong(flowableTask.getAssignee());
        taskMapper.updatePendingAction(tenantId, taskId, "ADD_SIGN", assignee,
                String.join(",", merged), comment, userId);
        auditor.publish(tenantId, "WF_TASK_ADD_SIGN", "WORKFLOW_TASK", String.valueOf(taskId), "MEDIUM");
        return new HandleTaskResult(taskId, "PENDING", "RUNNING", List.of(String.valueOf(taskId)));
    }

    private HandleTaskResult delegate(long tenantId, long userId, long taskId, Task flowableTask, String comment,
                                      Long assigneeId) {
        requireAssignee(assigneeId);
        try {
            taskService.delegateTask(flowableTask.getId(), String.valueOf(assigneeId));
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "任务委托失败: " + rootMessage(ex));
        }
        taskMapper.updatePendingAction(tenantId, taskId, "DELEGATE", assigneeId,
                candidateIds(flowableTask.getId()), comment, userId);
        auditor.publish(tenantId, "WF_TASK_DELEGATED", "WORKFLOW_TASK", String.valueOf(taskId), "MEDIUM");
        return new HandleTaskResult(taskId, "PENDING", "RUNNING", List.of(String.valueOf(taskId)));
    }

    private void addComment(Task task, String flowableInstanceId, String comment) {
        if (comment != null && !comment.isBlank()) {
            taskService.addComment(task.getId(), flowableInstanceId, comment);
        }
    }

    private boolean canHandle(Task task, long userId) {
        if (task.getAssignee() != null) {
            return String.valueOf(userId).equals(task.getAssignee());
        }
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (IdentityLinkType.CANDIDATE.equals(link.getType())
                    && String.valueOf(userId).equals(link.getUserId())) {
                return true;
            }
        }
        return true;
    }

    private String candidateIds(String flowableTaskId) {
        List<String> ids = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(flowableTaskId)) {
            if (IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getUserId() != null) {
                ids.add(link.getUserId());
            }
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private boolean instanceRunning(String flowableInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableInstanceId).count() > 0;
    }

    private void requireAssignee(Long assigneeId) {
        if (assigneeId == null || assigneeId <= 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "assigneeId 不能为空");
        }
    }

    private Long currentUserId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        return snapshot == null ? null : snapshot.userId();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
