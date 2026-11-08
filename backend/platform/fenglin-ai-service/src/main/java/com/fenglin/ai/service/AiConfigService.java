package com.fenglin.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.ai.AiErrorCode;
import com.fenglin.ai.mapper.AiModelMapper;
import com.fenglin.ai.mapper.AiPromptMapper;
import com.fenglin.ai.web.dto.AiDtos.ModelCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.ModelUpdateRequest;
import com.fenglin.ai.web.dto.AiDtos.ModelView;
import com.fenglin.ai.web.dto.AiDtos.PromptCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.PromptView;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HUB-API-1301 模型配置 / HUB-API-1302 提示词管理。
 */
@Service
public class AiConfigService {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9._-]{2,63}");
    private static final Set<String> PROVIDER_TYPES = Set.of("PUBLIC_CLOUD", "PRIVATE_CLOUD", "LOCAL");
    private static final Set<String> MODEL_TYPES = Set.of("LLM", "EMBEDDING", "RERANK", "OCR");
    private static final Set<String> SCENES = Set.of("RAG", "CHAT", "SUMMARY", "CLASSIFY");

    private final AiModelMapper modelMapper;
    private final AiPromptMapper promptMapper;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public AiConfigService(AiModelMapper modelMapper, AiPromptMapper promptMapper,
                           AesGcmCipher cipher, ObjectMapper objectMapper, AuditSink auditSink) {
        this.modelMapper = modelMapper;
        this.promptMapper = promptMapper;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public ModelView createModel(ModelCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String code = requireCode(request.code());
        String providerType = normalize(request.providerType());
        if (!PROVIDER_TYPES.contains(providerType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "provider_type 仅支持 PUBLIC_CLOUD/PRIVATE_CLOUD/LOCAL");
        }
        String modelType = normalize(request.modelType());
        if (!MODEL_TYPES.contains(modelType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "model_type 仅支持 LLM/EMBEDDING/RERANK/OCR");
        }
        String endpoint = require(request.endpoint(), "endpoint");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "endpoint 必须以 http(s):// 开头");
        }
        String apiKey = require(request.apiKey(), "apiKey");
        int routingWeight = request.routingWeight() == null ? 100 : request.routingWeight();
        if (routingWeight < 1 || routingWeight > 1000) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "routing_weight 需在 1..1000");
        }
        Map<String, Object> existing = modelMapper.findByCode(tenantId, code);
        if (existing != null) {
            return toModelView(existing);
        }
        long id = IdWorker.getId();
        modelMapper.insert(id, tenantId, code, providerType, modelType, endpoint,
                cipher.encrypt(apiKey, aad(tenantId)), routingWeight, fingerprint(apiKey), operatorId);
        audit(tenantId, operatorId, "AI_MODEL_CONFIGURED", code);
        return new ModelView(id, code, providerType, modelType, endpoint, fingerprint(apiKey),
                routingWeight, true);
    }

    @Transactional
    public PromptView createPrompt(PromptCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String code = requireCode(request.code());
        String name = require(request.name(), "name");
        String scene = normalize(request.scene());
        if (!SCENES.contains(scene)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "scene 仅支持 RAG/CHAT/SUMMARY/CLASSIFY");
        }
        String systemPrompt = require(request.systemPrompt(), "systemPrompt");
        String userPrompt = require(request.userPrompt(), "userPrompt");
        List<String> variables = request.variables() == null ? List.of() : request.variables();
        String content = systemPrompt + "\n\n" + userPrompt;
        String variablesJson = toJson(variables);

        Map<String, Object> latest = promptMapper.findLatest(tenantId, code);
        if (latest != null && sameContent(latest, name, scene, systemPrompt, userPrompt, variablesJson)) {
            return toPromptView(latest);
        }
        int nextVersion = latest == null ? 1 : intValue(latest.get("prompt_version")) + 1;
        long id = IdWorker.getId();
        promptMapper.insert(id, tenantId, code, nextVersion, content, variablesJson, name, scene,
                systemPrompt, userPrompt, operatorId);
        audit(tenantId, operatorId, "AI_PROMPT_PUBLISHED", code);
        return new PromptView(id, code, nextVersion, name, scene, systemPrompt, userPrompt,
                variables, "PUBLISHED");
    }

    /** 供 RAG 解密调用凭据（AAD 绑定租户）。 */
    @Transactional(readOnly = true)
    public String apiKeyOf(long modelId, long tenantId) {
        String encrypted = modelMapper.findApiKeyEnc(modelId);
        if (encrypted == null) {
            throw new BizException(AiErrorCode.MODEL_DEGRADED, "模型未配置 API Key");
        }
        return cipher.decrypt(encrypted, aad(tenantId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> enabledLlm(long tenantId) {
        Map<String, Object> model = modelMapper.findEnabledLlm(tenantId);
        if (model == null) {
            throw new BizException(AiErrorCode.MODEL_DEGRADED, "未配置可用 LLM 模型");
        }
        return model;
    }

    @Transactional(readOnly = true)
    public PromptView findPrompt(long tenantId, String code) {
        Map<String, Object> latest = promptMapper.findLatest(tenantId, code);
        return latest == null ? null : toPromptView(latest);
    }

    /** 扩展：模型列表（总纲未定义，按管理端展示最小语义实现）。 */
    @Transactional(readOnly = true)
    public PageResult<ModelView> listModels(String modelType, String providerType,
                                            long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String type = blankToNull(modelType);
        String provider = blankToNull(providerType);
        List<ModelView> records = modelMapper.list(tenantId, type, provider,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toModelView)
                .toList();
        return PageResult.of(records, modelMapper.count(tenantId, type, provider), safePage, safeSize);
    }

    /** 扩展：模型更新，apiKey 为空时保留原密钥。 */
    @Transactional
    public ModelView updateModel(long id, ModelUpdateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        Map<String, Object> existing = modelMapper.findById(tenantId, id);
        if (existing == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "模型不存在");
        }
        String providerType = request.providerType() == null
                ? (String) existing.get("provider_type") : normalize(request.providerType());
        if (!PROVIDER_TYPES.contains(providerType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "provider_type 仅支持 PUBLIC_CLOUD/PRIVATE_CLOUD/LOCAL");
        }
        String modelType = request.modelType() == null
                ? (String) existing.get("model_type") : normalize(request.modelType());
        if (!MODEL_TYPES.contains(modelType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "model_type 仅支持 LLM/EMBEDDING/RERANK/OCR");
        }
        String endpoint = request.endpoint() == null
                ? (String) existing.get("base_url") : require(request.endpoint(), "endpoint");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "endpoint 必须以 http(s):// 开头");
        }
        int routingWeight = request.routingWeight() == null
                ? intValue(existing.get("routing_weight")) : request.routingWeight();
        if (routingWeight < 1 || routingWeight > 1000) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "routing_weight 需在 1..1000");
        }
        int enabled = request.enabled() == null
                ? intValue(existing.get("enabled")) : (request.enabled() ? 1 : 0);
        String apiKeyEnc = null;
        String apiKeyFingerprint = null;
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String apiKey = request.apiKey().trim();
            apiKeyEnc = cipher.encrypt(apiKey, aad(tenantId));
            apiKeyFingerprint = fingerprint(apiKey);
        }
        modelMapper.update(tenantId, id, providerType, modelType, endpoint, apiKeyEnc,
                apiKeyFingerprint, routingWeight, enabled, operatorId);
        audit(tenantId, operatorId, "AI_MODEL_UPDATED", (String) existing.get("code"));
        return toModelView(modelMapper.findById(tenantId, id));
    }

    /** 扩展：模型软删除。 */
    @Transactional
    public void deleteModel(long id, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> existing = modelMapper.findById(tenantId, id);
        if (existing == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "模型不存在");
        }
        modelMapper.softDelete(tenantId, id, operatorId);
        audit(tenantId, operatorId, "AI_MODEL_DELETED", (String) existing.get("code"));
    }

    /** 扩展：提示词列表（每个 code 仅返回最新版本）。 */
    @Transactional(readOnly = true)
    public PageResult<PromptView> listPrompts(String scene, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String sceneFilter = blankToNull(scene);
        List<PromptView> records = promptMapper.listLatest(tenantId, sceneFilter,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toPromptView)
                .toList();
        return PageResult.of(records, promptMapper.countLatest(tenantId, sceneFilter),
                safePage, safeSize);
    }

    /** 扩展：提示词更新，内容变化时按 1302 既有语义追加新版本。 */
    @Transactional
    public PromptView updatePrompt(String code, PromptCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String normalizedCode = requireCode(code);
        if (promptMapper.findLatest(tenantId, normalizedCode) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "提示词不存在");
        }
        return createPrompt(new PromptCreateRequest(normalizedCode, request.name(), request.scene(),
                request.systemPrompt(), request.userPrompt(), request.variables()), operatorId);
    }

    /** 扩展：提示词软删除（该 code 全部版本）。 */
    @Transactional
    public void deletePrompt(String code, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String normalizedCode = requireCode(code);
        int affected = promptMapper.softDeleteByCode(tenantId, normalizedCode, operatorId);
        if (affected == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "提示词不存在");
        }
        audit(tenantId, operatorId, "AI_PROMPT_DELETED", normalizedCode);
    }

    private boolean sameContent(Map<String, Object> latest, String name, String scene,
                                String systemPrompt, String userPrompt, String variablesJson) {
        return name.equals(latest.get("name"))
                && scene.equals(latest.get("scene"))
                && systemPrompt.equals(latest.get("system_prompt"))
                && userPrompt.equals(latest.get("user_prompt"))
                && parseVariables(variablesJson).equals(parseVariables((String) latest.get("variables_text")));
    }

    private ModelView toModelView(Map<String, Object> row) {
        return new ModelView(longValue(row.get("id")), (String) row.get("code"),
                (String) row.get("provider_type"), (String) row.get("model_type"),
                (String) row.get("base_url"), (String) row.get("api_key_fingerprint"),
                intValue(row.get("routing_weight")), intValue(row.get("enabled")) == 1);
    }

    private PromptView toPromptView(Map<String, Object> row) {
        return new PromptView(longValue(row.get("id")), (String) row.get("code"),
                intValue(row.get("prompt_version")), (String) row.get("name"),
                (String) row.get("scene"), (String) row.get("system_prompt"),
                (String) row.get("user_prompt"), parseVariables((String) row.get("variables_text")),
                (String) row.get("status"));
    }

    private List<String> parseVariables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String aad(long tenantId) {
        return "hub_ai_model:" + tenantId + ":api_key";
    }

    private String fingerprint(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "API Key 指纹计算失败");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "variables 不是合法 JSON");
        }
    }

    private String requireCode(String code) {
        String value = require(code, "code");
        if (!CODE.matcher(value).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "code 需小写字母开头，仅含小写字母/数字/./_/-");
        }
        return value;
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private void audit(long tenantId, Long operatorId, String eventType, String bizId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), eventType, "ai", "UPDATE",
                "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "AI_CONFIG", bizId, Instant.now()));
    }
}
