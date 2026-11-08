package com.fenglin.iam.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.iam.mapper.ParamMapper;
import com.fenglin.iam.web.dto.ParamView;
import com.fenglin.iam.web.dto.UpsertParamRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 参数配置：租户参数覆盖平台默认参数（tenant_id=0），按 value_type 校验取值。
 */
@Service
public class ParamService {

    private static final Set<String> SCOPES = Set.of("TENANT", "PRODUCT");
    private static final Set<String> VALUE_TYPES = Set.of("STRING", "NUMBER", "BOOL", "JSON");

    private final ParamMapper paramMapper;
    private final AuditSink auditSink;
    private final ObjectMapper objectMapper;

    public ParamService(ParamMapper paramMapper, AuditSink auditSink, ObjectMapper objectMapper) {
        this.paramMapper = paramMapper;
        this.auditSink = auditSink;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ParamView> list(String scope, String productCode) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, ParamView> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : paramMapper.list(tenantId, normalize(scope), normalize(productCode))) {
            boolean tenantOwned = ((Number) row.get("tenant_id")).longValue() == tenantId;
            String key = (String) row.get("param_key");
            ParamView existing = merged.get(key);
            if (existing == null || tenantOwned) {
                merged.put(key, toView(row, tenantOwned ? "TENANT" : "PLATFORM"));
            }
        }
        return List.copyOf(merged.values());
    }

    @Transactional(readOnly = true)
    public ParamView get(String paramKey) {
        long tenantId = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = paramMapper.findByKey(tenantId, normalize(paramKey));
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "参数不存在: " + paramKey);
        }
        Map<String, Object> row = rows.get(0);
        boolean tenantOwned = ((Number) row.get("tenant_id")).longValue() == tenantId;
        return toView(row, tenantOwned ? "TENANT" : "PLATFORM");
    }

    @Transactional
    public ParamView upsert(String paramKey, UpsertParamRequest request) {
        long tenantId = TenantContext.requireTenantId();
        String normalizedKey = normalize(paramKey);
        if (normalizedKey == null || !normalizedKey.matches("[A-Za-z0-9_.:-]{1,128}")) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "参数键不合法: " + paramKey);
        }
        String scope = request.scope() == null ? "TENANT" : request.scope().toUpperCase();
        if (!SCOPES.contains(scope)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "scope 仅支持 TENANT/PRODUCT");
        }
        String valueType = request.valueType() == null ? "STRING" : request.valueType().toUpperCase();
        if (!VALUE_TYPES.contains(valueType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "valueType 仅支持 STRING/NUMBER/BOOL/JSON");
        }
        validateValue(normalizedKey, request.value(), valueType);
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        paramMapper.upsert(IdWorker.getId(), tenantId, normalizedKey, request.value(), scope,
                normalize(request.productCode()), valueType, normalize(request.remark()), operatorId);
        audit("IAM_PARAM_SAVED", normalizedKey, operatorId);
        return new ParamView(normalizedKey, request.value(), scope, normalize(request.productCode()),
                valueType, normalize(request.remark()), "TENANT");
    }

    @Transactional
    public void delete(String paramKey) {
        long tenantId = TenantContext.requireTenantId();
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        if (paramMapper.delete(tenantId, normalize(paramKey), operatorId) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "参数不存在或为平台默认项: " + paramKey);
        }
        audit("IAM_PARAM_DELETED", normalize(paramKey), operatorId);
    }

    private void validateValue(String paramKey, String value, String valueType) {
        if (value == null) {
            return;
        }
        try {
            switch (valueType) {
                case "NUMBER" -> new BigDecimal(value.trim());
                case "BOOL" -> {
                    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                        throw new IllegalArgumentException("BOOL 仅允许 true/false");
                    }
                }
                case "JSON" -> objectMapper.readTree(value);
                default -> {
                }
            }
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "参数值不符合 " + valueType + " 类型: " + paramKey);
        }
    }

    private ParamView toView(Map<String, Object> row, String source) {
        return new ParamView(
                (String) row.get("param_key"),
                (String) row.get("param_value"),
                (String) row.get("scope"),
                (String) row.get("product_code"),
                (String) row.get("value_type"),
                (String) row.get("remark"),
                source);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String resourceId, Long operatorId) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "iam",
                eventType,
                "MEDIUM",
                snapshot == null ? null : snapshot.tenantId(),
                operatorId,
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                "PARAM",
                resourceId,
                Instant.now()));
    }
}
