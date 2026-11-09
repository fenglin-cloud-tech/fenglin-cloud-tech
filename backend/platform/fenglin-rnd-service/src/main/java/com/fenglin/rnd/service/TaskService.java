package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.TaskMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.TaskCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TaskView;
import com.fenglin.rnd.web.dto.RndDtos.WorklogCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.WorklogView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E1 任务与工时：状态流转、工时累计（单日 24h 上限）。
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "todo", Set.of("doing", "done"),
            "doing", Set.of("done", "closed"),
            "done", Set.of("closed"));

    private final TaskMapper taskMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final RndAudit audit;

    public TaskService(TaskMapper taskMapper, CommonsMapper commonsMapper, ProductService productService,
                       RndAudit audit) {
        this.taskMapper = taskMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.audit = audit;
    }

    @Transactional
    public TaskView create(TaskCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long id = IdWorker.getId();
        taskMapper.insertTask(id, tenantId, request.productId(), "TASK-" + commonsMapper.nextTaskNo(),
                RndContext.requireText(request.title(), "title"),
                request.taskType() == null ? "dev" : request.taskType(),
                request.priority() == null ? "P2" : request.priority(), request.sprintId(),
                request.requirementId(), request.defectId(), null, request.assigneeId(),
                request.estimateHours(), request.dueDate(), userId);
        audit.publish("TASK_CREATED", "create", "devops_task", id, "low");
        return get(id);
    }

    public TaskView get(long id) {
        Map<String, Object> row = taskMapper.findTask(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<TaskView> list(long productId, Long sprintId, Long assigneeId, String status,
                               Integer page, Integer size) {
        return taskMapper.listTasks(RndContext.tenantId(), productId, sprintId, assigneeId,
                        RndContext.text(status), RndContext.size(size), RndContext.offset(page, size))
                .stream().map(TaskService::toView).toList();
    }

    @Transactional
    public TaskView transition(long id, String status) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> task = requireTask(tenantId, id);
        String target = RndContext.requireText(status, "status");
        Set<String> allowed = TRANSITIONS.getOrDefault(Rows.str(task, "status"), Set.of());
        if (!allowed.contains(target)) {
            throw new BizException(RndErrorCode.TASK_STATE_INVALID);
        }
        taskMapper.updateStatus(tenantId, id, target, RndContext.userId());
        audit.publish("TASK_TRANSITIONED", "transition", "devops_task", id, "low");
        return get(id);
    }

    @Transactional
    public WorklogView addWorklog(WorklogCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.workDate() == null || request.hours() == null
                || request.hours().compareTo(BigDecimal.ZERO) <= 0
                || request.hours().compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        if (request.taskId() == null && request.defectId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        BigDecimal used = taskMapper.sumUserHoursOnDate(tenantId, userId, request.workDate());
        BigDecimal total = (used == null ? BigDecimal.ZERO : used).add(request.hours());
        if (total.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new BizException(RndErrorCode.WORKLOG_HOURS_EXCEEDED);
        }
        long id = IdWorker.getId();
        taskMapper.insertWorklog(id, tenantId, request.taskId(), request.defectId(), userId,
                request.workDate(), request.hours(), RndContext.text(request.remark()));
        if (request.taskId() != null) {
            taskMapper.refreshActualHours(tenantId, request.taskId(), userId);
        }
        audit.publish("WORKLOG_CREATED", "create", "devops_task_worklog", id, "low");
        return listWorklogs(request.taskId(), 50).stream().filter(w -> w.id() == id).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<WorklogView> listWorklogs(Long taskId, Integer limit) {
        return taskMapper.listWorklogs(RndContext.tenantId(), taskId,
                        limit == null ? 50 : Math.min(limit, 200))
                .stream()
                .map(row -> new WorklogView(Rows.lng(row, "id"), Rows.lng(row, "taskId"),
                        Rows.lng(row, "defectId"), Rows.lng(row, "userId"), Rows.date(row, "workDate"),
                        Rows.dec(row, "hours"), Rows.str(row, "remark"), Rows.str(row, "createdAt")))
                .toList();
    }

    private Map<String, Object> requireTask(long tenantId, long id) {
        Map<String, Object> task = taskMapper.findTask(tenantId, id);
        if (task == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return task;
    }

    public static TaskView toView(Map<String, Object> row) {
        return new TaskView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "taskNo"),
                Rows.str(row, "title"), Rows.str(row, "taskType"), Rows.str(row, "status"),
                Rows.str(row, "priority"), Rows.lng(row, "sprintId"), Rows.lng(row, "requirementId"),
                Rows.lng(row, "assigneeId"), Rows.dec(row, "estimateHours"), Rows.dec(row, "actualHours"),
                Rows.date(row, "dueDate"), Rows.str(row, "completedAt"), Rows.str(row, "createdAt"));
    }
}
