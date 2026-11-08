package com.fenglin.ai.service;

import com.fenglin.ai.AiErrorCode;
import com.fenglin.ai.client.ModelGatewayClient;
import com.fenglin.ai.client.SearchServiceClient;
import com.fenglin.ai.client.SearchServiceClient.RetrievedDoc;
import com.fenglin.ai.web.dto.AiDtos.Citation;
import com.fenglin.ai.web.dto.AiDtos.PromptView;
import com.fenglin.ai.web.dto.AiDtos.RagDone;
import com.fenglin.ai.web.dto.AiDtos.RagRequest;
import com.fenglin.common.core.BizException;
import com.fenglin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HUB-API-1303 RAG 问答（SSE）。
 * 契约缺口：
 *   1) 未约定模型选择与提示词 code，按租户启用模型 routing_weight + 内置 rag_default 兜底；
 *   2) 生成阶段失败时 HTTP 已提交，改为 done 事件 degraded=true（总纲 0000008 语义保留在预检阶段）。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String DEFAULT_SYSTEM_PROMPT = "你是企业知识助手，只基于给定资料回答。";
    private static final String DEFAULT_USER_PROMPT = "基于资料回答：{{context}}\n问题：{{question}}";
    private static final String PROMPT_CODE = "rag_default";

    private final AiGovernanceService governanceService;
    private final GuardrailService guardrailService;
    private final SearchServiceClient searchClient;
    private final AiConfigService configService;
    private final ModelGatewayClient modelGatewayClient;
    private final ExecutorService executorService;
    private final double citationMinScore;
    private final int ragTopK;

    public RagService(AiGovernanceService governanceService,
                      GuardrailService guardrailService,
                      SearchServiceClient searchClient,
                      AiConfigService configService,
                      ModelGatewayClient modelGatewayClient,
                      @Qualifier("aiStreamExecutor") ExecutorService executorService,
                      @Value("${fenglin.ai.citation-min-score:0.6}") double citationMinScore,
                      @Value("${fenglin.ai.rag-top-k:5}") int ragTopK) {
        this.governanceService = governanceService;
        this.guardrailService = guardrailService;
        this.searchClient = searchClient;
        this.configService = configService;
        this.modelGatewayClient = modelGatewayClient;
        this.executorService = executorService;
        this.citationMinScore = citationMinScore;
        this.ragTopK = ragTopK;
    }

    public SseEmitter stream(RagRequest request, long tenantId, Long userId, String hostProduct,
                             String clientType, String requestId, int secretLevel) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BizException(com.fenglin.common.core.CommonErrorCode.BAD_REQUEST,
                    "content 不能为空");
        }
        String product = hostProduct == null || hostProduct.isBlank() ? "hub" : hostProduct.trim();
        if (!governanceService.isAuthorized(tenantId, product, "RAG")) {
            throw new BizException(AiErrorCode.CAPABILITY_DENIED,
                    "RAG 能力未授权，宿主产品: " + product);
        }
        if (guardrailService.check(request.content(), "INPUT").blocked()) {
            throw new BizException(AiErrorCode.GUARDRAIL_BLOCKED, "内容命中护栏规则，已拦截");
        }
        List<Citation> citations = request.useKb() == null || !request.useKb()
                ? List.of() : retrieve(tenantId, userId, request.content(), secretLevel);
        Map<String, Object> model = configService.enabledLlm(tenantId);
        long modelId = ((Number) model.get("id")).longValue();
        String endpoint = (String) model.get("base_url");
        String apiKey = configService.apiKeyOf(modelId, tenantId);
        String prompt = renderPrompt(tenantId, request.content(),
                citations.stream().map(Citation::title).toList());

        String mode = "hub".equals(product) ? "STANDALONE" : "EMBEDDED";
        SseEmitter emitter = new SseEmitter(120_000L);
        executorService.execute(() -> {
            TenantContext.set(tenantId, userId, clientType, requestId, false);
            int tokenIn = estimateTokens(prompt);
            AtomicInteger outputChars = new AtomicInteger();
            long start = System.nanoTime();
            boolean degraded = false;
            java.util.concurrent.atomic.AtomicLong ttftMs = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
            try {
                for (Citation citation : citations) {
                    emitter.send(SseEmitter.event().name("citation")
                            .data(citation, MediaType.APPLICATION_JSON));
                }
                modelGatewayClient.stream(endpoint, apiKey, prompt, request.temperature(), delta -> {
                    if (first.compareAndSet(true, false)) {
                        ttftMs.set((System.nanoTime() - start) / 1_000_000);
                    }
                    outputChars.addAndGet(delta.length());
                    try {
                        emitter.send(SseEmitter.event().name("delta")
                                .data(Map.of("content", delta), MediaType.APPLICATION_JSON));
                    } catch (Exception ex) {
                        throw new BizException(AiErrorCode.MODEL_DEGRADED, "SSE 写出失败");
                    }
                });
            } catch (Exception ex) {
                degraded = true;
                log.warn("RAG 生成降级：tenant={} endpoint={} error={}", tenantId, endpoint,
                        ex.getMessage());
            }
            int tokenOut = Math.max(1, outputChars.get() / 4);
            try {
                governanceService.recordUsage(tenantId, product, "RAG", mode, tokenIn, tokenOut,
                        userId);
            } catch (Exception ex) {
                log.warn("AI 计量写入失败：tenant={} error={}", tenantId, ex.getMessage());
            }
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data(new RagDone(tokenIn, tokenOut, ttftMs.get(), degraded),
                                MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                log.debug("SSE done 事件写出失败：{}", ex.getMessage());
            }
            emitter.complete();
            TenantContext.clear();
        });
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    private List<Citation> retrieve(long tenantId, Long userId, String question, int secretLevel) {
        List<RetrievedDoc> docs = searchClient.search(tenantId, userId, question, "HYBRID",
                ragTopK, secretLevel);
        List<Citation> citations = new ArrayList<>();
        for (RetrievedDoc doc : docs) {
            if (doc.score() < citationMinScore || doc.docId() == null) {
                continue;
            }
            citations.add(new Citation(doc.docId(), doc.title(), doc.score()));
            if (citations.size() >= ragTopK) {
                break;
            }
        }
        return citations;
    }

    private String renderPrompt(long tenantId, String question, List<String> titles) {
        PromptView prompt = configService.findPrompt(tenantId, PROMPT_CODE);
        String system = prompt == null || prompt.systemPrompt() == null
                ? DEFAULT_SYSTEM_PROMPT : prompt.systemPrompt();
        String user = prompt == null || prompt.userPrompt() == null
                ? DEFAULT_USER_PROMPT : prompt.userPrompt();
        String context = titles.isEmpty() ? "（无参考资料）" : String.join("\n", titles);
        return system + "\n\n" + user.replace("{{context}}", context)
                .replace("{{question}}", question);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
