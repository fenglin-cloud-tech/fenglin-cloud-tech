package com.fenglin.intelligence.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.intelligence.IntelligenceErrorCode;
import com.fenglin.intelligence.client.AiGatewayClient;
import com.fenglin.intelligence.mapper.CopilotMapper;
import com.fenglin.intelligence.mapper.KnowledgeMapper;
import com.fenglin.intelligence.support.IntelligenceAudit;
import com.fenglin.intelligence.support.IntelligenceContext;
import com.fenglin.intelligence.support.IntelligenceMaps;
import com.fenglin.intelligence.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H2 Copilot/Agent：会话、RAG 问答、引用溯源、文本助手、智能体编排、反馈。
 * 模型生成与护栏委托平台 AI 中台；本域负责业务上下文拼装、检索、留痕与引用。
 */
@Service
@Transactional
public class CopilotService {

    private final CopilotMapper copilotMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final AiGatewayClient gatewayClient;
    private final IntelligenceAudit audit;

    public CopilotService(CopilotMapper copilotMapper, KnowledgeMapper knowledgeMapper,
                          AiGatewayClient gatewayClient, IntelligenceAudit audit) {
        this.copilotMapper = copilotMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.gatewayClient = gatewayClient;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> createConversation(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long id = IdWorker.getId();
        copilotMapper.insertConversation(id, tenantId,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "title")), "新会话"),
                userId,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "type")), "RAG"),
                kbCsv(IntelligenceContext.first(body, "kb_ids", "kbIds")),
                IntelligenceContext.string(IntelligenceContext.first(body, "model_code", "modelCode")));
        audit.low("conversation.create", "conversation", id);
        Map<String, Object> result = new LinkedHashMap<>(copilotMapper.findConversation(tenantId, id));
        result.put("messages", List.of());
        return result;
    }

    public PageResult<Map<String, Object>> listConversations(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        return PageResult.of(copilotMapper.listConversations(tenantId, userId,
                        IntelligenceContext.size(size), IntelligenceContext.offset(page, size)),
                copilotMapper.countConversations(tenantId, userId),
                IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    public PageResult<Map<String, Object>> listMessages(long conversationId, Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        requireConversation(tenantId, conversationId);
        List<Map<String, Object>> rows = copilotMapper.listMessages(tenantId, conversationId,
                IntelligenceContext.size(size), IntelligenceContext.offset(page, size));
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("citations", copilotMapper.listCitations(tenantId, Rows.lngVal(row, "id")));
            enriched.add(item);
        }
        return PageResult.of(enriched, copilotMapper.countMessages(tenantId, conversationId),
                IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> sendMessage(long conversationId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> conversation = requireConversation(tenantId, conversationId);
        String question = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "content", "question")));
        String kbCsv = copilotMapper.findConversationKbCsv(tenantId, conversationId);
        List<Map<String, Object>> hits = knowledgeMapper.searchChunks(tenantId, kbCsv, question,
                IntelligenceContext.clamp(IntelligenceContext.intValue(
                        IntelligenceContext.first(body, "sec_level", "secLevel"), 2), 1, 4), 5);
        String prompt = buildPrompt(question, hits);
        long start = System.nanoTime();
        AiGatewayClient.ChatResult chat = gatewayClient.chat(prompt, 0.2, 2);
        int latency = (int) ((System.nanoTime() - start) / 1_000_000);
        long userMessageId = IdWorker.getId();
        long assistantMessageId = IdWorker.getId();
        copilotMapper.insertMessage(userMessageId, tenantId, conversationId, "USER", question, "TEXT",
                null, null, null, null, null, "[]", userId);
        copilotMapper.insertMessage(assistantMessageId, tenantId, conversationId, "ASSISTANT", chat.answer(),
                "MARKDOWN", chat.tokenIn(), chat.tokenOut(),
                IntelligenceContext.string(IntelligenceContext.first(body, "model_code", "modelCode")),
                latency, (int) chat.ttftMs(), "[]", userId);
        List<Map<String, Object>> citations = new ArrayList<>();
        int limit = Math.min(hits.size(), 3);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> hit = hits.get(i);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("document_id", Rows.lngVal(hit, "document_id"));
            citation.put("chunk_id", Rows.lngVal(hit, "chunk_id"));
            citation.put("document_title", Rows.str(hit, "document_title"));
            citation.put("score", Rows.dec(hit, "score"));
            citation.put("snippet", truncate(Rows.str(hit, "content"), 160));
            citation.put("page_no", Rows.intg(hit, "page_no"));
            copilotMapper.insertCitation(IdWorker.getId(), tenantId, assistantMessageId,
                    Rows.lngVal(hit, "document_id"), Rows.lngVal(hit, "chunk_id"),
                    Rows.dec(hit, "score") == null ? BigDecimal.ZERO : Rows.dec(hit, "score"),
                    truncate(Rows.str(hit, "content"), 200), Rows.intg(hit, "page_no"), userId);
            citations.add(citation);
        }
        copilotMapper.increaseMessageCount(tenantId, conversationId, userId);
        audit.low("copilot.message", "conversation", conversationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation_id", conversationId);
        result.put("user_message_id", userMessageId);
        result.put("assistant_message_id", assistantMessageId);
        result.put("answer", chat.answer());
        result.put("model_degraded", chat.degraded());
        result.put("latency_ms", latency);
        result.put("citations", citations);
        result.put("conversation", Map.of("id", conversationId, "title", Rows.str(conversation, "title")));
        return result;
    }

    private String buildPrompt(String question, List<Map<String, Object>> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是丰麟云智知识助手。只依据以下资料回答；资料不足时明确说明，不要编造。\n\n【资料】\n");
        if (hits.isEmpty()) {
            builder.append("（无匹配资料）\n");
        } else {
            for (Map<String, Object> hit : hits) {
                builder.append("- [")
                        .append(Rows.str(hit, "document_title"))
                        .append("#")
                        .append(Rows.intVal(hit, "seq_no"))
                        .append("] ")
                        .append(Rows.str(hit, "content"))
                        .append('\n');
            }
        }
        builder.append("\n【问题】").append(question);
        return builder.toString();
    }

    @Transactional
    public Map<String, Object> feedback(long messageId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (copilotMapper.findMessage(tenantId, messageId) == null) {
            throw new BizException(IntelligenceErrorCode.CONVERSATION_NOT_FOUND);
        }
        String type = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "feedback_type", "feedbackType")));
        long id = IdWorker.getId();
        Object ratingValue = IntelligenceContext.first(body, "rating");
        copilotMapper.insertFeedback(id, tenantId, messageId, userId, type,
                ratingValue == null ? null : IntelligenceContext.intValue(ratingValue, 0),
                IntelligenceContext.string(IntelligenceContext.first(body, "comment")),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "corrected_doc_id", "correctedDocId")));
        audit.low("copilot.feedback", "message", messageId);
        return Map.of("id", id, "message_id", messageId, "feedback_type", type);
    }

    public Map<String, Object> textAssist(String mode, Map<String, Object> body) {
        String text = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "text", "content")));
        String instruction = switch (mode) {
            case "WRITE" -> "请基于以下要点撰写一段正式、简洁的中文文稿：";
            case "SUMMARIZE" -> "请用要点总结以下内容，保留关键数字与结论：";
            case "TRANSLATE" -> "请将以下内容翻译为"
                    + defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "target_lang", "targetLang")), "英文")
                    + "，保持术语准确：";
            default -> "请处理以下内容：";
        };
        AiGatewayClient.ChatResult chat = gatewayClient.chat(instruction + "\n" + text, 0.3, 2);
        aiAudit(mode);
        return Map.of("mode", mode, "content", chat.answer(), "token_in", chat.tokenIn(),
                "token_out", chat.tokenOut(), "degraded", chat.degraded());
    }

    @Transactional
    public Map<String, Object> createAgent(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        Map<String, Object> existing = copilotMapper.findAgentByCode(tenantId, code);
        int version = IntelligenceContext.intValue(IntelligenceContext.first(body, "version_no", "versionNo"),
                existing == null ? 1 : Rows.intVal(existing, "version_no") + 1);
        long id = IdWorker.getId();
        copilotMapper.insertAgent(id, tenantId, code,
                IntelligenceContext.requireText(IntelligenceContext.string(IntelligenceContext.first(body, "name"))),
                IntelligenceContext.string(IntelligenceContext.first(body, "description")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "nodes")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "tools")),
                IntelligenceContext.string(IntelligenceContext.first(body, "model_code", "modelCode")),
                version,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "status")), "DRAFT"),
                IntelligenceContext.intValue(IntelligenceContext.first(body, "max_steps", "maxSteps"), 20), userId);
        audit.low("agent.create", "agent", id);
        return Map.of("id", id, "code", code, "version_no", version);
    }

    public PageResult<Map<String, Object>> listAgents(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(copilotMapper.listAgents(tenantId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                copilotMapper.countAgents(tenantId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> runAgent(String code, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> agent = copilotMapper.findAgentByCode(tenantId, code);
        if (agent == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID);
        }
        if (!"PUBLISHED".equals(Rows.str(agent, "status"))) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "智能体未发布");
        }
        int maxSteps = Rows.intVal(agent, "max_steps");
        Map<String, Object> input = asMap(IntelligenceContext.first(body, "input"));
        String prompt = "智能体「" + Rows.str(agent, "name") + "」执行任务。编排节点："
                + Rows.str(agent, "nodes") + "\n输入：" + IntelligenceMaps.json(input);
        AiGatewayClient.ChatResult chat = gatewayClient.chat(prompt, 0.2, 2);
        long id = IdWorker.getId();
        copilotMapper.insertExecution(id, tenantId, Rows.lngVal(agent, "id"),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "conversation_id", "conversationId")),
                userId, "SUCCESS", IntelligenceMaps.json(input),
                IntelligenceMaps.json(Map.of("answer", chat.answer())),
                IntelligenceMaps.json(List.of(Map.of("node", "LLM", "status", "SUCCESS"))),
                1, chat.tokenIn() + chat.tokenOut(), null);
        if (1 > maxSteps) {
            throw new BizException(IntelligenceErrorCode.AGENT_STEP_EXCEEDED);
        }
        audit.low("agent.run", "agent_execution", id);
        Map<String, Object> result = new LinkedHashMap<>(copilotMapper.findExecution(tenantId, id));
        result.put("answer", chat.answer());
        return result;
    }

    public Map<String, Object> guardrailAck(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("managed_by", "platform-ai");
        result.put("owner_unit", "fenglin-ai-service / hub_ai_*");
        result.put("accepted", true);
        result.put("config", IntelligenceContext.first(body, "config"));
        result.put("note", "护栏策略的实际生效与存储由平台 AI 中台负责，云智仅透传租户配置意图");
        return result;
    }

    private void aiAudit(String mode) {
        if ("WRITE".equals(mode) || "SUMMARIZE".equals(mode) || "TRANSLATE".equals(mode)) {
            audit.low("copilot.text." + mode.toLowerCase(), "ai_text", mode);
        }
    }

    private Map<String, Object> requireConversation(long tenantId, long conversationId) {
        Map<String, Object> conversation = copilotMapper.findConversation(tenantId, conversationId);
        if (conversation == null || "ARCHIVED".equals(Rows.str(conversation, "status"))) {
            throw new BizException(IntelligenceErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", value);
    }

    private String kbCsv(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return IntelligenceContext.csv(iterable);
        }
        if (value instanceof Object[] array) {
            return IntelligenceContext.csv(List.of(array));
        }
        return IntelligenceContext.text(value == null ? null : String.valueOf(value));
    }

    private String truncate(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
