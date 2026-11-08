package com.fenglin.audit.web.dto;

import java.time.Instant;

/**
 * 审计日志导出请求（HUB-API-802）。format 仅支持 CSV，eventType 缺省或 ALL 表示不限。
 */
public record ExportAuditRequest(
        Instant from,
        Instant to,
        String eventType,
        String format) {
}
