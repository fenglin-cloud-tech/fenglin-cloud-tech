package com.fenglin.ai.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    // HUB-API-1301
    public record ModelCreateRequest(String code, String providerType, String modelType,
                                     String endpoint, String apiKey, Integer routingWeight) {
    }

    public record ModelView(long id, String code, String providerType, String modelType,
                            String endpoint, String apiKeyFingerprint, int routingWeight) {
    }

    // HUB-API-1302
    public record PromptCreateRequest(String code, String name, String scene, String systemPrompt,
                                      String userPrompt, List<String> variables) {
    }

    public record PromptView(long id, String code, int promptVersion, String name, String scene,
                             String systemPrompt, String userPrompt, List<String> variables,
                             String status) {
    }

    // HUB-API-1303
    public record RagRequest(String content, Boolean useKb, List<Long> kbIds, Double temperature) {
    }

    public record Citation(String docId, String title, double score) {
    }

    public record RagDone(int tokenIn, int tokenOut, long ttftMs, boolean degraded) {
    }

    // HUB-API-1304
    public record HybridSearchRequest(String query, List<Long> kbIds, Double fulltextWeight,
                                      Double vectorWeight, Integer topK, Boolean rerank) {
    }

    public record HybridRecord(String chunkId, double score, String source, String title) {
    }

    public record HybridSearchResult(List<HybridRecord> records, String fusion, long latencyMs) {
    }

    // HUB-API-1305
    public record GuardrailRequest(String content, String direction) {
    }

    public record GuardrailHit(String type, String rule) {
    }

    public record GuardrailResult(boolean blocked, List<GuardrailHit> hits, String action) {
    }

    // HUB-API-1306
    public record GrantItem(String hostProduct, String capability, Boolean enabled, Integer quotaQps,
                            Long quotaTokenMonth, String billingMode) {
    }

    public record GrantRequest(List<GrantItem> items) {
    }

    public record GrantView(long id, String hostProduct, String capability, boolean enabled,
                            int quotaQps, long quotaTokenMonth, String billingMode) {
    }

    public record GrantResult(List<GrantView> items) {
    }

    // HUB-API-1307
    public record MeteringRecord(String hostProduct, String capability, String mode, long callCount,
                                 long tokenIn, long tokenOut, BigDecimal cost, LocalDate statDate) {
    }

    public record MeteringResult(List<MeteringRecord> records) {
    }

    // HUB-API-1308
    public record ModeValidateResult(String mode, boolean consistent, List<String> issues) {
    }
}
