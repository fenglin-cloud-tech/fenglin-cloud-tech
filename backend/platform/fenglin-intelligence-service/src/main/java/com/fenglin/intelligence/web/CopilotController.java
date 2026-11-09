package com.fenglin.intelligence.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.intelligence.service.CopilotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * H2 Copilot/Agent 接口。
 */
@RestController
@RequestMapping("/api/intelligence/v1")
public class CopilotController {

    private final CopilotService service;

    public CopilotController(CopilotService service) {
        this.service = service;
    }

    @PostMapping("/copilot/conversations")
    public ApiResponse<Map<String, Object>> createConversation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.createConversation(body == null ? Map.of() : body));
    }

    @GetMapping("/copilot/conversations")
    public ApiResponse<PageResult<Map<String, Object>>> listConversations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.listConversations(page, size));
    }

    @GetMapping("/copilot/conversations/{id}/messages")
    public ApiResponse<PageResult<Map<String, Object>>> listMessages(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.listMessages(id, page, size));
    }

    @PostMapping("/copilot/conversations/{id}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:chat:rag");
        return ApiResponse.success(service.sendMessage(id, body));
    }

    @PostMapping("/copilot/messages/{id}/feedback")
    public ApiResponse<Map<String, Object>> feedback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:chat:feedback");
        return ApiResponse.success(service.feedback(id, body));
    }

    @PostMapping("/copilot/text:write")
    public ApiResponse<Map<String, Object>> write(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:writing:use");
        return ApiResponse.success(service.textAssist("WRITE", body));
    }

    @PostMapping("/copilot/text:summarize")
    public ApiResponse<Map<String, Object>> summarize(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:summarize:use");
        return ApiResponse.success(service.textAssist("SUMMARIZE", body));
    }

    @PostMapping("/copilot/text:translate")
    public ApiResponse<Map<String, Object>> translate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:translate:use");
        return ApiResponse.success(service.textAssist("TRANSLATE", body));
    }

    @PostMapping("/admin/agents")
    public ApiResponse<Map<String, Object>> createAgent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:agent:manage");
        return ApiResponse.success(service.createAgent(body));
    }

    @GetMapping("/agents")
    public ApiResponse<PageResult<Map<String, Object>>> listAgents(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.listAgents(page, size));
    }

    @PostMapping("/agents/{code}/run")
    public ApiResponse<Map<String, Object>> runAgent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:agent:run");
        return ApiResponse.success(service.runAgent(code, body == null ? Map.of() : body));
    }

    @PutMapping("/admin/guardrails")
    public ApiResponse<Map<String, Object>> guardrails(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:guardrail:config");
        return ApiResponse.success(service.guardrailAck(body));
    }
}
