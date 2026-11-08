package com.fenglin.lowcode.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.lowcode.mapper.LowcodeAppMapper;
import com.fenglin.lowcode.mapper.LowcodeRecordMapper;
import com.fenglin.lowcode.mapper.LowcodeSchemaMapper;
import com.fenglin.lowcode.web.dto.LowcodeDtos.RecordView;
import com.fenglin.lowcode.web.dto.LowcodeDtos.SubmitResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUB-API-1204 表单数据提交；数据查询为验收 3 的兜底扩展接口。
 */
@Service
public class LowcodeDataService {

    private final LowcodeAppMapper appMapper;
    private final LowcodeRecordMapper recordMapper;
    private final LowcodeSchemaMapper schemaMapper;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public LowcodeDataService(LowcodeAppMapper appMapper,
                              LowcodeRecordMapper recordMapper,
                              LowcodeSchemaMapper schemaMapper,
                              ObjectMapper objectMapper,
                              AuditSink auditSink) {
        this.appMapper = appMapper;
        this.recordMapper = recordMapper;
        this.schemaMapper = schemaMapper;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public SubmitResult submit(String code, Map<String, Object> data, String idempotencyKey,
                               Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String appCode = require(code, "code");
        Map<String, Object> app = appMapper.findByCode(tenantId, appCode);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "低代码应用不存在: " + appCode);
        }
        if (!"PUBLISHED".equals(app.get("status"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "应用未发布，不可提交数据");
        }
        long appId = ((Number) app.get("id")).longValue();
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : idempotencyKey.trim();
        if (key != null) {
            Map<String, Object> existing = recordMapper.findByIdempotencyKey(tenantId, key);
            if (existing != null) {
                return new SubmitResult(((Number) existing.get("id")).longValue(),
                        (String) existing.get("record_no"), (String) existing.get("status"));
            }
        }
        if (data == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "data 不能为空");
        }
        validate(tenantId, appId, data);
        long id = IdWorker.getId();
        String recordNo = "LC" + id;
        try {
            recordMapper.insert(id, tenantId, appId, recordNo, key, toJson(data), operatorId);
        } catch (DuplicateKeyException ex) {
            Map<String, Object> existing = key == null ? null
                    : recordMapper.findByIdempotencyKey(tenantId, key);
            if (existing == null) {
                throw ex;
            }
            return new SubmitResult(((Number) existing.get("id")).longValue(),
                    (String) existing.get("record_no"), (String) existing.get("status"));
        }
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "LOWCODE_RECORD_SUBMITTED",
                "lowcode", "CREATE", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "LOWCODE_RECORD", recordNo, Instant.now()));
        return new SubmitResult(id, recordNo, "SUBMITTED");
    }

    @Transactional(readOnly = true)
    public PageResult<RecordView> records(String code, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = appMapper.findByCode(tenantId, require(code, "code"));
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "低代码应用不存在: " + code);
        }
        long appId = ((Number) app.get("id")).longValue();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        List<RecordView> records = new ArrayList<>();
        for (Map<String, Object> row : recordMapper.list(tenantId, appId,
                (safePage - 1) * safeSize, safeSize)) {
            records.add(new RecordView(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("record_no"),
                    parseJson((String) row.get("data_text")),
                    (String) row.get("status"),
                    row.get("submitted_by") == null ? null : ((Number) row.get("submitted_by")).longValue(),
                    toInstant(row.get("created_at"))));
        }
        return PageResult.of(records, recordMapper.count(tenantId, appId), safePage, safeSize);
    }

    private void validate(long tenantId, long appId, Map<String, Object> data) {
        List<Map<String, Object>> fields = schemaMapper.listModels(tenantId, appId);
        if (fields.isEmpty()) {
            return;
        }
        Set<String> allowed = new java.util.HashSet<>();
        for (Map<String, Object> field : fields) {
            String name = (String) field.get("field_name");
            allowed.add(name);
            Object value = data.get(name);
            int required = ((Number) field.get("required")).intValue();
            if (value == null || (value instanceof String text && text.isBlank())) {
                if (required == 1) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "字段必填: " + name);
                }
                continue;
            }
            assertType(name, (String) field.get("field_type"), value);
            Object maxLength = field.get("max_length");
            if (maxLength != null && value instanceof String text
                    && text.length() > ((Number) maxLength).intValue()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST,
                        "字段超长: " + name + " 最大 " + maxLength);
            }
        }
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "字段不在数据模型中: " + key);
            }
        }
    }

    private void assertType(String name, String type, Object value) {
        String expected = type == null ? "string" : type;
        boolean ok = switch (expected) {
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List<?>;
            case "date", "datetime" -> value instanceof String;
            default -> value instanceof String;
        };
        if (!ok) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "字段类型不匹配: " + name + " 期望 " + expected);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "data 不是合法 JSON");
        }
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
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
        return null;
    }
}
