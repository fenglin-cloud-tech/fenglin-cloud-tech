package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.MeetingMapper;
import com.fenglin.collab.mapper.WorkbenchMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.LayoutSaveRequest;
import com.fenglin.collab.web.dto.CollabDtos.LayoutView;
import com.fenglin.collab.web.dto.CollabDtos.NotificationReadResult;
import com.fenglin.collab.web.dto.CollabDtos.NotificationView;
import com.fenglin.collab.web.dto.CollabDtos.TodoView;
import com.fenglin.collab.web.dto.CollabDtos.WidgetCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.WidgetPlacement;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作台：个人布局、统一待办（来源唯一去重）、通知已读。
 */
@Service
public class WorkbenchService {

    private final WorkbenchMapper workbenchMapper;
    private final MeetingMapper meetingMapper;
    private final CollabAudit audit;
    private final ObjectMapper objectMapper;

    public WorkbenchService(WorkbenchMapper workbenchMapper, MeetingMapper meetingMapper,
                            CollabAudit audit, ObjectMapper objectMapper) {
        this.workbenchMapper = workbenchMapper;
        this.meetingMapper = meetingMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public LayoutView layout() {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> row = workbenchMapper.findPersonalLayout(tenantId, userId);
        if (row == null) {
            row = workbenchMapper.findDefaultLayout(tenantId);
        }
        if (row == null) {
            return new LayoutView("PERSONAL", List.of());
        }
        return new LayoutView(Rows.str(row, "layoutType"), parseWidgets(Rows.str(row, "widgets")));
    }

    @Transactional
    public LayoutView saveLayout(LayoutSaveRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        List<WidgetPlacement> widgets = request == null || request.widgets() == null
                ? List.of() : request.widgets();
        String json = writeJson(widgets);
        Map<String, Object> existing = workbenchMapper.findPersonalLayout(tenantId, userId);
        if (existing == null) {
            workbenchMapper.insertPersonalLayout(IdWorker.getId(), tenantId, userId, json);
        } else {
            workbenchMapper.updateLayout(tenantId, Rows.lngVal(existing, "layoutId"), json, userId);
        }
        audit.publish("COLLAB_WORKBENCH_LAYOUT_SAVED", "UPDATE", "collab_workbench_layout", userId, "LOW");
        return new LayoutView("PERSONAL", widgets);
    }

    @Transactional
    public void createWidget(WidgetCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.code() == null || request.name() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "组件编码与名称不能为空");
        }
        workbenchMapper.insertWidget(IdWorker.getId(), tenantId, request.code(), request.name(),
                request.widgetType() == null ? "CARD" : request.widgetType(),
                request.defaultSize() == null ? "MEDIUM" : request.defaultSize(),
                request.enabled() == null || request.enabled() ? 1 : 0, userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> widgets() {
        return workbenchMapper.listWidgets(CollabContext.tenantId());
    }

    @Transactional
    public long createTodo(long tenantId, long ownerId, String sourceType, String sourceId, String title,
                           OffsetDateTime dueAt, String priority, long userId) {
        workbenchMapper.insertTodo(IdWorker.getId(), tenantId, ownerId, sourceType, sourceId, title, dueAt,
                priority == null ? "NORMAL" : priority, userId);
        Long todoId = workbenchMapper.findTodoIdBySource(tenantId, ownerId, sourceType, sourceId);
        if (todoId == null) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "待办创建失败");
        }
        return todoId;
    }

    @Transactional(readOnly = true)
    public PageResult<TodoView> todos(String status, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<TodoView> records = workbenchMapper
                .listTodos(tenantId, userId, status, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new TodoView(Rows.lngVal(row, "todoId"), Rows.str(row, "sourceType"),
                        Rows.str(row, "sourceId"), Rows.str(row, "title"), Rows.instant(row, "dueAt"),
                        Rows.str(row, "priority"), Rows.str(row, "status")))
                .toList();
        return PageResult.of(records, records.size(), safePage, safeSize);
    }

    @Transactional
    public void completeTodo(long todoId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> todo = workbenchMapper.findTodo(tenantId, todoId, userId);
        if (todo == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "待办不存在");
        }
        if (workbenchMapper.markTodoDone(tenantId, todoId, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "待办已完成或已取消");
        }
        if ("MEETING_ACTION".equals(Rows.str(todo, "sourceType"))) {
            meetingMapper.completeAction(tenantId, Long.parseLong(Rows.str(todo, "sourceId")), userId);
        }
        audit.publish("COLLAB_TODO_COMPLETED", "UPDATE", "collab_todo", todoId, "LOW");
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> notifications(Boolean unreadOnly, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<NotificationView> records = workbenchMapper
                .listNotifications(tenantId, userId, unreadOnly, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new NotificationView(Rows.lngVal(row, "notificationId"), Rows.str(row, "category"),
                        Rows.str(row, "title"), Rows.str(row, "content"),
                        Rows.intVal(row, "readFlag") == 1, Rows.instant(row, "createdAt")))
                .toList();
        return PageResult.of(records, workbenchMapper.countNotifications(tenantId, userId, unreadOnly),
                safePage, safeSize);
    }

    /**
     * 缺口补齐 API-WB-006：通知标记已读。
     */
    @Transactional
    public NotificationReadResult markNotificationRead(long notificationId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (workbenchMapper.markNotificationRead(tenantId, notificationId, userId) == 0) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        return new NotificationReadResult(notificationId, true);
    }

    @Transactional
    public void notifyUser(long tenantId, long receiverId, String category, String title, String content,
                           String bizType, String bizId, long userId) {
        workbenchMapper.insertNotification(IdWorker.getId(), tenantId, receiverId, category, title, content,
                bizType, bizId, userId);
    }

    @SuppressWarnings("unchecked")
    private List<WidgetPlacement> parseWidgets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<WidgetPlacement>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(List<WidgetPlacement> widgets) {
        try {
            return objectMapper.writeValueAsString(widgets);
        } catch (JsonProcessingException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "工作台布局格式非法");
        }
    }
}
