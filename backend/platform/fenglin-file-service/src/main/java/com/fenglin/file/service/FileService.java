package com.fenglin.file.service;

import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.file.config.FileProperties;
import com.fenglin.file.storage.StorageService;
import com.fenglin.file.web.dto.DownloadResult;
import com.fenglin.file.web.dto.FastCheckResult;
import com.fenglin.file.web.dto.PreviewResult;
import com.fenglin.file.web.dto.UploadResult;
import com.fenglin.file.web.dto.VersionListResult;
import com.fenglin.file.web.dto.VersionView;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final long MB = 1024L * 1024L;
    private static final BigDecimal GB = BigDecimal.valueOf(1024L * 1024L * 1024L);
    private static final Set<String> EXECUTABLE_EXT = Set.of(
            "exe", "bat", "cmd", "sh", "ps1", "msi", "dll", "js", "vbs", "jar", "scr", "com", "pif", "apk");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "avi", "mov", "mkv", "webm");
    private static final Set<String> AUDIO_EXT = Set.of("mp3", "wav", "flac", "aac", "m4a");
    private static final Set<String> OFFICE_EXT = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods");
    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "csv", "rtf");

    private final StorageService storage;
    private final FileRegistry registry;
    private final FilePolicyService policyService;
    private final FileProperties properties;
    private final AuditSink auditSink;

    public FileService(StorageService storage,
                       FileRegistry registry,
                       FilePolicyService policyService,
                       FileProperties properties,
                       AuditSink auditSink) {
        this.storage = storage;
        this.registry = registry;
        this.policyService = policyService;
        this.properties = properties;
        this.auditSink = auditSink;
    }

    public UploadResult upload(MultipartFile part,
                               Integer chunkIndex,
                               Integer totalChunks,
                               String md5,
                               String bizType,
                               String bizId,
                               Integer secLevel) {
        long tenantId = TenantContext.requireTenantId();
        if (part == null || part.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        FilePolicy policy = policyService.current(tenantId);
        String name = part.getOriginalFilename() == null || part.getOriginalFilename().isBlank()
                ? "unnamed" : part.getOriginalFilename();
        String ext = extOf(name);
        assertAllowed(policy, ext);
        int total = totalChunks == null || totalChunks < 1 ? 1 : totalChunks;
        int index = chunkIndex == null ? 0 : chunkIndex;
        if (index < 0 || index >= total) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "chunkIndex 超出 0..totalChunks-1 范围");
        }
        int level = secLevel == null ? 1 : secLevel;
        if (level < 1 || level > 5) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "secLevel 必须在 1..5 之间");
        }
        return total == 1
                ? uploadSingle(tenantId, policy, part, name, ext, md5, bizType, bizId, level)
                : uploadChunk(tenantId, policy, part, name, ext, md5, bizType, bizId, level, index, total);
    }

    public FastCheckResult fastCheck(String md5, Long size, String name) {
        long tenantId = TenantContext.requireTenantId();
        if (name != null) {
            assertAllowed(policyService.current(tenantId), extOf(name));
        }
        Map<String, Object> existing = registry.findReusable(tenantId, normalizeMd5(md5), size);
        if (existing == null) {
            return new FastCheckResult(false, null, null);
        }
        return new FastCheckResult(true, longValue(existing.get("id")), (String) existing.get("file_no"));
    }

    public DownloadResult download(long fileId, Integer version) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> file = requireFile(tenantId, fileId);
        assertAvailable(file);
        String objectKey = (String) file.get("object_key");
        if (version != null) {
            Map<String, Object> versionRow = registry.findVersion(tenantId, fileId, version);
            if (versionRow == null) {
                throw new BizException(CommonErrorCode.NOT_FOUND, "文件版本不存在: " + version);
            }
            objectKey = (String) versionRow.get("object_key");
        }
        FilePolicy policy = policyService.current(tenantId);
        boolean watermark = policy.watermarkEnabled()
                && intValue(file.get("sec_level")) >= policy.watermarkSecLevelFrom();
        String url = storage.presignedGetUrl(objectKey, properties.presignMinutes());
        audit(tenantId, "FILE_DOWNLOADED", "FILE", (String) file.get("file_no"), "MEDIUM");
        return new DownloadResult(url, watermark);
    }

    public PreviewResult preview(long fileId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> file = requireFile(tenantId, fileId);
        assertAvailable(file);
        String ext = (String) file.get("ext");
        FilePolicy policy = policyService.current(tenantId);
        boolean watermark = policy.watermarkEnabled()
                && intValue(file.get("sec_level")) >= policy.watermarkSecLevelFrom();
        String url = storage.presignedGetUrl((String) file.get("object_key"), properties.presignMinutes());
        String watermarkText = watermark ? watermarkText(tenantId) : null;
        audit(tenantId, "FILE_PREVIEWED", "FILE", (String) file.get("file_no"), "LOW");
        return new PreviewResult(previewType(ext), url, watermarkText);
    }

    public VersionListResult versions(long fileId) {
        long tenantId = TenantContext.requireTenantId();
        requireFile(tenantId, fileId);
        List<VersionView> views = registry.versions(tenantId, fileId).stream()
                .map(row -> new VersionView(
                        intValue(row.get("ver_no")),
                        longValue(row.get("size")),
                        (String) row.get("md5"),
                        row.get("created_by") == null ? null : longValue(row.get("created_by")),
                        toInstant(row.get("created_at"))))
                .toList();
        return new VersionListResult(views);
    }

    private UploadResult uploadSingle(long tenantId, FilePolicy policy, MultipartFile part, String name,
                                      String ext, String md5, String bizType, String bizId, int secLevel) {
        byte[] bytes;
        try {
            bytes = part.getBytes();
        } catch (IOException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "读取上传内容失败");
        }
        String actualMd5 = md5Hex(bytes);
        String expectedMd5 = normalizeMd5(md5);
        if (expectedMd5 != null && !expectedMd5.equals(actualMd5)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "文件 MD5 校验失败");
        }
        long size = bytes.length;
        assertFileSize(policy, size);
        Map<String, Object> existing = registry.findReusable(tenantId, actualMd5, size);
        if (existing != null) {
            return toUploadResult(existing, "AVAILABLE");
        }
        assertQuota(tenantId, policy, size);
        long fileId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        String objectKey = objectKey(tenantId, fileId, ext);
        storage.putObject(objectKey, new ByteArrayInputStream(bytes), size, part.getContentType());
        Map<String, Object> row = registry.register(tenantId,
                new FileRegistry.FileRegistration(name, ext, size, actualMd5, properties.bucket(),
                        objectKey, bizType, bizId, secLevel));
        audit(tenantId, "FILE_UPLOADED", "FILE", (String) row.get("file_no"), "MEDIUM");
        return toUploadResult(row, "AVAILABLE");
    }

    private UploadResult uploadChunk(long tenantId, FilePolicy policy, MultipartFile part, String name,
                                     String ext, String md5, String bizType, String bizId, int secLevel,
                                     int index, int total) {
        String chunkMd5 = normalizeMd5(md5);
        if (chunkMd5 == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "分片上传必须提供 md5");
        }
        String chunkKey = "_chunks/" + tenantId + "/" + chunkMd5 + "/" + index;
        try (InputStream stream = part.getInputStream()) {
            storage.putObject(chunkKey, stream, part.getSize(), "application/octet-stream");
        } catch (IOException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "读取分片内容失败");
        }
        long[] sizes = new long[total];
        boolean complete = true;
        long totalSize = 0;
        for (int i = 0; i < total; i++) {
            String key = "_chunks/" + tenantId + "/" + chunkMd5 + "/" + i;
            if (!storage.exists(key)) {
                complete = false;
                break;
            }
            sizes[i] = storage.size(key);
            totalSize += sizes[i];
        }
        if (!complete) {
            return new UploadResult(null, null, "UPLOADING", chunkKey);
        }
        assertFileSize(policy, totalSize);
        Map<String, Object> existing = registry.findReusable(tenantId, chunkMd5, totalSize);
        if (existing != null) {
            cleanupChunks(tenantId, chunkMd5, total);
            return toUploadResult(existing, "AVAILABLE");
        }
        assertQuota(tenantId, policy, totalSize);
        long fileId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        String objectKey = objectKey(tenantId, fileId, ext);
        List<InputStream> streams = new ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                streams.add(storage.openStream("_chunks/" + tenantId + "/" + chunkMd5 + "/" + i));
            }
            InputStream merged = new java.io.SequenceInputStream(java.util.Collections.enumeration(streams));
            storage.putObject(objectKey, merged, totalSize, part.getContentType());
        } finally {
            for (InputStream stream : streams) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // 关闭失败不影响主流程
                }
            }
        }
        cleanupChunks(tenantId, chunkMd5, total);
        Map<String, Object> row = registry.register(tenantId,
                new FileRegistry.FileRegistration(name, ext, totalSize, chunkMd5, properties.bucket(),
                        objectKey, bizType, bizId, secLevel));
        audit(tenantId, "FILE_UPLOADED", "FILE", (String) row.get("file_no"), "MEDIUM");
        return toUploadResult(row, "AVAILABLE");
    }

    private void cleanupChunks(long tenantId, String md5, int total) {
        for (int i = 0; i < total; i++) {
            storage.removeObject("_chunks/" + tenantId + "/" + md5 + "/" + i);
        }
    }

    private Map<String, Object> requireFile(long tenantId, long fileId) {
        Map<String, Object> file = registry.findFile(tenantId, fileId);
        if (file == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "文件不存在: " + fileId);
        }
        return file;
    }

    private void assertAvailable(Map<String, Object> file) {
        String status = (String) file.get("status");
        if (!"AVAILABLE".equals(status)) {
            throw new BizException(CommonErrorCode.CONFLICT, "文件当前状态不可访问: " + status);
        }
    }

    private void assertAllowed(FilePolicy policy, String ext) {
        if (ext == null || ext.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "0006002 文件类型不允许: 缺少扩展名");
        }
        if (EXECUTABLE_EXT.contains(ext)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "0006002 文件类型不允许: " + ext);
        }
        if (!policy.allowedExt().contains(ext)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "0006002 文件类型不允许: " + ext);
        }
    }

    private void assertFileSize(FilePolicy policy, long size) {
        long limit = policy.maxSizeMb() * MB;
        if (size > limit) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "0006001 文件超过大小上限: " + policy.maxSizeMb() + "MB");
        }
    }

    private void assertQuota(long tenantId, FilePolicy policy, long size) {
        BigDecimal quotaGb = policy.storageQuotaGb();
        if (quotaGb == null || quotaGb.signum() <= 0) {
            return;
        }
        BigDecimal limit = quotaGb.multiply(GB);
        BigDecimal used = BigDecimal.valueOf(registry.usedBytes(tenantId)).add(BigDecimal.valueOf(size));
        if (used.compareTo(limit) > 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "0000101 存储配额不足，请清理文件后重试");
        }
    }

    private UploadResult toUploadResult(Map<String, Object> row, String status) {
        return new UploadResult(longValue(row.get("id")), (String) row.get("file_no"),
                status, (String) row.get("object_key"));
    }

    private String objectKey(long tenantId, long fileId, String ext) {
        String month = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "tenant/" + tenantId + "/" + month + "/" + fileId + (ext == null || ext.isBlank() ? "" : "." + ext);
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            return null;
        }
        return md5.trim().toLowerCase(Locale.ROOT);
    }

    private String md5Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "MD5 计算失败");
        }
    }

    private String previewType(String ext) {
        if (ext == null) {
            return "OTHER";
        }
        if ("pdf".equals(ext)) {
            return "PDF";
        }
        if (IMAGE_EXT.contains(ext)) {
            return "IMAGE";
        }
        if (VIDEO_EXT.contains(ext)) {
            return "VIDEO";
        }
        if (AUDIO_EXT.contains(ext)) {
            return "AUDIO";
        }
        if (OFFICE_EXT.contains(ext)) {
            return "OFFICE";
        }
        if (TEXT_EXT.contains(ext)) {
            return "TEXT";
        }
        return "OTHER";
    }

    private String watermarkText(long tenantId) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        String user = snapshot == null || snapshot.userId() == null
                ? "用户" : registry.displayName(tenantId, snapshot.userId());
        return user + " " + LocalDate.now() + " T" + tenantId;
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private void audit(long tenantId, String eventType, String resourceType, String resourceId, String risk) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "file",
                eventType,
                risk,
                tenantId,
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                resourceType,
                resourceId,
                Instant.now()));
    }
}
