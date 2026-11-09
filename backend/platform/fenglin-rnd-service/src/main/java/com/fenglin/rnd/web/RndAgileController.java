package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.BoardService;
import com.fenglin.rnd.service.SprintService;
import com.fenglin.rnd.service.TaskService;
import com.fenglin.rnd.web.dto.RndDtos.BoardCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.BoardView;
import com.fenglin.rnd.web.dto.RndDtos.BurndownView;
import com.fenglin.rnd.web.dto.RndDtos.MoveRequest;
import com.fenglin.rnd.web.dto.RndDtos.SprintCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.SprintView;
import com.fenglin.rnd.web.dto.RndDtos.TaskCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TaskTransitionRequest;
import com.fenglin.rnd.web.dto.RndDtos.TaskView;
import com.fenglin.rnd.web.dto.RndDtos.WorklogCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.WorklogView;
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
 * E1 迭代、看板、任务与工时接口。
 */
@RestController
@RequestMapping("/api/rnd/v1")
public class RndAgileController {

    private static final String SPRINT_MANAGE = "biz:devops:sprint:manage";
    private static final String SPRINT_VIEW = "biz:devops:sprint:view";
    private static final String BOARD_MANAGE = "biz:devops:board:manage";
    private static final String BOARD_VIEW = "biz:devops:board:view";
    private static final String TASK_CREATE = "biz:devops:task:create";
    private static final String TASK_TRANSITION = "biz:devops:task:transition";
    private static final String TASK_VIEW = "biz:devops:task:view";
    private static final String WORKLOG_SUBMIT = "biz:devops:worklog:submit";
    private static final String WORKLOG_VIEW = "biz:devops:worklog:view";

    private final SprintService sprintService;
    private final BoardService boardService;
    private final TaskService taskService;

    public RndAgileController(SprintService sprintService, BoardService boardService, TaskService taskService) {
        this.sprintService = sprintService;
        this.boardService = boardService;
        this.taskService = taskService;
    }

    @PostMapping("/sprints")
    public ApiResponse<SprintView> createSprint(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody SprintCreateRequest request) {
        PermissionGuard.require(permissions, SPRINT_MANAGE);
        return ApiResponse.success(sprintService.create(request));
    }

    @GetMapping("/sprints")
    public ApiResponse<List<SprintView>> listSprints(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "status", required = false) String status) {
        PermissionGuard.require(permissions, SPRINT_VIEW);
        return ApiResponse.success(sprintService.list(productId, status));
    }

    @GetMapping("/sprints/{id}")
    public ApiResponse<SprintView> getSprint(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SPRINT_VIEW);
        return ApiResponse.success(sprintService.get(id));
    }

    @PostMapping("/sprints/{id}/start")
    public ApiResponse<SprintView> startSprint(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SPRINT_MANAGE);
        return ApiResponse.success(sprintService.start(id));
    }

    @PostMapping("/sprints/{id}/complete")
    public ApiResponse<SprintView> completeSprint(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SPRINT_MANAGE);
        return ApiResponse.success(sprintService.complete(id));
    }

    @GetMapping("/sprints/{id}/burndown")
    public ApiResponse<BurndownView> burndown(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SPRINT_VIEW);
        return ApiResponse.success(sprintService.burndown(id));
    }

    @PostMapping("/boards")
    public ApiResponse<BoardView> createBoard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody BoardCreateRequest request) {
        PermissionGuard.require(permissions, BOARD_MANAGE);
        return ApiResponse.success(boardService.create(request));
    }

    @GetMapping("/boards/{id}")
    public ApiResponse<BoardView> getBoard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, BOARD_VIEW);
        return ApiResponse.success(boardService.get(id));
    }

    @PostMapping("/boards/{id}/move")
    public ApiResponse<BoardView> moveCard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody MoveRequest request) {
        PermissionGuard.require(permissions, BOARD_MANAGE);
        return ApiResponse.success(boardService.move(id, request));
    }

    @PostMapping("/tasks")
    public ApiResponse<TaskView> createTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TaskCreateRequest request) {
        PermissionGuard.require(permissions, TASK_CREATE);
        return ApiResponse.success(taskService.create(request));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TaskView>> listTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "sprintId", required = false) Long sprintId,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, TASK_VIEW);
        return ApiResponse.success(taskService.list(productId, sprintId, assigneeId, status, page, size));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<TaskView> getTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, TASK_VIEW);
        return ApiResponse.success(taskService.get(id));
    }

    @PostMapping("/tasks/{id}/transition")
    public ApiResponse<TaskView> transitionTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody TaskTransitionRequest request) {
        PermissionGuard.require(permissions, TASK_TRANSITION);
        return ApiResponse.success(taskService.transition(id, request.status()));
    }

    @PostMapping("/worklogs")
    public ApiResponse<WorklogView> addWorklog(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody WorklogCreateRequest request) {
        PermissionGuard.require(permissions, WORKLOG_SUBMIT);
        return ApiResponse.success(taskService.addWorklog(request));
    }

    @GetMapping("/worklogs")
    public ApiResponse<List<WorklogView>> listWorklogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, WORKLOG_VIEW);
        return ApiResponse.success(taskService.listWorklogs(taskId, limit));
    }
}
