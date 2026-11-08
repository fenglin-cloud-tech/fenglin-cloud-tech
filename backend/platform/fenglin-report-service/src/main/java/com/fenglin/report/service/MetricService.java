package com.fenglin.report.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.ReportErrorCode;
import com.fenglin.report.mapper.MetricMapper;
import com.fenglin.report.web.dto.ReportDtos.MetricCreateRequest;
import com.fenglin.report.web.dto.ReportDtos.MetricView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HUB-API-1101 指标创建、1102 指标列表/血缘。
 */
@Service
public class MetricService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Set<String> METRIC_TYPES = Set.of("ATOMIC", "DERIVED", "COMPOSITE");
    private static final Set<String> GRAINS = Set.of("HOUR", "DAY", "WEEK", "MONTH", "QUARTER", "YEAR");

    private final MetricMapper metricMapper;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public MetricService(MetricMapper metricMapper, ObjectMapper objectMapper, AuditSink auditSink) {
        this.metricMapper = metricMapper;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public MetricView create(MetricCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String code = require(request.code(), "code", 64);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "code 需以字母开头，仅含字母/数字/_.-");
        }
        String name = require(request.name(), "name", 64);
        String metricType = request.metricType() == null ? "ATOMIC" : request.metricType().trim().toUpperCase();
        if (!METRIC_TYPES.contains(metricType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "metric_type 仅支持 ATOMIC/DERIVED/COMPOSITE");
        }
        String grain = request.grain() == null ? "DAY" : request.grain().trim().toUpperCase();
        if (!GRAINS.contains(grain)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "grain 取值非法");
        }
        if (metricMapper.findByCode(tenantId, code) != null) {
            throw new BizException(ReportErrorCode.METRIC_CONFLICT, "指标口径冲突: " + code);
        }
        String dimensions = request.dimensions() == null || request.dimensions().isEmpty()
                ? null
                : String.join(",", request.dimensions().stream().map(String::trim)
                .filter(value -> !value.isEmpty()).toList());
        String lineage = toJson(request.lineage());
        long id = IdWorker.getId();
        metricMapper.insert(id, tenantId, code, name, metricType, request.formula(), request.unit(),
                grain, dimensions, lineage, operatorId, "PUBLISHED", operatorId);

        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "REPORT_METRIC_CREATED", "report",
                "CREATE", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "METRIC", String.valueOf(id), Instant.now()));
        return new MetricView(id, code, name, metricType, request.formula(), request.unit(), grain,
                dimensions == null ? List.of() : Arrays.asList(dimensions.split(",")),
                request.lineage(), "PUBLISHED");
    }

    @Transactional(readOnly = true)
    public PageResult<MetricView> list(String category, String status, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        List<MetricView> records = metricMapper.list(tenantId, category, status,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toView)
                .toList();
        return PageResult.of(records, metricMapper.count(tenantId, category, status), safePage, safeSize);
    }

    private MetricView toView(Map<String, Object> row) {
        String dimensions = (String) row.get("dimensions");
        return new MetricView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("metric_type"),
                (String) row.get("formula"),
                (String) row.get("unit"),
                (String) row.get("grain"),
                dimensions == null || dimensions.isBlank() ? List.of() : Arrays.asList(dimensions.split(",")),
                parseJson((String) row.get("lineage_text")),
                (String) row.get("status"));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "lineage 不是合法 JSON 对象");
        }
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            return value;
        }
    }

    private String require(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }
}
