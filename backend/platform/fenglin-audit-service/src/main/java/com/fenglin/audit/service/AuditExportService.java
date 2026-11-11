package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.mapper.AuditExportTaskMapper;
import com.fenglin.audit.mapper.AuditLogMapper;
import com.fenglin.audit.web.dto.AuditExportTaskView;
import com.fenglin.audit.web.dto.ExportAuditRequest;
import com.fenglin.audit.web.dto.RecordAuditRequest;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 审计日志导出（HUB-API-802）：创建异步任务，由 {@link AuditExportJob} 轮询生成 CSV。
 * 契约未定义任务查询与文件下载，补充 export-tasks 端点（文档缺口兜底）。
 */
@Service
public class AuditExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditExportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TASK_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String[] CSV_HEADERS = {
            "log_no", "event_type", "module", "action", "operator_id", "operator_name",
            "target_type", "target_id", "request_id", "client_type", "result", "risk_level",
            "detail", "occurred_at"
    };

    private final AuditExportTaskMapper taskMapper;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Path exportDir;

    public AuditExportService(AuditExportTaskMapper taskMapper,
                              AuditLogMapper auditLogMapper,
                              AuditService auditService,
                              PlatformTransactionManager transactionManager,
                              @Value("${fenglin.audit.export-dir:${java.io.tmpdir}/fenglin-audit-export}") String exportDir) {
        this.taskMapper = taskMapper;
        this.auditLogMapper = auditLogMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.exportDir = Path.of(exportDir).toAbsolutePath().normalize();
    }

    @Transactional
    public AuditExportTaskView create(ExportAuditRequest request, Long operatorId) {
        String format = normalizeFormat(request.format());
        String eventType = normalizeEventType(request.eventType());
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "导出时间范围不合法：from 必须早于 to");
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BizException(CommonErrorCode.TENANT_REQUIRED);
        }
        long id = IdWorker.getId();
        String taskNo = "AEX" + TASK_NO_TIME.format(java.time.LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        taskMapper.insert(id, tenantId, taskNo, format, eventType, request.from(), request.to(), operatorId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task_no", taskNo);
        detail.put("format", format);
        if (eventType != null) {
            detail.put("event_type", eventType);
        }
        if (request.from() != null) {
            detail.put("from", request.from().toString());
        }
        if (request.to() != null) {
            detail.put("to", request.to().toString());
        }
        auditService.record(new RecordAuditRequest(null, "AUDIT_EXPORT_CREATED", "audit", "EXPORT",
                "MEDIUM", operatorId, null, "AUDIT_EXPORT", taskNo, null, null, "ADMIN", null,
                "SUCCESS", MAPPER.valueToTree(detail), Instant.now()));
        return toView(taskMapper.findById(tenantId, id));
    }

    @Transactional(readOnly = true)
    public AuditExportTaskView get(long taskId) {
        Map<String, Object> row = taskMapper.findById(TenantContext.requireTenantId(), taskId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "导出任务不存在: " + taskId);
        }
        return toView(row);
    }

    @Transactional(readOnly = true)
    public ExportFile download(long taskId) {
        Map<String, Object> row = taskMapper.findById(TenantContext.requireTenantId(), taskId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "导出任务不存在: " + taskId);
        }
        if (!"SUCCEEDED".equals(row.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "导出任务尚未完成: " + taskId);
        }
        String fileName = (String) row.get("file_path");
        Path file = fileName == null ? null : exportDir.resolve(fileName).normalize();
        if (file == null || !file.startsWith(exportDir) || !Files.isRegularFile(file)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "导出文件不存在: " + taskId);
        }
        try {
            return new ExportFile(file.getFileName().toString(), Files.readAllBytes(file));
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "读取导出文件失败");
        }
    }

    /**
     * 执行导出任务：标记 RUNNING → 生成 CSV → 标记 SUCCEEDED/FAILED。
     */
    public void process(long taskId, long tenantId) {
        TenantContext.set(tenantId, null, "SYSTEM", null, false);
        try {
            Integer started = transactionTemplate.execute(status -> taskMapper.markRunning(taskId));
            if (started == null || started == 0) {
                return;
            }
            try {
                Map<String, Object> task = transactionTemplate.execute(status ->
                        taskMapper.findById(tenantId, taskId));
                if (task == null) {
                    return;
                }
                List<Map<String, Object>> rows = transactionTemplate.execute(status ->
                        auditLogMapper.findForExport(tenantId, (String) task.get("event_type"),
                                toInstant(task.get("filter_from")), toInstant(task.get("filter_to"))));
                String fileName = writeCsv(tenantId, (String) task.get("task_no"), rows == null ? List.of() : rows);
                long rowCount = rows == null ? 0 : rows.size();
                transactionTemplate.executeWithoutResult(status -> taskMapper.markSucceeded(taskId, fileName, rowCount));
            } catch (RuntimeException ex) {
                log.error("审计导出任务失败：taskId={} tenant={}", taskId, tenantId, ex);
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                safeMarkFailed(taskId, message);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void safeMarkFailed(long taskId, String message) {
        String trimmed = message.length() > 500 ? message.substring(0, 500) : message;
        try {
            transactionTemplate.executeWithoutResult(status -> taskMapper.markFailed(taskId, trimmed));
        } catch (RuntimeException ex) {
            log.error("审计导出任务失败状态回写失败：taskId={}", taskId, ex);
        }
    }

    private String writeCsv(long tenantId, String taskNo, List<Map<String, Object>> rows) {
        Path tenantDir = exportDir.resolve(String.valueOf(tenantId));
        String fileName = tenantId + "/" + taskNo + ".csv";
        try {
            Files.createDirectories(tenantDir);
            Path file = tenantDir.resolve(taskNo + ".csv");
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write('\uFEFF');
                writer.write(String.join(",", CSV_HEADERS));
                writer.newLine();
                for (Map<String, Object> row : rows) {
                    writer.write(csvLine(row));
                    writer.newLine();
                }
            }
            return fileName;
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "写导出文件失败: " + e.getMessage());
        }
    }

    private String csvLine(Map<String, Object> row) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < CSV_HEADERS.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            Object value = row.get(CSV_HEADERS[i]);
            line.append(escape(value instanceof Instant instant ? instant.toString()
                    : value == null ? "" : String.valueOf(value)));
        }
        return line.toString();
    }

    private String escape(String value) {
        if (value.indexOf('"') < 0 && value.indexOf(',') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private AuditExportTaskView toView(Map<String, Object> row) {
        return new AuditExportTaskView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("task_no"),
                (String) row.get("format"),
                (String) row.get("event_type"),
                toInstant(row.get("filter_from")),
                toInstant(row.get("filter_to")),
                (String) row.get("status"),
                row.get("row_count") == null ? null : ((Number) row.get("row_count")).longValue(),
                (String) row.get("error_msg"),
                toInstant(row.get("created_at")));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private String normalizeFormat(String format) {
        String normalized = format == null || format.isBlank() ? "CSV" : format.trim().toUpperCase();
        if (!"CSV".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "暂仅支持 CSV 导出格式");
        }
        return normalized;
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank() || "ALL".equalsIgnoreCase(eventType.trim())) {
            return null;
        }
        return eventType.trim();
    }

    public record ExportFile(String fileName, byte[] content) {
    }
}
