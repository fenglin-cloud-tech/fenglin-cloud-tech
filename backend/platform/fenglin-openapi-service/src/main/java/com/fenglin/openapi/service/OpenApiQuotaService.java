package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.OpenApiErrorCode;
import com.fenglin.openapi.mapper.ApiQuotaMapper;
import com.fenglin.openapi.mapper.OpenAppMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalQuotaView;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaItem;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HUB-API-1004 配额配置，以及签名网关的 QPS/日限额校验。
 */
@Service
public class OpenApiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiQuotaService.class);

    private final ApiQuotaMapper quotaMapper;
    private final OpenAppMapper appMapper;
    private final StringRedisTemplate redisTemplate;
    private final AuditSink auditSink;

    public OpenApiQuotaService(ApiQuotaMapper quotaMapper,
                               OpenAppMapper appMapper,
                               StringRedisTemplate redisTemplate,
                               AuditSink auditSink) {
        this.quotaMapper = quotaMapper;
        this.appMapper = appMapper;
        this.redisTemplate = redisTemplate;
        this.auditSink = auditSink;
    }

    @Transactional
    public List<QuotaView> upsert(QuotaRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request.appId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "app_id 不能为空");
        }
        if (appMapper.findById(tenantId, request.appId()) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + request.appId());
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "items 不能为空");
        }
        List<QuotaView> views = new ArrayList<>();
        for (QuotaItem item : request.items()) {
            String apiPath = normalizePath(item.apiPath());
            if (item.qpsLimit() != null && item.qpsLimit() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "qps_limit 必须为正整数");
            }
            if (item.dailyLimit() != null && item.dailyLimit() < 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "daily_limit 不能为负数");
            }
            quotaMapper.upsert(IdWorker.getId(), tenantId, request.appId(), apiPath,
                    item.qpsLimit(), item.dailyLimit(), operatorId);
            Map<String, Object> row = quotaMapper.findBestMatch(tenantId, request.appId(), apiPath);
            views.add(new QuotaView(request.appId(), apiPath,
                    toInteger(row.get("qps_limit")),
                    toLong(row.get("daily_limit")),
                    toLong(row.get("used_today"))));
        }
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "OPENAPI_QUOTA_UPDATED", "openapi",
                "UPDATE", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(),
                "API_QUOTA", String.valueOf(request.appId()), Instant.now()));
        return views;
    }

    /** 开发者门户：按应用维度返回配额、当日用量与当前 QPS 限流状态。 */
    @Transactional(readOnly = true)
    public List<PortalQuotaView> listOwned(long appId) {
        long tenantId = TenantContext.requireTenantId();
        long epochSecond = Instant.now().getEpochSecond();
        return quotaMapper.listByApp(tenantId, appId).stream()
                .map(row -> {
                    String apiPath = (String) row.get("api_path");
                    Integer qpsLimit = toInteger(row.get("qps_limit"));
                    Long dailyLimit = toLong(row.get("daily_limit"));
                    long usedToday = row.get("used_today") == null ? 0L : ((Number) row.get("used_today")).longValue();
                    Object usedDate = row.get("used_date");
                    Integer currentQps = currentQps(appId, apiPath, epochSecond);
                    boolean qpsFull = qpsLimit != null && qpsLimit > 0
                            && currentQps != null && currentQps >= qpsLimit;
                    boolean dailyExceeded = dailyLimit != null && dailyLimit > 0 && usedToday >= dailyLimit;
                    boolean dailyWarning = dailyLimit != null && dailyLimit > 0 && usedToday * 5 >= dailyLimit * 4;
                    String limitStatus = qpsFull || dailyExceeded ? "EXCEEDED" : dailyWarning ? "WARNING" : "NORMAL";
                    return new PortalQuotaView(appId, apiPath, qpsLimit, dailyLimit, usedToday,
                            usedDate == null ? null : usedDate.toString(), currentQps, limitStatus);
                })
                .toList();
    }

    private Integer currentQps(long appId, String apiPath, long epochSecond) {
        if (apiPath == null || "*".equals(apiPath)) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue()
                    .get("openapi:qps:" + appId + ":" + apiPath + ":" + epochSecond);
            return value == null ? 0 : Integer.valueOf(value);
        } catch (RuntimeException ex) {
            log.warn("QPS 状态读取失败：appId={} path={}", appId, apiPath, ex);
            return null;
        }
    }

    /**
     * 签名网关调用前的配额校验：QPS 使用 Redis 秒级计数，日限额使用数据库原子累加。
     */
    @Transactional
    public void checkAndConsume(long tenantId, long appId, String apiPath) {
        Map<String, Object> quota = quotaMapper.findBestMatch(tenantId, appId, apiPath);
        if (quota == null) {
            return;
        }
        Integer qpsLimit = toInteger(quota.get("qps_limit"));
        if (qpsLimit != null && qpsLimit > 0) {
            checkQps(appId, apiPath, qpsLimit);
        }
        Long dailyLimit = toLong(quota.get("daily_limit"));
        if (dailyLimit != null && quotaMapper.incrementDaily(((Number) quota.get("id")).longValue()) == 0) {
            throw new BizException(OpenApiErrorCode.QUOTA_EXCEEDED, "应用配额超限: " + apiPath);
        }
    }

    private void checkQps(long appId, String apiPath, int qpsLimit) {
        String key = "openapi:qps:" + appId + ":" + apiPath + ":" + Instant.now().getEpochSecond();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(2));
            }
            if (count != null && count > qpsLimit) {
                throw new BizException(OpenApiErrorCode.QUOTA_EXCEEDED, "应用 QPS 超限: " + apiPath);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 配额中心（Redis）异常时降级放行，避免开放平台整体不可用，仅记录告警日志
            log.warn("QPS 计数不可用，降级放行：appId={} path={}", appId, apiPath, ex);
        }
    }

    private String normalizePath(String apiPath) {
        if (apiPath == null || apiPath.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "api_path 不能为空");
        }
        String normalized = apiPath.trim();
        if (!normalized.startsWith("/") || normalized.length() > 256) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "api_path 必须以 / 开头且不超过 256 字符");
        }
        return normalized;
    }

    private Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
