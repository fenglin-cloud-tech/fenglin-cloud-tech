package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.TenantConfigMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.TenantConfigUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TenantConfigView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 租户集成与度量配置：承载文档 API-59 预留的集成管理配置（时区、留存期、DORA 目标、Webhook 基址）。
 * RND-09：配置按环境（dev/test/staging/prod）各存一份，未指定时按 prod 处理，兼容原单条配置。
 */
@Service
@Transactional(readOnly = true)
public class TenantConfigService {

    static final String DEFAULT_ENV = "prod";
    private static final Set<String> ENVS = Set.of("dev", "test", "staging", "prod");
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final int DEFAULT_RETENTION_MONTHS = 24;
    private static final String DEFAULT_DORA_LEVEL = "high";

    private final TenantConfigMapper tenantConfigMapper;
    private final RndAudit audit;
    private final ObjectMapper objectMapper;

    public TenantConfigService(TenantConfigMapper tenantConfigMapper, RndAudit audit,
                               ObjectMapper objectMapper) {
        this.tenantConfigMapper = tenantConfigMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public TenantConfigView get(String env) {
        String targetEnv = normalizeEnv(env);
        return toView(tenantConfigMapper.find(RndContext.tenantId(), targetEnv), targetEnv);
    }

    @Transactional
    public TenantConfigView update(TenantConfigUpdateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String env = normalizeEnv(request.env());
        Map<String, Object> existing = tenantConfigMapper.find(tenantId, env);
        Map<String, Object> configJson = request.configJson() == null
                ? parse(existing == null ? null : Rows.str(existing, "configJson"))
                : request.configJson();
        String timezone = request.timezone() == null
                ? (existing == null ? DEFAULT_TIMEZONE : Rows.str(existing, "timezone")) : request.timezone();
        int retention = request.dataRetentionMonths() == null
                ? (existing == null ? DEFAULT_RETENTION_MONTHS : Rows.intVal(existing, "dataRetentionMonths"))
                : request.dataRetentionMonths();
        String doraLevel = request.doraTargetLevel() == null
                ? (existing == null ? DEFAULT_DORA_LEVEL : Rows.str(existing, "doraTargetLevel"))
                : request.doraTargetLevel();
        String webhookBaseUrl = request.webhookBaseUrl() == null
                ? (existing == null ? null : Rows.str(existing, "webhookBaseUrl")) : request.webhookBaseUrl();
        tenantConfigMapper.upsert(IdWorker.getId(), tenantId, env, write(configJson), timezone, retention,
                doraLevel, webhookBaseUrl, userId);
        audit.publish("TENANT_CONFIG_UPDATED", "update", "devops_tenant_config", tenantId, "high");
        return get(env);
    }

    /** 环境值统一小写；空值按 prod，非法值直接 400，不做静默兜底。 */
    private String normalizeEnv(String env) {
        if (env == null || env.isBlank()) {
            return DEFAULT_ENV;
        }
        String value = env.trim().toLowerCase(Locale.ROOT);
        if (!ENVS.contains(value)) {
            throw new BizException(RndErrorCode.PARAM_INVALID,
                    "env 仅支持 dev/test/staging/prod");
        }
        return value;
    }

    private TenantConfigView toView(Map<String, Object> row, String env) {
        if (row == null) {
            return new TenantConfigView(null, env, Map.of(), DEFAULT_TIMEZONE, DEFAULT_RETENTION_MONTHS,
                    DEFAULT_DORA_LEVEL, null, null);
        }
        return new TenantConfigView(Rows.lng(row, "id"), Rows.str(row, "env"),
                parse(Rows.str(row, "configJson")),
                Rows.str(row, "timezone"), Rows.intVal(row, "dataRetentionMonths"),
                Rows.str(row, "doraTargetLevel"), Rows.str(row, "webhookBaseUrl"), Rows.str(row, "updatedAt"));
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
    }

    private String write(Map<String, Object> configJson) {
        try {
            return objectMapper.writeValueAsString(configJson);
        } catch (JsonProcessingException e) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
    }
}
