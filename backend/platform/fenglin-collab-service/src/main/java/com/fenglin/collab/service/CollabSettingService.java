package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.CollabSettingMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyView;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyView;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 租户级协作配置：敏感词策略（IM-R04）、文档密级策略（DOC-R06）。
 * 配置持久化在 collab_setting，按 setting_key 区分；未配置时使用文档默认值。
 */
@Service
public class CollabSettingService {

    public static final String KEY_SENSITIVE_WORDS = "im.sensitive_words";
    public static final String KEY_DOC_POLICY = "doc.policy";

    private static final Set<String> ACTIONS = Set.of("BLOCK", "REPLACE");
    private static final Set<String> SCOPES = Set.of("TENANT", "DEPT");

    private final CollabSettingMapper settingMapper;
    private final CollabAudit audit;
    private final ObjectMapper objectMapper;

    public CollabSettingService(CollabSettingMapper settingMapper, CollabAudit audit, ObjectMapper objectMapper) {
        this.settingMapper = settingMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public record SensitivePolicy(List<String> words, String action, boolean enabled) {
    }

    public record DocPolicy(boolean confidentialNoLink, boolean confidentialNoDownload,
                            String watermarkTemplate, int maxConcurrentEditors) {
    }

    @Transactional(readOnly = true)
    public SensitivePolicy sensitivePolicy() {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> row = settingMapper.find(tenantId, KEY_SENSITIVE_WORDS);
        if (row == null) {
            return new SensitivePolicy(List.of(), "BLOCK", false);
        }
        Map<String, Object> content = parse(Rows.str(row, "content"));
        List<String> words = stringList(content.get("words"));
        String action = content.get("action") == null ? "BLOCK" : String.valueOf(content.get("action"));
        boolean enabled = Boolean.parseBoolean(String.valueOf(content.getOrDefault("enabled", true)));
        return new SensitivePolicy(words, action, enabled);
    }

    /**
     * 文本消息发送前过滤：命中按策略拦截或替换。非文本消息由文件 DLP 负责，不在此处理。
     */
    @Transactional(readOnly = true)
    public String applySensitiveFilter(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        SensitivePolicy policy = sensitivePolicy();
        if (!policy.enabled() || policy.words().isEmpty()) {
            return content;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        String hit = policy.words().stream()
                .filter(word -> !word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (hit == null) {
            return content;
        }
        audit.publish("COLLAB_SENSITIVE_WORD_HIT", "READ", "collab_message", null, "LOW");
        if ("REPLACE".equals(policy.action())) {
            String filtered = content;
            for (String word : policy.words()) {
                if (!word.isBlank()) {
                    filtered = filtered.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
                }
            }
            return filtered;
        }
        throw new BizException(CollabErrorCode.MESSAGE_SENSITIVE);
    }

    @Transactional(readOnly = true)
    public DocPolicy docPolicy() {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> row = settingMapper.find(tenantId, KEY_DOC_POLICY);
        if (row == null) {
            return new DocPolicy(true, true, null, 50);
        }
        Map<String, Object> content = parse(Rows.str(row, "content"));
        return new DocPolicy(
                Boolean.parseBoolean(String.valueOf(content.getOrDefault("confidentialNoLink", true))),
                Boolean.parseBoolean(String.valueOf(content.getOrDefault("confidentialNoDownload", true))),
                content.get("watermarkTemplate") == null ? null : String.valueOf(content.get("watermarkTemplate")),
                content.get("maxConcurrentEditors") == null ? 50
                        : Integer.parseInt(String.valueOf(content.get("maxConcurrentEditors"))));
    }

    @Transactional
    public SensitiveWordPolicyView saveSensitivePolicy(SensitiveWordPolicyRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "敏感词策略不能为空");
        }
        List<String> words = request.words() == null ? List.of() : request.words().stream()
                .filter(word -> word != null && !word.isBlank())
                .map(String::trim).filter(word -> word.length() <= 64)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        if (words.size() > 500) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "敏感词数量不能超过 500");
        }
        String action = request.action() == null ? "BLOCK" : request.action().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "敏感词策略必须为 BLOCK/REPLACE");
        }
        String scope = request.scope() == null ? "TENANT" : request.scope().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "生效范围必须为 TENANT/DEPT");
        }
        boolean enabled = request.enabled() == null || request.enabled();
        String json = write(Map.of("words", words, "action", action, "scope", scope, "enabled", enabled));
        settingMapper.upsert(IdWorker.getId(), tenantId, KEY_SENSITIVE_WORDS, json, userId);
        Map<String, Object> row = settingMapper.find(tenantId, KEY_SENSITIVE_WORDS);
        audit.publish("COLLAB_IM_SENSITIVE_POLICY_UPDATED", "UPDATE", "collab_setting",
                Rows.lngVal(row, "settingId"), "MEDIUM");
        return new SensitiveWordPolicyView(Rows.lngVal(row, "settingId"), words.size(), action, scope, enabled);
    }

    @Transactional
    public DocPolicyView saveDocPolicy(DocPolicyRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "文档策略不能为空");
        }
        int maxEditors = request.maxConcurrentEditors() == null ? 50 : request.maxConcurrentEditors();
        if (maxEditors < 1 || maxEditors > 1000) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "并发编辑人数需在 1-1000");
        }
        DocPolicyView view = new DocPolicyView(
                request.confidentialNoLink() == null || request.confidentialNoLink(),
                request.confidentialNoDownload() == null || request.confidentialNoDownload(),
                request.watermarkTemplate(), maxEditors);
        settingMapper.upsert(IdWorker.getId(), tenantId, KEY_DOC_POLICY, write(Map.of(
                "confidentialNoLink", view.confidentialNoLink(),
                "confidentialNoDownload", view.confidentialNoDownload(),
                "watermarkTemplate", view.watermarkTemplate() == null ? "" : view.watermarkTemplate(),
                "maxConcurrentEditors", view.maxConcurrentEditors())), userId);
        audit.publish("COLLAB_DOC_POLICY_UPDATED", "UPDATE", "collab_setting", null, "MEDIUM");
        return view;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private String write(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "策略内容格式非法");
        }
    }
}
