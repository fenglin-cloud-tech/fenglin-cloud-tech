package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.TicketService;
import com.fenglin.service.support.ServiceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工单主链：建单/导入/邮件转单、分派、流转、沟通、SLA 与工作台。
 */
@RestController
@RequestMapping("/api/service/v1")
public class TicketController {

    private static final String CREATE = "service:ticket:create";
    private static final String VIEW = "service:ticket:view";
    private static final String SELF_VIEW = "biz:service:self:view";
    private static final String DESK_VIEW = "biz:service:desk:view";
    private static final String ASSIGN = "service:ticket:assign";
    private static final String HANDLE = "service:ticket:handle";
    private static final String SLA_VIEW = "service:sla:view";

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets")
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemHeader,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(ticketService.createWithIdempotency(body,
                ServiceContext.idempotencyKey(idemHeader), "/api/service/v1/tickets"));
    }

    @PostMapping("/tickets/import")
    public ApiResponse<Map<String, Object>> importBatch(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemHeader,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CREATE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        return ApiResponse.success(ticketService.importBatch(items, ServiceContext.idempotencyKey(idemHeader)));
    }

    @PostMapping("/internal/tickets/email-inbound")
    public ApiResponse<Map<String, Object>> emailInbound(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(ticketService.emailInbound(body));
    }

    @GetMapping("/tickets")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ticketType", required = false) String ticketType,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "requesterId", required = false) Long requesterId,
            @RequestParam(value = "catalogItemId", required = false) Long catalogItemId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(ticketService.list(status, ticketType, priority, assigneeId, requesterId,
                catalogItemId, keyword, page, size));
    }

    @GetMapping("/tickets/{id}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(ticketService.detailWithPii(id, permissions));
    }

    @PostMapping("/tickets/{id}/assign")
    public ApiResponse<Map<String, Object>> assign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ASSIGN);
        return ApiResponse.success(ticketService.assign(id, body));
    }

    @PostMapping("/tickets/{id}/transition")
    public ApiResponse<Map<String, Object>> transition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(ticketService.transition(id, body));
    }

    @PostMapping("/tickets/{id}/suspend")
    public ApiResponse<Map<String, Object>> suspend(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(ticketService.transition(id, transitionBody("PENDING", body)));
    }

    @PostMapping("/tickets/{id}/resume")
    public ApiResponse<Map<String, Object>> resume(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(ticketService.transition(id, transitionBody("IN_PROGRESS", body)));
    }

    @PostMapping("/tickets/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        if (PermissionGuard.has(permissions, HANDLE)) {
            return ApiResponse.success(ticketService.comment(id, body));
        }
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(ticketService.selfComment(id, body));
    }

    /** 员工自助关闭：仅工单提交人本人，等价于 RESOLVED → CLOSED。 */
    @PostMapping("/tickets/{id}/self-close")
    public ApiResponse<Map<String, Object>> selfClose(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(ticketService.selfTransition(id, "CLOSED", body));
    }

    /** 员工自助重开：仅工单提交人本人，CLOSED/RESOLVED → REOPENED。 */
    @PostMapping("/tickets/{id}/self-reopen")
    public ApiResponse<Map<String, Object>> selfReopen(
            @RequestHeader("X-Permissions") String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(ticketService.selfTransition(id, "REOPENED", body));
    }

    @GetMapping("/tickets/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(ticketService.comments(id));
    }

    @GetMapping("/tickets/{id}/flow-logs")
    public ApiResponse<List<Map<String, Object>>> flowLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(ticketService.flowLogs(id));
    }

    @PostMapping("/tickets/{id}/relations")
    public ApiResponse<Map<String, Object>> addRelation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, HANDLE);
        return ApiResponse.success(ticketService.addRelation(id, body));
    }

    @GetMapping("/tickets/{id}/sla")
    public ApiResponse<Map<String, Object>> sla(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, SLA_VIEW);
        return ApiResponse.success(ticketService.sla(id));
    }

    @GetMapping("/sla/breaches")
    public ApiResponse<PageResult<Map<String, Object>>> breaches(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SLA_VIEW);
        return ApiResponse.success(ticketService.breaches(status, page, size));
    }

    @PostMapping("/internal/sla/tick")
    public ApiResponse<Map<String, Object>> tickSla() {
        return ApiResponse.success(ticketService.tickSla());
    }

    @GetMapping("/my/tickets")
    public ApiResponse<PageResult<Map<String, Object>>> myTickets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(ticketService.workspace("my", null, null, page, size));
    }

    @GetMapping("/desk/tickets")
    public ApiResponse<PageResult<Map<String, Object>>> deskTickets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, DESK_VIEW);
        return ApiResponse.success(ticketService.workspace("desk", assigneeId, null, page, size));
    }

    private Map<String, Object> transitionBody(String target, Map<String, Object> body) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (body != null) {
            payload.putAll(body);
        }
        payload.put("to_status", target);
        return payload;
    }
}
