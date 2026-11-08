package com.fenglin.ai.web;

import com.fenglin.ai.service.GuardrailService;
import com.fenglin.ai.service.HybridSearchService;
import com.fenglin.ai.service.RagService;
import com.fenglin.ai.web.dto.AiDtos.GuardrailRequest;
import com.fenglin.ai.web.dto.AiDtos.GuardrailResult;
import com.fenglin.ai.web.dto.AiDtos.HybridSearchRequest;
import com.fenglin.ai.web.dto.AiDtos.HybridSearchResult;
import com.fenglin.ai.web.dto.AiDtos.RagRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AiPortalController {

    private static final String RAG_PERMISSION = "ai:chat:rag";

    private final RagService ragService;
    private final HybridSearchService hybridSearchService;
    private final GuardrailService guardrailService;

    public AiPortalController(RagService ragService, HybridSearchService hybridSearchService,
                              GuardrailService guardrailService) {
        this.ragService = ragService;
        this.hybridSearchService = hybridSearchService;
        this.guardrailService = guardrailService;
    }

    /** HUB-API-1303 RAG 问答（SSE：citation / delta / done）。 */
    @PostMapping(value = "/api/hub/v1/ai/copilot/messages:stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Caller-Product", required = false) String callerProduct,
            @RequestHeader(value = "X-Secret-Level", required = false) Integer secretLevel,
            @RequestBody RagRequest request) {
        PermissionGuard.require(permissions, RAG_PERMISSION);
        TenantContext.Snapshot context = TenantContext.get();
        return ragService.stream(request, TenantContext.requireTenantId(), operator(userId),
                callerProduct,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(),
                secretLevel == null ? 0 : secretLevel);
    }

    /** HUB-API-1304 向量/混合检索（RRF 融合）。 */
    @PostMapping("/api/hub/v1/ai/search/hybrid")
    public ApiResponse<HybridSearchResult> hybrid(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Secret-Level", required = false) Integer secretLevel,
            @RequestBody HybridSearchRequest request) {
        return ApiResponse.success(hybridSearchService.search(TenantContext.requireTenantId(),
                operator(userId), secretLevel == null ? 0 : secretLevel, request));
    }

    /** HUB-API-1305 护栏检测。 */
    @PostMapping("/api/hub/v1/ai/guardrails:check")
    public ApiResponse<GuardrailResult> guardrail(@RequestBody GuardrailRequest request) {
        return ApiResponse.success(guardrailService.check(
                request == null ? null : request.content(),
                request == null ? null : request.direction()));
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
