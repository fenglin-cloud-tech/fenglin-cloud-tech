package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.mapper.ApiCallLogMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.CallLogView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * HUB-API-1005 调用日志查询，以及签名网关的调用日志落库。
 */
@Service
public class OpenApiCallLogService {

    /** OPEN-02：单次导出行数上限，避免一次拉爆内存；超出时只导出最近的前 N 条。 */
    private static final int EXPORT_LIMIT = 10000;
    private static final DateTimeFormatter EXPORT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final ApiCallLogMapper callLogMapper;

    public OpenApiCallLogService(ApiCallLogMapper callLogMapper) {
        this.callLogMapper = callLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long tenantId, long appId, String appKey, String apiPath, String method,
                       int httpStatus, Integer latencyMs, String requestId) {
        callLogMapper.insert(IdWorker.getId(), tenantId, appId, appKey, apiPath, method,
                httpStatus, null, latencyMs, requestId);
    }

    @Transactional(readOnly = true)
    public PageResult<CallLogView> search(Long appId, String apiPath, Integer status,
                                          Instant from, Instant to, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (safePage - 1) * safeSize;
        OffsetDateTime fromTime = from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTime = to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC);
        List<CallLogView> records = callLogMapper
                .search(tenantId, appId, apiPath, status, fromTime, toTime, offset, safeSize)
                .stream()
                .map(this::toView)
                .toList();
        long total = callLogMapper.count(tenantId, appId, apiPath, status, fromTime, toTime);
        return PageResult.of(records, total, safePage, safeSize);
    }

    /**
     * OPEN-02：按与列表相同的过滤条件导出 CSV（UTF-8 BOM，便于 Excel 直接打开）。
     * 只导出当前租户、当前应用；行数上限 {@link #EXPORT_LIMIT}。
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long appId, String apiPath, Integer status, Instant from, Instant to) {
        long tenantId = TenantContext.requireTenantId();
        OffsetDateTime fromTime = from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTime = to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC);
        List<Map<String, Object>> rows = callLogMapper.searchForExport(
                tenantId, appId, apiPath, status, fromTime, toTime, EXPORT_LIMIT);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("调用时间,方法,接口路径,HTTP状态,业务码,耗时(ms),AppKey,RequestId\r\n");
        for (Map<String, Object> row : rows) {
            CallLogView view = toView(row);
            csv.append(escape(EXPORT_TIME.format(view.calledAt()))).append(',')
                    .append(escape(view.method())).append(',')
                    .append(escape(view.apiPath())).append(',')
                    .append(view.httpStatus()).append(',')
                    .append(escape(view.bizCode())).append(',')
                    .append(view.latencyMs() == null ? "" : view.latencyMs()).append(',')
                    .append(escape(view.appKey())).append(',')
                    .append(escape(view.requestId())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private CallLogView toView(Map<String, Object> row) {        Object calledAt = row.get("called_at");
        Instant instant = calledAt instanceof OffsetDateTime offsetDateTime
                ? offsetDateTime.toInstant()
                : ((java.sql.Timestamp) calledAt).toInstant();
        return new CallLogView(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("app_id")).longValue(),
                (String) row.get("app_key"),
                (String) row.get("api_path"),
                (String) row.get("method"),
                ((Number) row.get("http_status")).intValue(),
                (String) row.get("biz_code"),
                row.get("latency_ms") == null ? null : ((Number) row.get("latency_ms")).intValue(),
                (String) row.get("request_id"),
                instant);
    }
}
