package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.audit.mapper.AuditAlertRuleMapper;
import com.fenglin.audit.web.dto.AlertRuleItem;
import com.fenglin.audit.web.dto.AlertRuleUpdateRequest;
import com.fenglin.audit.web.dto.AlertRuleView;
import com.fenglin.audit.web.dto.RecordAuditRequest;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计告警规则（HUB-API-804）：租户级全量替换，规则变更落审计。
 */
@Service
public class AuditAlertRuleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_CHANNELS = Set.of("STATION", "EMAIL", "SMS", "WEBHOOK");

    private final AuditAlertRuleMapper alertRuleMapper;
    private final AuditService auditService;

    public AuditAlertRuleService(AuditAlertRuleMapper alertRuleMapper, AuditService auditService) {
        this.alertRuleMapper = alertRuleMapper;
        this.auditService = auditService;
    }

    @Transactional
    public List<AlertRuleView> replace(AlertRuleUpdateRequest request, Long operatorId) {
        Long tenantId = TenantContext.requireTenantId();
        List<AlertRuleItem> items = validate(request.items());
        alertRuleMapper.deleteAll(tenantId);
        for (AlertRuleItem item : items) {
            alertRuleMapper.insert(IdWorker.getId(), tenantId, item.code().trim(), item.eventType().trim(),
                    item.threshold() == null ? 1 : item.threshold(), item.receiverRole().trim(),
                    String.join(",", normalizeChannels(item.channels())), operatorId);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("count", items.size());
        detail.put("codes", items.stream().map(item -> item.code().trim()).toList());
        auditService.record(new RecordAuditRequest(null, "AUDIT_ALERT_RULE_UPDATED", "audit", "UPDATE",
                "MEDIUM", operatorId, null, "AUDIT_ALERT_RULE", String.valueOf(tenantId), null, null,
                "ADMIN", null, "SUCCESS", MAPPER.valueToTree(detail), Instant.now()));
        return list();
    }

    @Transactional(readOnly = true)
    public List<AlertRuleView> list() {
        return alertRuleMapper.findAll().stream().map(this::toView).toList();
    }

    private List<AlertRuleItem> validate(List<AlertRuleItem> items) {
        Set<String> codes = new HashSet<>();
        for (AlertRuleItem item : items) {
            String code = item.code() == null ? "" : item.code().trim();
            if (!codes.add(code)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "告警规则 code 重复: " + code);
            }
            normalizeChannels(item.channels());
        }
        return items;
    }

    private List<String> normalizeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "告警渠道不能为空");
        }
        List<String> normalized = channels.stream().map(channel -> channel == null ? "" : channel.trim().toUpperCase())
                .filter(channel -> !channel.isEmpty()).distinct().toList();
        if (normalized.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "告警渠道不能为空");
        }
        for (String channel : normalized) {
            if (!SUPPORTED_CHANNELS.contains(channel)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的告警渠道: " + channel);
            }
        }
        return normalized;
    }

    private AlertRuleView toView(Map<String, Object> row) {
        String channels = (String) row.get("channels");
        return new AlertRuleView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"),
                (String) row.get("event_type"),
                ((Number) row.get("threshold")).intValue(),
                (String) row.get("receiver_role"),
                channels == null || channels.isBlank() ? List.of() : Arrays.asList(channels.split(",")),
                true);
    }
}
