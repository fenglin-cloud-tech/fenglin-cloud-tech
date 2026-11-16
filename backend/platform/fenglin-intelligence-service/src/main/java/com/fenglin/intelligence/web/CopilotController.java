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
    private final com.fenglin.intelligence.service.KnowledgeService knowledgeService;

    public CopilotController(CopilotService service,
                             com.fenglin.intelligence.service.KnowledgeService knowledgeService) {
        this.service = service;
        this.knowledgeService = knowledgeService;
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

    @PostMapping(value = "/copilot/conversations/{id}/messages:stream",
            produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamMessage(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:chat:rag");
        return service.streamMessage(id, body);
    }

    @GetMapping("/copilot/messages/{id}/citations")
    public ApiResponse<java.util.List<Map<String, Object>>> citations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:chat:cite");
        return ApiResponse.success(service.messageCitations(id));
    }

    @GetMapping("/copilot/private-kb")
    public ApiResponse<java.util.List<Map<String, Object>>> privateKnowledgeBases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "ai:chat:private-kb");
        return ApiResponse.success(knowledgeService.listPersonalKnowledgeBases());
    }

    @PostMapping("/copilot/private-kb")
    public ApiResponse<Map<String, Object>> createPrivateKnowledgeBase(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:chat:private-kb");
        return ApiResponse.success(knowledgeService.createPersonalKnowledgeBase(
                body == null ? Map.of() : body));
    }

    @GetMapping("/agents/executions")
    public ApiResponse<PageResult<Map<String, Object>>> listExecutions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(name = "agent_id", required = false) Long agentId,
            @RequestParam(required = false) String scope) {
        PermissionGuard.require(permissions, "ai:chat:use");
        boolean all = "ALL".equalsIgnoreCase(scope)
                && PermissionGuard.has(permissions, "tenant:ai:agent:manage");
        java.util.List<Map<String, Object>> rows = service.listExecutions(page, size, agentId, all);
        return ApiResponse.success(PageResult.of(rows, service.countExecutions(agentId, all),
                page == null ? 1 : page, size == null ? 20 : size));
    }

    @GetMapping("/agents/executions/{id}")
    public ApiResponse<Map<String, Object>> executionDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.executionDetail(id));
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
