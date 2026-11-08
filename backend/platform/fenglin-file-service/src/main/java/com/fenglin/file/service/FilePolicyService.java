package com.fenglin.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.file.config.FileProperties;
import com.fenglin.file.mapper.FilePolicyMapper;
import com.fenglin.file.web.dto.FilePolicyRequest;
import com.fenglin.file.web.dto.FilePolicyView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件安全策略：无专用表，按平台参数约定存放于 hub_param（模块设计 7.4）。
 */
@Service
public class FilePolicyService {

    static final String KEY_ALLOWED_EXT = "file.policy.allowed_ext";
    static final String KEY_MAX_SIZE_MB = "file.policy.max_size_mb";
    static final String KEY_WATERMARK_ENABLED = "file.policy.watermark_enabled";
    static final String KEY_WATERMARK_SEC_LEVEL_FROM = "file.policy.watermark_sec_level_from";
    static final String KEY_STORAGE_QUOTA_GB = "file.policy.storage_quota_gb";

    static final List<String> DEFAULT_ALLOWED_EXT = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf",
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg",
            "mp4", "avi", "mov", "mkv", "webm", "mp3", "wav", "flac", "aac");

    private final FilePolicyMapper mapper;
    private final FileProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public FilePolicyService(FilePolicyMapper mapper,
                             FileProperties properties,
                             ObjectMapper objectMapper,
                             AuditSink auditSink) {
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional(readOnly = true)
    public FilePolicy current(long tenantId) {
        List<String> allowed = readAllowedExt(tenantId);
        int maxSizeMb = readInt(tenantId, KEY_MAX_SIZE_MB, properties.maxSizeMb());
        boolean watermarkEnabled = readBoolean(tenantId, KEY_WATERMARK_ENABLED, true);
        int watermarkFrom = readInt(tenantId, KEY_WATERMARK_SEC_LEVEL_FROM, 3);
        BigDecimal quota = readDecimal(tenantId, KEY_STORAGE_QUOTA_GB, BigDecimal.ZERO);
        return new FilePolicy(allowed, maxSizeMb, watermarkEnabled, watermarkFrom, quota);
    }

    @Transactional(readOnly = true)
    public FilePolicyView view(long tenantId) {
        FilePolicy policy = current(tenantId);
        return new FilePolicyView(policy.allowedExt(), policy.maxSizeMb(),
                new FilePolicyView.Watermark(policy.watermarkEnabled(), policy.watermarkSecLevelFrom()),
                policy.storageQuotaGb().intValue());
    }

    @Transactional
    public FilePolicyView save(long tenantId, FilePolicyRequest request) {
        FilePolicy existing = current(tenantId);
        List<String> allowed = request.allowedExt() == null ? existing.allowedExt()
                : normalizeExt(request.allowedExt());
        if (allowed.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "allowedExt 不能为空");
        }
        int maxSizeMb = request.maxSizeMb() == null ? existing.maxSizeMb() : request.maxSizeMb();
        if (maxSizeMb < 1 || maxSizeMb > 5120) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "maxSizeMb 必须在 1..5120 之间");
        }
        boolean watermarkEnabled = request.watermark() == null || request.watermark().enabled() == null
                ? existing.watermarkEnabled() : request.watermark().enabled();
        int watermarkFrom = request.watermark() == null || request.watermark().secLevelFrom() == null
                ? existing.watermarkSecLevelFrom() : request.watermark().secLevelFrom();
        if (watermarkFrom < 1 || watermarkFrom > 5) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "watermark.secLevelFrom 必须在 1..5 之间");
        }
        BigDecimal quota = request.storageQuotaGb() == null ? existing.storageQuotaGb()
                : BigDecimal.valueOf(request.storageQuotaGb());
        if (quota.signum() < 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "storageQuotaGb 不能为负数");
        }
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        upsert(tenantId, KEY_ALLOWED_EXT, writeJson(allowed), "JSON", operatorId);
        upsert(tenantId, KEY_MAX_SIZE_MB, String.valueOf(maxSizeMb), "NUMBER", operatorId);
        upsert(tenantId, KEY_WATERMARK_ENABLED, String.valueOf(watermarkEnabled), "BOOLEAN", operatorId);
        upsert(tenantId, KEY_WATERMARK_SEC_LEVEL_FROM, String.valueOf(watermarkFrom), "NUMBER", operatorId);
        upsert(tenantId, KEY_STORAGE_QUOTA_GB, quota.stripTrailingZeros().toPlainString(), "NUMBER", operatorId);
        audit(tenantId, "FILE_POLICY_UPDATED");
        return new FilePolicyView(allowed, maxSizeMb, new FilePolicyView.Watermark(watermarkEnabled, watermarkFrom),
                quota.intValue());
    }

    private void upsert(long tenantId, String key, String value, String type, Long operatorId) {
        mapper.upsert(IdWorker.getId(), tenantId, key, value, type, "文件中心策略", operatorId);
    }

    private List<String> readAllowedExt(long tenantId) {
        String value = mapper.findValue(tenantId, KEY_ALLOWED_EXT);
        if (value == null || value.isBlank()) {
            return DEFAULT_ALLOWED_EXT;
        }
        try {
            List<String> parsed = objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
            return parsed.isEmpty() ? DEFAULT_ALLOWED_EXT : normalizeExt(parsed);
        } catch (Exception ex) {
            return DEFAULT_ALLOWED_EXT;
        }
    }

    private int readInt(long tenantId, String key, int fallback) {
        String value = mapper.findValue(tenantId, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean readBoolean(long tenantId, String key, boolean fallback) {
        String value = mapper.findValue(tenantId, key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private BigDecimal readDecimal(long tenantId, String key, BigDecimal fallback) {
        String value = mapper.findValue(tenantId, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<String> normalizeExt(List<String> source) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ext : source) {
            if (ext == null || ext.isBlank()) {
                continue;
            }
            String value = ext.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith(".")) {
                value = value.substring(1);
            }
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "策略序列化失败");
        }
    }

    private void audit(long tenantId, String eventType) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "file",
                eventType,
                "MEDIUM",
                tenantId,
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                "FILE_POLICY",
                "file.policy",
                Instant.now()));
    }
}
