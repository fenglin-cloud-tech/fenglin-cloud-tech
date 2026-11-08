package com.fenglin.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.workflow.mapper.WorkflowDefinitionMapper;
import com.fenglin.workflow.mapper.WorkflowFormDataMapper;
import com.fenglin.workflow.mapper.WorkflowInstanceMapper;
import com.fenglin.workflow.mapper.WorkflowTaskMapper;
import com.fenglin.workflow.web.dto.InstanceDetailResult;
import com.fenglin.workflow.web.dto.NodeView;
import com.fenglin.workflow.web.dto.PendingTaskView;
import com.fenglin.workflow.web.dto.StartInstanceRequest;
import com.fenglin.workflow.web.dto.StartInstanceResult;
import com.fenglin.workflow.web.dto.TerminateResult;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkflowInstanceService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> NODE_TYPES = Set.of("startEvent", "userTask", "endEvent");

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowFormDataMapper formDataMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowAuditor auditor;

    public WorkflowInstanceService(WorkflowDefinitionMapper definitionMapper,
                                   WorkflowInstanceMapper instanceMapper,
                                   WorkflowTaskMapper taskMapper,
                                   WorkflowFormDataMapper formDataMapper,
                                   RuntimeService runtimeService,
                                   TaskService taskService,
                                   HistoryService historyService,
                                   ObjectMapper objectMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   WorkflowAuditor auditor) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.formDataMapper = formDataMapper;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.auditor = auditor;
    }

    @Transactional
    public StartInstanceResult start(StartInstanceRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Long userId = currentUserId();
        if (userId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "缺少用户上下文，无法发起流程");
        }
        String processKey = request == null ? null : request.processKey();
        if (processKey == null || processKey.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "processKey 不能为空");
        }
        Map<String, Object> definition = definitionMapper.findPublished(tenantId, processKey);
        if (definition == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "0004001 流程定义不存在: " + processKey);
        }

        Map<String, Object> variables = new HashMap<>();
        if (request.formData() != null) {
            variables.putAll(request.formData());
        }
        variables.put("initiatorId", userId);
        variables.put("processKey", processKey);
        if (request.businessKey() != null) {
            variables.put("businessKey", request.businessKey());
        }
        if (request.businessType() != null) {
            variables.put("businessType", request.businessType());
        }
        if (request.title() != null) {
            variables.put("title", request.title());
        }

        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceByKeyAndTenantId(
                    processKey, request.businessKey(), variables, String.valueOf(tenantId));
        } catch (FlowableObjectNotFoundException ex) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "0004001 流程定义不存在: " + processKey);
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "发起流程失败: " + rootMessage(ex));
        }

        long instanceId = IdWorker.getId();
        String instanceNo = "WF" + instanceId;
        instanceMapper.insert(instanceId, tenantId, instanceNo, processKey,
                intValue(definition.get("def_version")), processInstance.getId(),
                request.businessKey(), request.businessType(), request.title(), userId);

        if (request.formData() != null && !request.formData().isEmpty()) {
            formDataMapper.insert(IdWorker.getId(), tenantId, instanceId, (String) definition.get("form_key"),
                    toJson(request.formData()), userId);
        }

        List<Long> taskIds = syncTasks(tenantId, instanceId, processInstance.getId(), userId);
        auditor.publish(tenantId, "WF_INSTANCE_STARTED", "WORKFLOW_INSTANCE", instanceNo, "MEDIUM");

        if (!instanceRunning(processInstance.getId())) {
            finishInstance(tenantId, instanceId, instanceNo, "APPROVED", userId);
            return new StartInstanceResult(instanceId, instanceNo, "COMPLETED", null);
        }
        return new StartInstanceResult(instanceId, instanceNo, "RUNNING",
                taskIds.isEmpty() ? null : String.valueOf(taskIds.get(0)));
    }

    @Transactional(readOnly = true)
    public PageResult<PendingTaskView> pending(String domain, Boolean urgent, Integer page, Integer size) {
        long tenantId = TenantContext.requireTenantId();
        Long userId = currentUserId();
        if (userId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "缺少用户上下文");
        }
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        boolean onlyUrgent = Boolean.TRUE.equals(urgent);
        Instant urgentBefore = Instant.now().plusSeconds(24 * 3600);
        List<Map<String, Object>> rows = taskMapper.pendingPage(tenantId, userId, blankToNull(domain),
                onlyUrgent, urgentBefore, currentSize, (currentPage - 1) * currentSize);
        long total = taskMapper.pendingCount(tenantId, userId, blankToNull(domain), onlyUrgent, urgentBefore);
        List<PendingTaskView> views = rows.stream()
                .map(row -> {
                    Instant dueAt = toInstant(row.get("due_at"));
                    String title = (String) row.get("title");
                    return new PendingTaskView(
                            longValue(row.get("id")),
                            longValue(row.get("instance_id")),
                            (String) row.get("instance_no"),
                            title == null || title.isBlank() ? (String) row.get("instance_no") : title,
                            (String) row.get("node_key"),
                            (String) row.get("node_name"),
                            row.get("initiator_id") == null ? null : longValue(row.get("initiator_id")),
                            (String) row.get("initiator_name"),
                            dueAt,
                            dueAt != null && dueAt.isBefore(urgentBefore));
                })
                .toList();
        return PageResult.of(views, total, currentPage, currentSize);
    }

    @Transactional(readOnly = true)
    public InstanceDetailResult detail(long instanceId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> instance = requireInstance(tenantId, instanceId);
        String flowableInstanceId = (String) instance.get("flowable_instance_id");
        List<Map<String, Object>> taskRows = taskMapper.findByInstance(tenantId, instanceId);

        Map<String, Map<String, Object>> latestTaskByNode = new LinkedHashMap<>();
        for (Map<String, Object> row : taskRows) {
            latestTaskByNode.put((String) row.get("node_key"), row);
        }
        Set<Long> handlerIds = taskRows.stream()
                .map(row -> row.get("assignee_id"))
                .filter(java.util.Objects::nonNull)
                .map(value -> ((Number) value).longValue())
                .collect(Collectors.toSet());
        List<HistoricActivityInstance> activities = flowableInstanceId == null
                ? List.of()
                : historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(flowableInstanceId)
                        .orderByHistoricActivityInstanceStartTime().asc()
                        .list();
        for (HistoricActivityInstance activity : activities) {
            Long assignee = parseLong(activity.getAssignee());
            if (assignee != null) {
                handlerIds.add(assignee);
            }
        }
        Map<Long, String> names = handlerIds.isEmpty() ? Map.of() : taskMapper.displayNames(
                        tenantId, new ArrayList<>(handlerIds)).stream()
                .collect(Collectors.toMap(
                        row -> longValue(row.get("id")),
                        row -> (String) row.get("display_name"),
                        (left, right) -> left));

        List<NodeView> nodes = new ArrayList<>();
        if (!activities.isEmpty()) {
            activities.stream()
                    .filter(activity -> NODE_TYPES.contains(activity.getActivityType()))
                    .sorted(Comparator.comparing(HistoricActivityInstance::getStartTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(activity -> {
                        Map<String, Object> taskRow = "userTask".equals(activity.getActivityType())
                                ? latestTaskByNode.get(activity.getActivityId()) : null;
                        Long handlerId = parseLong(activity.getAssignee());
                        if (handlerId == null && taskRow != null) {
                            handlerId = taskRow.get("assignee_id") == null
                                    ? null : longValue(taskRow.get("assignee_id"));
                        }
                        nodes.add(new NodeView(
                                activity.getActivityId(),
                                activity.getActivityName() == null
                                        ? activity.getActivityId() : activity.getActivityName(),
                                activity.getEndTime() == null ? "ACTIVE" : "DONE",
                                handlerId,
                                handlerId == null ? null : names.getOrDefault(handlerId, String.valueOf(handlerId)),
                                taskRow == null ? null : (String) taskRow.get("comment"),
                                toInstant(activity.getEndTime())));
                    });
        } else {
            for (Map<String, Object> row : taskRows) {
                Long assignee = row.get("assignee_id") == null ? null : longValue(row.get("assignee_id"));
                nodes.add(new NodeView(
                        (String) row.get("node_key"),
                        (String) row.get("node_name"),
                        (String) row.get("status"),
                        assignee,
                        assignee == null ? null : names.getOrDefault(assignee, String.valueOf(assignee)),
                        (String) row.get("comment"),
                        toInstant(row.get("handled_at"))));
            }
        }

        return new InstanceDetailResult(
                instanceId,
                (String) instance.get("instance_no"),
                (String) instance.get("process_key"),
                intValue(instance.get("def_version")),
                (String) instance.get("status"),
                (String) instance.get("result"),
                (String) instance.get("title"),
                (String) instance.get("business_key"),
                (String) instance.get("business_type"),
                instance.get("initiator_id") == null ? null : longValue(instance.get("initiator_id")),
                toInstant(instance.get("started_at")),
                toInstant(instance.get("ended_at")),
                nodes);
    }

    @Transactional
    public TerminateResult terminate(long instanceId, String reason) {
        long tenantId = TenantContext.requireTenantId();
        Long userId = currentUserId();
        Map<String, Object> instance = requireInstance(tenantId, instanceId);
        if (!"RUNNING".equals(instance.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "0000005 流程实例当前状态不可终止: "
                    + instance.get("status"));
        }
        long initiatorId = longValue(instance.get("initiator_id"));
        if (userId == null || userId != initiatorId) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅流程发起人可终止该实例");
        }
        String flowableInstanceId = (String) instance.get("flowable_instance_id");
        if (flowableInstanceId != null) {
            try {
                runtimeService.deleteProcessInstance(flowableInstanceId,
                        reason == null || reason.isBlank() ? "TERMINATED_BY_INITIATOR" : reason);
            } catch (FlowableObjectNotFoundException ignored) {
                // 引擎侧已结束，按本地状态收口
            }
        }
        taskMapper.cancelPendingByInstance(tenantId, instanceId, userId);
        instanceMapper.markTerminated(tenantId, instanceId, userId);
        auditor.publish(tenantId, "WF_INSTANCE_TERMINATED", "WORKFLOW_INSTANCE",
                (String) instance.get("instance_no"), "HIGH");
        eventPublisher.publishEvent(new WorkflowStateChangedEvent(tenantId, instanceId, "PROCESS_TERMINATED"));
        return new TerminateResult(instanceId, "TERMINATED", "CANCELLED");
    }

    public List<Long> syncActiveTasks(long tenantId, long instanceId, String flowableInstanceId, Long operatorId) {
        return syncTasks(tenantId, instanceId, flowableInstanceId, operatorId);
    }

    private List<Long> syncTasks(long tenantId, long instanceId, String flowableInstanceId, Long operatorId) {
        if (flowableInstanceId == null) {
            return List.of();
        }
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(flowableInstanceId).list();
        List<Long> ids = new ArrayList<>();
        for (Task task : tasks) {
            Long existing = taskMapper.findPendingByFlowableTaskId(tenantId, task.getId());
            if (existing != null) {
                ids.add(existing);
                continue;
            }
            List<Long> candidates = new ArrayList<>();
            List<String> groups = new ArrayList<>();
            for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
                if (!IdentityLinkType.CANDIDATE.equals(link.getType())) {
                    continue;
                }
                Long candidate = parseLong(link.getUserId());
                if (candidate != null) {
                    candidates.add(candidate);
                }
                if (link.getGroupId() != null) {
                    groups.add(link.getGroupId());
                }
            }
            long taskRowId = IdWorker.getId();
            taskMapper.insert(taskRowId, tenantId, instanceId, task.getId(),
                    task.getTaskDefinitionKey(), task.getName(), parseLong(task.getAssignee()),
                    joinIds(candidates), groups.isEmpty() ? null : String.join(",", groups),
                    "APPROVE", toInstant(task.getDueDate()), operatorId);
            ids.add(taskRowId);
        }
        return ids;
    }

    private void finishInstance(long tenantId, long instanceId, String instanceNo, String result, Long operatorId) {
        instanceMapper.markCompleted(tenantId, instanceId, result, operatorId);
        auditor.publish(tenantId, "WF_INSTANCE_COMPLETED", "WORKFLOW_INSTANCE", instanceNo, "MEDIUM");
        eventPublisher.publishEvent(new WorkflowStateChangedEvent(tenantId, instanceId, "PROCESS_COMPLETED"));
    }

    private boolean instanceRunning(String flowableInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableInstanceId).count() > 0;
    }

    private Map<String, Object> requireInstance(long tenantId, long instanceId) {
        Map<String, Object> instance = instanceMapper.findById(tenantId, instanceId);
        if (instance == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        return instance;
    }

    private Long currentUserId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        return snapshot == null ? null : snapshot.userId();
    }

    private String toJson(Map<String, Object> formData) {
        try {
            return objectMapper.writeValueAsString(formData);
        } catch (JsonProcessingException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "formData 无法序列化为 JSON");
        }
    }

    private String joinIds(List<Long> ids) {
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return Instant.parse(value.toString());
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
