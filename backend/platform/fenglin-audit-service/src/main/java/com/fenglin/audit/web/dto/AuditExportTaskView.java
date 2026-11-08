package com.fenglin.audit.web.dto;

import java.time.Instant;

/**
 * 审计导出任务视图。契约仅要求 taskId/status，补充查询所需的元数据（文档缺口兜底）。
 */
public record AuditExportTaskView(
        long taskId,
        String taskNo,
        String format,
        String eventType,
        Instant from,
        Instant to,
        String status,
        Long rowCount,
        String errorMsg,
        Instant createdAt) {
}
