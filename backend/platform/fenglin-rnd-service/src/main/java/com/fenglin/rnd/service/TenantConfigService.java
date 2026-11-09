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

import java.util.Map;

/**
 * 租户集成与度量配置：承载文档 API-59 预留的集成管理配置（时区、留存期、DORA 目标、Webhook 基址）。
 */
@Service
@Transactional(readOnly = true)
public class TenantConfigService {

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

    public TenantConfigView get() {
        return toView(tenantConfigMapper.find(RndContext.tenantId()));
    }

    @Transactional
    public TenantConfigView update(TenantConfigUpdateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        Map<String, Object> existing = tenantConfigMapper.find(tenantId);
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
        tenantConfigMapper.upsert(IdWorker.getId(), tenantId, write(configJson), timezone, retention,
                doraLevel, webhookBaseUrl, userId);
        audit.publish("TENANT_CONFIG_UPDATED", "update", "devops_tenant_config", tenantId, "high");
        return get();
    }

    private TenantConfigView toView(Map<String, Object> row) {
        if (row == null) {
            return new TenantConfigView(null, Map.of(), DEFAULT_TIMEZONE, DEFAULT_RETENTION_MONTHS,
                    DEFAULT_DORA_LEVEL, null, null);
        }
        return new TenantConfigView(Rows.lng(row, "id"), parse(Rows.str(row, "configJson")),
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
