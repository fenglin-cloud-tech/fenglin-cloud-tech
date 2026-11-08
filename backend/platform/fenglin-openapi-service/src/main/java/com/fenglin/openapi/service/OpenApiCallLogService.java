package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.mapper.ApiCallLogMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.CallLogView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * HUB-API-1005 调用日志查询，以及签名网关的调用日志落库。
 */
@Service
public class OpenApiCallLogService {

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

    private CallLogView toView(Map<String, Object> row) {
        Object calledAt = row.get("called_at");
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
