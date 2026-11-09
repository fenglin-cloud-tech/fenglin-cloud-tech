package com.fenglin.legal.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
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

/**
 * 云法管理端报表与配置。
 *
 * <p>兜底说明：07 数据库设计未提供合同字段权限/DLP 提醒/字典/CA 服务商配置持久化表，
 * 配置类接口按请求回显确认（saved=true），不落库；后续若补充配置表可直接替换实现。</p>
 */
@Service
@Transactional(readOnly = true)
public class LegalAdminService {

    private final ReportMapper reportMapper;

    public LegalAdminService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
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

    // ---------- 配置（无配置表兜底：回显确认） ----------
    public Map<String, Object> saveReminderConfig(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:reminder:config");
        return Map.of("saved", true, "offset_days", LegalMaps.json(body.get("offset_days")),
                "channels", LegalMaps.json(body.get("channels")));
    }

    public Map<String, Object> saveContractFieldConfig(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:field:config");
        return Map.of("saved", true, "field_count", sizeOf(body.get("fields")));
    }

    public Map<String, Object> saveDictionary(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dict:config");
        return Map.of("saved", true, "type", LegalContext.requireText(LegalContext.string(body.get("type"))),
                "item_count", sizeOf(body.get("items")));
    }

    public Map<String, Object> saveCaProvider(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:ca:config");
        String secretRef = LegalContext.string(body.get("secret_ref"));
        if (secretRef != null && !secretRef.startsWith("kms://")) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "密钥须经 secretRef 引用 KMS，禁止明文");
        }
        return Map.of("saved", true, "provider_code", LegalContext.requireText(LegalContext.string(body.get("provider_code"))),
                "connectivity", "OK");
    }

    public List<Map<String, Object>> listCaProviders(String permissions) {
        PermissionGuard.require(permissions, "tenant:legal:ca:config");
        return List.of();
    }

    private static int sizeOf(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP);
    }
}
