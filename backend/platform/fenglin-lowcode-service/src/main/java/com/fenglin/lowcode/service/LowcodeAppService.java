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
import com.fenglin.lowcode.LowcodeErrorCode;
import com.fenglin.lowcode.mapper.LowcodeAppMapper;
import com.fenglin.lowcode.mapper.LowcodePublishMapper;
import com.fenglin.lowcode.mapper.LowcodeSchemaMapper;
import com.fenglin.lowcode.mapper.LowcodeVersionMapper;
import com.fenglin.lowcode.web.dto.LowcodeDtos.AppCreateRequest;
import com.fenglin.lowcode.web.dto.LowcodeDtos.AppView;
import com.fenglin.lowcode.web.dto.LowcodeDtos.DataModel;
import com.fenglin.lowcode.web.dto.LowcodeDtos.ModelField;
import com.fenglin.lowcode.web.dto.LowcodeDtos.PublishResult;
import com.fenglin.lowcode.web.dto.LowcodeDtos.VersionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HUB-API-1201 应用创建、1202 发布、1203 列表，并兜底验收 3 的版本回滚。
 */
@Service
public class LowcodeAppService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{2,39}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> APP_TYPES = Set.of("FORM", "PAGE", "COMBO");
    private static final Set<String> ENVS = Set.of("DEV", "TEST", "PROD");
    private static final Set<String> FIELD_TYPES = Set.of(
            "string", "number", "boolean", "date", "datetime", "array");

    private final LowcodeAppMapper appMapper;
    private final LowcodeVersionMapper versionMapper;
    private final LowcodePublishMapper publishMapper;
    private final LowcodeSchemaMapper schemaMapper;
    private final WorkflowServiceClient workflowClient;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public LowcodeAppService(LowcodeAppMapper appMapper,
                             LowcodeVersionMapper versionMapper,
                             LowcodePublishMapper publishMapper,
                             LowcodeSchemaMapper schemaMapper,
                             WorkflowServiceClient workflowClient,
                             ObjectMapper objectMapper,
                             AuditSink auditSink) {
        this.appMapper = appMapper;
        this.versionMapper = versionMapper;
        this.publishMapper = publishMapper;
        this.schemaMapper = schemaMapper;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public AppView create(AppCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String code = require(request.code(), "code", 40);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "code 需小写字母开头，仅含小写字母/数字/下划线");
        }
        String name = require(request.name(), "name", 128);
        String appType = normalize(request.appType(), "FORM");
        if (!APP_TYPES.contains(appType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "app_type 仅支持 FORM/PAGE/COMBO");
        }
        String env = normalize(request.env(), "DEV");
        if (!ENVS.contains(env)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "env 仅支持 DEV/TEST/PROD");
        }
        if (request.schema() == null || !request.schema().isObject()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "schema 必须为 JSON 对象");
        }
        DataModel dataModel = request.dataModel() == null
                ? new DataModel(List.of(), null) : request.dataModel();
        List<ModelField> fields = validateFields(dataModel.fields());
        if (appMapper.findByCode(tenantId, code) != null) {
            throw new BizException(LowcodeErrorCode.MODEL_CONFLICT, "低代码模型冲突: " + code);
        }
        long id = IdWorker.getId();
        String schemaJson = toJson(request.schema());
        String dataModelJson = toJson(dataModel);
        appMapper.insert(id, tenantId, code, name, appType, schemaJson, dataModelJson, env, "DRAFT",
                operatorId);
        indexFields(tenantId, id, fields, operatorId);
        indexFormsAndPages(tenantId, id, request.schema(), operatorId);
        audit(tenantId, operatorId, "LOWCODE_APP_CREATED", String.valueOf(id));
        return new AppView(id, code, name, appType, request.schema(), dataModel, env, "DRAFT", null, 0);
    }

    @Transactional
    public PublishResult publish(long id, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = requireApp(tenantId, id);
        if ("PUBLISHED".equals(app.get("status"))) {
            Map<String, Object> latest = publishMapper.findLatest(tenantId, id);
            String processInstanceId = latest == null ? null : (String) latest.get("process_instance_id");
            return new PublishResult(id, (String) app.get("env"), "PUBLISHED", processInstanceId,
                    versionMapper.maxVersionNo(tenantId, id));
        }
        String schemaText = (String) app.get("schema_text");
        String dataModelText = (String) app.get("data_model_text");
        int versionNo = versionMapper.maxVersionNo(tenantId, id) + 1;
        String processInstanceId = null;
        String processKey = publishProcessKey(dataModelText);
        if (processKey != null) {
            processInstanceId = workflowClient.startPublishProcess(tenantId, operatorId, processKey,
                    (String) app.get("code"), app.get("name") + " 发布 v" + versionNo,
                    Map.of("app_id", id, "app_code", app.get("code"), "version_no", versionNo));
        }
        long versionId = IdWorker.getId();
        versionMapper.insert(versionId, tenantId, id, versionNo, schemaText, dataModelText,
                processInstanceId, operatorId);
        appMapper.markPublished(tenantId, id, "PROD", operatorId);
        publishMapper.insert(IdWorker.getId(), tenantId, id, versionId, "PROD", "PUBLISH",
                processInstanceId, null, operatorId);
        audit(tenantId, operatorId, "LOWCODE_APP_PUBLISHED", String.valueOf(id));
        return new PublishResult(id, "PROD", "PUBLISHED", processInstanceId, versionNo);
    }

    /** 扩展接口：版本回滚（验收 3 要求，总纲无接口编号）。 */
    @Transactional
    public AppView rollback(long id, Integer versionNo, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        requireApp(tenantId, id);
        if (versionNo == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "version_no 不能为空");
        }
        Map<String, Object> version = versionMapper.findByVersion(tenantId, id, versionNo);
        if (version == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "版本不存在: " + versionNo);
        }
        String schemaText = (String) version.get("schema_text");
        String dataModelText = (String) version.get("data_model_text");
        appMapper.restoreVersion(tenantId, id, schemaText, dataModelText, "PROD", operatorId);
        DataModel dataModel = parseDataModel(dataModelText);
        schemaMapper.deleteModels(tenantId, id);
        schemaMapper.deleteForms(tenantId, id);
        schemaMapper.deletePages(tenantId, id);
        indexFields(tenantId, id, dataModel == null ? List.of() : dataModel.fields(), operatorId);
        if (schemaText != null) {
            indexFormsAndPages(tenantId, id, parseJson(schemaText), operatorId);
        }
        publishMapper.insert(IdWorker.getId(), tenantId, id, longValue(version.get("id")), "PROD",
                "ROLLBACK", (String) version.get("process_instance_id"),
                "rollback to v" + versionNo, operatorId);
        audit(tenantId, operatorId, "LOWCODE_APP_ROLLED_BACK", String.valueOf(id));
        return toView(requireApp(tenantId, id));
    }

    @Transactional(readOnly = true)
    public PageResult<AppView> list(String env, String status, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String envFilter = env == null || env.isBlank() ? null : env.trim().toUpperCase();
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<AppView> records = appMapper.list(tenantId, envFilter, statusFilter,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toView)
                .toList();
        return PageResult.of(records, appMapper.count(tenantId, envFilter, statusFilter),
                safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(long id) {
        long tenantId = TenantContext.requireTenantId();
        requireApp(tenantId, id);
        List<VersionView> views = new ArrayList<>();
        for (Map<String, Object> row : versionMapper.list(tenantId, id)) {
            views.add(new VersionView(
                    longValue(row.get("id")),
                    intValue(row.get("version_no")),
                    (String) row.get("status"),
                    (String) row.get("process_instance_id"),
                    toInstant(row.get("published_at")),
                    parseJson((String) row.get("schema_text"))));
        }
        return views;
    }

    // ---------- helpers ----------

    private List<ModelField> validateFields(List<ModelField> fields) {
        if (fields == null) {
            return List.of();
        }
        Set<String> names = new HashSet<>();
        List<ModelField> normalized = new ArrayList<>();
        for (ModelField field : fields) {
            if (field == null || field.name() == null || !FIELD_PATTERN.matcher(field.name()).matches()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST,
                        "字段名需小写字母开头，仅含小写字母/数字/下划线");
            }
            if (!names.add(field.name())) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "字段重复: " + field.name());
            }
            String type = field.type() == null ? "string" : field.type().trim().toLowerCase();
            if (!FIELD_TYPES.contains(type)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "字段类型非法: " + field.type());
            }
            if (field.maxLength() != null && field.maxLength() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "max_length 必须大于 0");
            }
            normalized.add(new ModelField(field.name(), type, field.required(),
                    field.maxLength(), field.defaultValue()));
        }
        return normalized;
    }

    private void indexFields(long tenantId, long appId, List<ModelField> fields, Long operatorId) {
        int sort = 0;
        for (ModelField field : fields) {
            schemaMapper.insertModel(IdWorker.getId(), tenantId, appId, field.name(), field.type(),
                    Boolean.TRUE.equals(field.required()) ? 1 : 0, field.maxLength(),
                    field.defaultValue(), sort++, operatorId);
        }
    }

    private void indexFormsAndPages(long tenantId, long appId, JsonNode schema, Long operatorId) {
        if (schema == null) {
            return;
        }
        indexNodes(tenantId, appId, schema.path("forms"), "form", operatorId);
        indexNodes(tenantId, appId, schema.path("pages"), "page", operatorId);
    }

    private void indexNodes(long tenantId, long appId, JsonNode nodes, String kind, Long operatorId) {
        if (!nodes.isArray()) {
            return;
        }
        int sort = 0;
        for (JsonNode node : nodes) {
            String key = node.path("key").asText(null);
            if (key == null || key.isBlank()) {
                key = node.path(kind + "_key").asText(null);
            }
            if (key == null || key.isBlank()) {
                continue;
            }
            String name = node.path("name").asText(key);
            String json = node.toString();
            if ("form".equals(kind)) {
                schemaMapper.insertForm(IdWorker.getId(), tenantId, appId, key, name, json, sort++,
                        operatorId);
            } else {
                schemaMapper.insertPage(IdWorker.getId(), tenantId, appId, key, name, json, sort++,
                        operatorId);
            }
        }
    }

    private Map<String, Object> requireApp(long tenantId, long id) {
        Map<String, Object> app = appMapper.findById(tenantId, id);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "低代码应用不存在: " + id);
        }
        return app;
    }

    private AppView toView(Map<String, Object> row) {
        return new AppView(
                longValue(row.get("id")),
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("app_type"),
                parseJson((String) row.get("schema_text")),
                parseDataModel((String) row.get("data_model_text")),
                (String) row.get("env"),
                (String) row.get("status"),
                toInstant(row.get("published_at")),
                intValue(row.get("latest_version")));
    }

    private String publishProcessKey(String dataModelText) {
        DataModel dataModel = parseDataModel(dataModelText);
        if (dataModel == null || dataModel.publishProcessKey() == null
                || dataModel.publishProcessKey().isBlank()) {
            return null;
        }
        return dataModel.publishProcessKey().trim();
    }

    private DataModel parseDataModel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, DataModel.class);
        } catch (Exception ex) {
            return null;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "schema/data_model 不是合法 JSON");
        }
    }

    private void audit(long tenantId, Long operatorId, String eventType, String bizId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), eventType, "lowcode",
                "UPDATE", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "LOWCODE_APP", bizId, Instant.now()));
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

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
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
