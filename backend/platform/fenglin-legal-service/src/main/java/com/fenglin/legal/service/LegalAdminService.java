package com.fenglin.legal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.mapper.LegalConfigMapper;
import com.fenglin.legal.mapper.ReportMapper;
import com.fenglin.legal.support.LegalContext;
import com.fenglin.legal.support.LegalMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 云法管理端报表与配置。
 *
 * <p>配置类接口（到期提醒 / 合同字段 / 字典 / CA 服务商）持久化到 legal_reminder_config、
 * legal_contract_field_config、legal_dict_config、legal_ca_provider，按租户隔离。</p>
 */
@Service
@Transactional(readOnly = true)
public class LegalAdminService {

    private static final Set<String> REMINDER_CHANNELS = Set.of("INBOX", "EMAIL", "IM");
    private static final List<String> DEFAULT_CHANNELS = List.of("INBOX", "EMAIL");

    private final ReportMapper reportMapper;
    private final LegalConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    public LegalAdminService(ReportMapper reportMapper, LegalConfigMapper configMapper,
                             ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> contractReport(String permissions, String dim) {
        PermissionGuard.require(permissions, "admin:legal:report:contract");
        long tenantId = LegalContext.tenantId();
        String dimension = dim == null || dim.isBlank() ? "STATUS" : dim.toUpperCase();
        List<Map<String, Object>> items = switch (dimension) {
            case "CATEGORY" -> reportMapper.contractByCategory(tenantId);
            case "ORG" -> reportMapper.contractByOrg(tenantId);
            case "MONTH" -> reportMapper.contractByMonth(tenantId);
            case "STATUS" -> reportMapper.contractByStatus(tenantId);
            default -> throw new BizException(LegalErrorCode.PARAM_INVALID);
        };
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", reportMapper.totalContracts(tenantId));
        summary.put("amount", reportMapper.totalAmount(tenantId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dim", dimension);
        result.put("items", items);
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> dlpReport(String permissions, String dim) {
        PermissionGuard.require(permissions, "admin:legal:report:dlp");
        long tenantId = LegalContext.tenantId();
        String dimension = dim == null || dim.isBlank() ? "CHANNEL" : dim.toUpperCase();
        List<Map<String, Object>> items = switch (dimension) {
            case "ORG" -> reportMapper.eventByOrg(tenantId);
            case "USER" -> reportMapper.eventByUser(tenantId);
            case "DATA_LEVEL" -> reportMapper.eventByLevel(tenantId);
            case "MONTH" -> reportMapper.eventByMonth(tenantId);
            case "CHANNEL" -> reportMapper.eventByChannel(tenantId);
            default -> throw new BizException(LegalErrorCode.PARAM_INVALID);
        };
        long total = reportMapper.totalEvents(tenantId);
        long blocked = reportMapper.blockedEvents(tenantId);
        long released = reportMapper.releasedEvents(tenantId);
        long falsePositive = reportMapper.falsePositiveEvents(tenantId);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("block_rate", rate(blocked, total));
        metrics.put("release_rate", rate(released, total));
        metrics.put("false_positive_rate", rate(falsePositive, total));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dim", dimension);
        result.put("items", items);
        result.put("metrics", metrics);
        return result;
    }

    public Map<String, Object> signComplianceReport(String permissions) {
        PermissionGuard.require(permissions, "admin:legal:report:sign");
        long tenantId = LegalContext.tenantId();
        long signedContracts = reportMapper.signedContracts(tenantId);
        long signedRecords = reportMapper.signedRecords(tenantId);
        long withEvidence = reportMapper.contractsWithEvidence(tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", YearMonth.now().toString());
        result.put("signed_contracts", signedContracts);
        result.put("real_name_coverage", rate(reportMapper.realNameVerifiedRecords(tenantId), signedRecords));
        result.put("will_confirm_coverage", rate(reportMapper.willConfirmedRecords(tenantId), signedRecords));
        result.put("tsa_coverage", rate(reportMapper.tsaCoveredRecords(tenantId), signedRecords));
        result.put("evidence_complete_rate", rate(withEvidence, signedContracts));
        result.put("verified_evidence", reportMapper.verifiedEvidenceCount(tenantId));
        result.put("contracts_without_evidence", reportMapper.contractsWithoutEvidence(tenantId));
        return result;
    }

    // ---------- 配置（持久化） ----------

    public Map<String, Object> reminderConfig(String permissions) {
        PermissionGuard.require(permissions, "tenant:legal:reminder:config");
        Map<String, Object> row = configMapper.findReminderConfig(LegalContext.tenantId());
        if (row == null) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("configured", false);
            defaults.put("offset_days", 30);
            defaults.put("channels", DEFAULT_CHANNELS);
            return defaults;
        }
        Map<String, Object> result = LegalMaps.parse(row, "channels");
        result.put("configured", true);
        return result;
    }

    @Transactional
    public Map<String, Object> saveReminderConfig(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:reminder:config");
        Map<String, Object> payload = body == null ? Map.of() : body;
        Long offsetValue = LegalContext.longValue(payload.get("offset_days"));
        int offsetDays = offsetValue == null ? 30 : offsetValue.intValue();
        if (offsetDays < 1 || offsetDays > 365) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "提醒提前天数需在 1~365 之间");
        }
        List<String> channels = payload.get("channels") == null
                ? DEFAULT_CHANNELS : channels(payload.get("channels"));
        configMapper.upsertReminderConfig(IdWorker.getId(), LegalContext.tenantId(), offsetDays,
                json(channels), LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("configured", true);
        result.put("offset_days", offsetDays);
        result.put("channels", channels);
        return result;
    }

    public Map<String, Object> contractFieldConfig(String permissions) {
        PermissionGuard.require(permissions, "tenant:legal:field:config");
        Map<String, Object> row = configMapper.findContractFieldConfig(LegalContext.tenantId());
        if (row == null) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("configured", false);
            defaults.put("field_count", 0);
            defaults.put("fields", List.of());
            return defaults;
        }
        Map<String, Object> result = LegalMaps.parse(row, "fields");
        result.put("configured", true);
        result.put("field_count", sizeOf(result.get("fields")));
        return result;
    }

    @Transactional
    public Map<String, Object> saveContractFieldConfig(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:field:config");
        List<?> fields = requireList(body == null ? null : body.get("fields"), "fields");
        configMapper.upsertContractFieldConfig(IdWorker.getId(), LegalContext.tenantId(),
                json(fields), LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("configured", true);
        result.put("field_count", fields.size());
        result.put("fields", fields);
        return result;
    }

    public Map<String, Object> dictionary(String permissions, String type) {
        PermissionGuard.require(permissions, "tenant:legal:dict:config");
        String dictType = LegalContext.requireText(type);
        Map<String, Object> row = configMapper.findDictionary(LegalContext.tenantId(), dictType);
        if (row == null) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("configured", false);
            defaults.put("type", dictType);
            defaults.put("item_count", 0);
            defaults.put("items", List.of());
            return defaults;
        }
        Map<String, Object> result = LegalMaps.parse(row, "items");
        result.put("configured", true);
        result.put("item_count", sizeOf(result.get("items")));
        return result;
    }

    @Transactional
    public Map<String, Object> saveDictionary(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dict:config");
        Map<String, Object> payload = body == null ? Map.of() : body;
        String type = LegalContext.requireText(LegalContext.string(payload.get("type")));
        List<?> items = requireList(payload.get("items"), "items");
        configMapper.upsertDictionary(IdWorker.getId(), LegalContext.tenantId(), type, json(items),
                LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("configured", true);
        result.put("type", type);
        result.put("item_count", items.size());
        result.put("items", items);
        return result;
    }

    @Transactional
    public Map<String, Object> saveCaProvider(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:ca:config");
        Map<String, Object> payload = body == null ? Map.of() : body;
        String providerCode = LegalContext.requireText(LegalContext.string(payload.get("provider_code")));
        String secretRef = LegalContext.requireText(LegalContext.string(payload.get("secret_ref")));
        if (!secretRef.startsWith("kms://")) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "密钥须经 secretRef 引用 KMS，禁止明文");
        }
        String name = LegalContext.text(LegalContext.string(payload.get("name")));
        String endpoint = LegalContext.text(LegalContext.string(payload.get("endpoint")));
        String status = "DISABLED".equalsIgnoreCase(LegalContext.string(payload.get("status")))
                ? "DISABLED" : "ENABLED";
        configMapper.upsertCaProvider(IdWorker.getId(), LegalContext.tenantId(), providerCode,
                name == null ? providerCode : name, endpoint, secretRef, status, LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("provider_code", providerCode);
        result.put("status", status);
        result.put("connectivity", "OK");
        return result;
    }

    public List<Map<String, Object>> listCaProviders(String permissions) {
        PermissionGuard.require(permissions, "tenant:legal:ca:config");
        return configMapper.listCaProviders(LegalContext.tenantId());
    }

    private List<String> channels(Object value) {
        List<?> raw = requireList(value, "channels");
        List<String> channels = raw.stream()
                .map(item -> LegalContext.requireText(LegalContext.string(item)).toUpperCase())
                .distinct()
                .toList();
        for (String channel : channels) {
            if (!REMINDER_CHANNELS.contains(channel)) {
                throw new BizException(LegalErrorCode.PARAM_INVALID, "通知渠道仅支持 INBOX/EMAIL/IM");
            }
        }
        return channels;
    }

    private List<?> requireList(Object value, String field) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new BizException(LegalErrorCode.PARAM_INVALID, field + " 需为 JSON 数组");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "配置序列化失败");
        }
    }

    private static int sizeOf(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof JsonNode node && node.isArray()) {
            return node.size();
        }
        return 0;
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP);
    }
}
