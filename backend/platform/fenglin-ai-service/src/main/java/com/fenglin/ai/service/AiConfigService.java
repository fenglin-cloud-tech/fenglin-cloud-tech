package com.fenglin.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.ai.AiErrorCode;
import com.fenglin.ai.mapper.AiModelMapper;
import com.fenglin.ai.mapper.AiPromptMapper;
import com.fenglin.ai.web.dto.AiDtos.ModelCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.ModelView;
import com.fenglin.ai.web.dto.AiDtos.PromptCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.PromptView;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
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
                routingWeight);
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
                intValue(row.get("routing_weight")));
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
