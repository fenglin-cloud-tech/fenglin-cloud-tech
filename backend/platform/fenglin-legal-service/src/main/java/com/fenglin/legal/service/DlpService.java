package com.fenglin.legal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.client.WorkflowClient;
import com.fenglin.legal.mapper.DlpMapper;
import com.fenglin.legal.support.HashChain;
import com.fenglin.legal.support.LegalAudit;
import com.fenglin.legal.support.LegalContext;
import com.fenglin.legal.support.LegalIdempotency;
import com.fenglin.legal.support.LegalMaps;
import com.fenglin.legal.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * G2 内部信息防泄漏服务：敏感规则、策略、通道、事件上报/决策/处置、放行、水印、白名单、终端与审计链。
 *
 * <p>兜底说明：07 数据库设计未提供独立放行单表与 DLP 配置表，放行单以
 * {@code legal_dlp_event(event_type=RELEASE_APPLY)} 承载；管理端报表/配置类接口按请求回显实现。</p>
 */
@Service
@Transactional
public class DlpService {

    private static final List<String> LEVELS = List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");
    private static final List<String> HANDLE_ACTIONS =
            List.of("TRIAGE", "INVESTIGATE", "CONFIRM", "FALSE_POSITIVE", "ESCALATE", "CLOSE");

    private final DlpMapper mapper;
    private final LegalAudit audit;
    private final HashChain hashChain;
    private final WorkflowClient workflowClient;
    private final LegalIdempotency idempotency;
    private final com.fenglin.legal.mapper.CommonsMapper commons;

    public DlpService(DlpMapper mapper, LegalAudit audit, HashChain hashChain, WorkflowClient workflowClient,
                      LegalIdempotency idempotency, com.fenglin.legal.mapper.CommonsMapper commons) {
        this.mapper = mapper;
        this.audit = audit;
        this.hashChain = hashChain;
        this.workflowClient = workflowClient;
        this.idempotency = idempotency;
        this.commons = commons;
    }

    // ---------- 敏感规则 ----------
    public Map<String, Object> createRule(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:rule:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String ruleType = LegalContext.requireText(LegalContext.string(body.get("rule_type")));
        String expression = LegalContext.string(body.get("expression"));
        validateExpression(ruleType, expression);
        long id = IdWorker.getId();
        String ruleNo = "SR" + Year.now().getValue()
                + String.format("%06d", commons.nextDlpEventNo() % 1_000_000);
        mapper.insertRule(id, tenantId, ruleNo,
                LegalContext.requireText(LegalContext.string(body.get("name"))), ruleType, expression,
                LegalContext.requireText(LegalContext.string(body.get("data_level"))),
                LegalContext.string(body.get("category")), decimal(body.get("threshold")),
                intOrDefault(body.get("priority"), 100), LegalContext.string(body.get("remark")), userId);
        writeAudit("RULE_CONFIG", "SENSITIVE_RULE", id, Map.of("rule_no", ruleNo), "SUCCESS", userId);
        audit.low("dlp.rule.create", "SENSITIVE_RULE", id);
        return Map.of("id", id, "rule_no", ruleNo, "enabled", true);
    }

    public PageResult<Map<String, Object>> listRules(String permissions, Integer page, Integer size,
                                                     String dataLevel, Boolean enabled) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:rule:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listRules(tenantId, LegalContext.text(dataLevel), enabled,
                        LegalContext.offset(page, size), limit),
                mapper.countRules(tenantId, LegalContext.text(dataLevel), enabled), LegalContext.page(page), limit);
    }

    public Map<String, Object> testRule(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:rule:test");
        long tenantId = LegalContext.tenantId();
        Map<String, Object> rule = mapper.findRule(tenantId, LegalContext.requireLong(body.get("rule_id")));
        if (rule == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String sample = LegalContext.string(body.get("sample_text"));
        List<Map<String, Object>> hits = matchRule(rule, sample);
        return Map.of("matched", !hits.isEmpty(), "hits", hits, "data_level", Rows.str(rule, "data_level"));
    }

    // ---------- 策略 ----------
    public Map<String, Object> createPolicy(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:policy:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String dataLevel = LegalContext.requireText(LegalContext.string(body.get("data_level")));
        int priority = intOrDefault(body.get("priority"), 100);
        String effectiveFrom = LegalContext.string(body.get("effective_from"));
        String effectiveTo = LegalContext.string(body.get("effective_to"));
        if (mapper.countPolicyConflict(tenantId, dataLevel, priority, effectiveFrom, effectiveTo) > 0) {
            throw new BizException(LegalErrorCode.POLICY_CONFLICT);
        }
        long id = IdWorker.getId();
        String policyNo = "DP" + Year.now().getValue()
                + String.format("%06d", commons.nextDlpEventNo() % 1_000_000);
        mapper.insertPolicy(id, tenantId, policyNo,
                LegalContext.requireText(LegalContext.string(body.get("name"))), dataLevel,
                LegalMaps.json(body.get("rule_ids")), LegalMaps.json(body.get("scope_org_ids")),
                LegalMaps.json(body.get("scope_role_ids")), LegalContext.requireText(LegalContext.string(body.get("action"))),
                LegalMaps.json(body.get("channel_codes")), effectiveFrom, effectiveTo, priority, userId);
        writeAudit("POLICY_CONFIG", "DLP_POLICY", id, Map.of("policy_no", policyNo), "SUCCESS", userId);
        audit.low("dlp.policy.create", "DLP_POLICY", id);
        return Map.of("id", id, "policy_no", policyNo, "version", 1);
    }

    public PageResult<Map<String, Object>> listPolicies(String permissions, Integer page, Integer size,
                                                        String dataLevel, String action, Boolean enabled) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:policy:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = LegalMaps.parseAll(mapper.listPolicies(tenantId,
                LegalContext.text(dataLevel), LegalContext.text(action), LegalContext.offset(page, size), limit),
                "rule_ids", "scope_org_ids", "scope_role_ids", "channel_codes");
        return PageResult.of(rows, mapper.countPolicies(tenantId, LegalContext.text(dataLevel),
                LegalContext.text(action)), LegalContext.page(page), limit);
    }

    // ---------- 通道 ----------
    public Map<String, Object> saveChannels(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:channel:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        List<?> channels = body.get("channels") instanceof List<?> list ? list : List.of();
        int saved = 0;
        for (Object item : channels) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> channel = (Map<String, Object>) raw;
            String channelCode = LegalContext.requireText(LegalContext.string(channel.get("channel_code")));
            Long policyId = LegalContext.longValue(channel.get("policy_id"));
            boolean enabled = channel.get("enabled") == null || Boolean.TRUE.equals(channel.get("enabled"));
            String defaultAction = LegalContext.string(channel.get("default_action"));
            String params = LegalMaps.json(channel.get("params"));
            Map<String, Object> existing = mapper.findChannel(tenantId, policyId, channelCode);
            if (existing == null) {
                mapper.insertChannel(IdWorker.getId(), tenantId, policyId, channelCode, enabled, defaultAction,
                        params, userId);
            } else {
                mapper.updateChannel(tenantId, Rows.lngVal(existing, "id"), enabled, defaultAction, params, userId);
            }
            saved++;
        }
        writeAudit("CHANNEL_CONFIG", "DLP_CHANNEL", null, Map.of("channel_count", saved), "SUCCESS", userId);
        return Map.of("saved", true, "channel_count", saved);
    }

    public List<Map<String, Object>> listChannels(String permissions, Long policyId) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:channel:config");
        return LegalMaps.parseAll(mapper.listChannels(LegalContext.tenantId(), policyId), "params");
    }

    // ---------- 白名单 ----------
    public Map<String, Object> createWhitelist(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:whitelist:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String effectiveFrom = LegalContext.string(body.get("effective_from"));
        String effectiveTo = LegalContext.string(body.get("effective_to"));
        if (effectiveFrom == null || effectiveTo == null
                || !Instant.parse(normalizeInstant(effectiveTo)).isAfter(Instant.parse(normalizeInstant(effectiveFrom)))) {
            throw new BizException(LegalErrorCode.WHITELIST_INVALID);
        }
        long id = IdWorker.getId();
        Long approvalRecordId = LegalContext.longValue(body.get("approval_record_id"));
        mapper.insertWhitelist(id, tenantId,
                LegalContext.requireText(LegalContext.string(body.get("name"))),
                LegalContext.requireText(LegalContext.string(body.get("channel_code"))),
                LegalContext.requireText(LegalContext.string(body.get("match_type"))),
                LegalContext.requireText(LegalContext.string(body.get("match_value"))),
                LegalContext.string(body.get("data_level")), LegalContext.string(body.get("reason")),
                approvalRecordId, effectiveFrom, effectiveTo, userId);
        writeAudit("WHITELIST", "DLP_WHITELIST", id, Map.of("name", String.valueOf(body.get("name"))),
                "SUCCESS", userId);
        return Map.of("id", id, "status", "ACTIVE", "approval_record_id", approvalRecordId == null ? 0 : approvalRecordId);
    }

    public PageResult<Map<String, Object>> listWhitelist(String permissions, Integer page, Integer size,
                                                         String channelCode, String status) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:whitelist:config");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listWhitelist(tenantId, LegalContext.text(channelCode),
                        LegalContext.text(status), LegalContext.offset(page, size), limit),
                mapper.countWhitelist(tenantId, LegalContext.text(channelCode), LegalContext.text(status)),
                LegalContext.page(page), limit);
    }

    // ---------- 放行 ----------
    public Map<String, Object> createRelease(String permissions, Map<String, Object> body, String requestId) {
        PermissionGuard.require(permissions, "legal:dlp:release:apply");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        long id = IdWorker.getId();
        String eventNo = "DLPR" + Year.now().getValue()
                + String.format("%06d", commons.nextDlpEventNo() % 1_000_000);
        String channelCode = LegalContext.requireText(LegalContext.string(body.get("channel_code")));
        Map<String, Object> channel = mapper.findChannel(tenantId, null, channelCode);
        if (channel != null && !Boolean.TRUE.equals(channel.get("enabled"))) {
            throw new BizException(LegalErrorCode.CHANNEL_DISABLED);
        }
        int inserted = mapper.insertEvent(id, tenantId, eventNo, "RELEASE_APPLY", channelCode, userId,
                LegalContext.longValue(body.get("org_id")), LegalContext.longValue(body.get("terminal_id")),
                LegalContext.string(body.get("data_level")),
                LegalContext.string(body.get("file_name")), LegalContext.string(body.get("content_fingerprint")),
                LegalMaps.json(body.get("rule_ids")), LegalContext.string(body.get("purpose")),
                "APPROVE", BigDecimal.ZERO, "PENDING", Instant.now().toString(), "PORTAL", userId);
        if (inserted == 0) {
            throw new BizException(LegalErrorCode.EVENT_DUPLICATED);
        }
        String instanceNo = workflowClient.startApproval(permissions, requestId, "legalDlpReleaseApproval", eventNo,
                "DLP_RELEASE", "敏感数据外发放行：" + body.get("file_name"),
                Map.of("release_id", id, "channel_code", channelCode, "target", String.valueOf(body.get("target"))));
        writeAudit("RELEASE", "DLP_RELEASE", id, Map.of("event_no", eventNo, "channel", channelCode),
                "PENDING", userId);
        audit.high("dlp.release.apply", "DLP_RELEASE", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("event_no", eventNo);
        result.put("status", "PENDING");
        result.put("process_instance_no", instanceNo);
        return result;
    }

    public Map<String, Object> approveRelease(String permissions, long releaseId, Map<String, Object> body,
                                              String idemHeader) {
        PermissionGuard.require(permissions, "legal:dlp:release:approve");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/dlp/releases/{id}/approve");
        Map<String, Object> existing = mapper.findEvent(tenantId, releaseId);
        if (existing == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(Rows.str(existing, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        String action = "REJECT".equals(LegalContext.string(body.get("action"))) ? "REJECTED" : "APPROVED";
        mapper.updateEventStatus(tenantId, releaseId, action, action, null, userId);
        long validHours = longOrDefault(body.get("valid_hours"), 24);
        Instant validUntil = Instant.now().plusSeconds(validHours * 3600);
        writeAudit("RELEASE", "DLP_RELEASE", releaseId, Map.of("action", action, "idem_key", key),
                action, userId);
        audit.high("dlp.release.approve", "DLP_RELEASE", releaseId);
        return Map.of("status", action, "valid_until", validUntil.toString());
    }

    public PageResult<Map<String, Object>> listReleases(String permissions, Integer page, Integer size,
                                                       Long applicantId, String status, String from, String to) {
        boolean approver = PermissionGuard.has(permissions, "legal:dlp:release:approve");
        if (!approver) {
            PermissionGuard.requireAny(permissions, "legal:dlp:release:approve", "legal:dlp:release:apply");
        }
        long tenantId = LegalContext.tenantId();
        // 没有放行审批权限时强制收敛到本人，忽略前端传入的 applicant_id。
        Long scopedApplicant = applicantId;
        if (!approver) {
            scopedApplicant = LegalContext.userId();
        }
        int limit = LegalContext.size(size);
        return PageResult.of(
                mapper.listReleases(tenantId, scopedApplicant, LegalContext.text(status),
                        LegalContext.text(from), LegalContext.text(to), LegalContext.offset(page, size), limit),
                mapper.countReleases(tenantId, scopedApplicant, LegalContext.text(status),
                        LegalContext.text(from), LegalContext.text(to)),
                LegalContext.page(page), limit);
    }

    public Map<String, Object> releaseDetail(String permissions, long releaseId) {
        Map<String, Object> release = loadRelease(permissions, releaseId);
        Map<String, Object> result = new LinkedHashMap<>(LegalMaps.parse(release, "matched_rule_ids"));
        result.put("progress", mapper.listReleaseProgress(LegalContext.tenantId(), releaseId));
        return result;
    }

    public Map<String, Object> releaseProgress(String permissions, long releaseId) {
        Map<String, Object> release = loadRelease(permissions, releaseId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", releaseId);
        result.put("event_no", release.get("event_no"));
        result.put("status", release.get("status"));
        result.put("applied_at", release.get("occurred_at"));
        result.put("timeline", mapper.listReleaseProgress(LegalContext.tenantId(), releaseId));
        return result;
    }

    private Map<String, Object> loadRelease(String permissions, long releaseId) {
        boolean approver = PermissionGuard.has(permissions, "legal:dlp:release:approve");
        if (!approver) {
            PermissionGuard.requireAny(permissions, "legal:dlp:release:approve", "legal:dlp:release:apply");
        }
        Map<String, Object> release = mapper.findRelease(LegalContext.tenantId(), releaseId);
        if (release == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!approver && Rows.lngVal(release, "user_id") != LegalContext.userId()) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅能查看本人的放行申请");
        }
        return release;
    }

    // ---------- 事件 ----------
    public PageResult<Map<String, Object>> listEvents(String permissions, Integer page, Integer size,
                                                      String dataLevel, String channelCode, String status,
                                                      String eventType) {
        PermissionGuard.require(permissions, "legal:dlp:audit:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listEvents(tenantId, LegalContext.text(dataLevel),
                        LegalContext.text(channelCode), LegalContext.text(status), LegalContext.text(eventType),
                        LegalContext.offset(page, size), limit),
                mapper.countEvents(tenantId, LegalContext.text(dataLevel), LegalContext.text(channelCode),
                        LegalContext.text(status), LegalContext.text(eventType)), LegalContext.page(page), limit);
    }

    public Map<String, Object> eventDetail(String permissions, long eventId) {
        PermissionGuard.require(permissions, "legal:dlp:audit:view");
        long tenantId = LegalContext.tenantId();
        Map<String, Object> event = mapper.findEvent(tenantId, eventId);
        if (event == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(LegalMaps.parse(event, "matched_rule_ids"));
        result.put("handles", mapper.listEventHandles(tenantId, eventId));
        return result;
    }

    public Map<String, Object> handleEvent(String permissions, long eventId, Map<String, Object> body,
                                           String idemHeader) {
        PermissionGuard.require(permissions, "legal:dlp:event:handle");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/dlp/events/{id}/handle");
        Map<String, Object> event = mapper.findEvent(tenantId, eventId);
        if (event == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String action = LegalContext.requireText(LegalContext.string(body.get("action")));
        if (!HANDLE_ACTIONS.contains(action)) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
        boolean terminal = "CONFIRM".equals(action) || "FALSE_POSITIVE".equals(action);
        if ("CONFIRM".equals(action)
                && (LegalContext.string(body.get("conclusion")) == null
                || LegalContext.string(body.get("rectify_item")) == null)) {
            throw new BizException(LegalErrorCode.REQUIRED_MISSING, "CONFIRM 须填写结论与整改项");
        }
        mapper.insertEventHandle(IdWorker.getId(), tenantId, eventId, action,
                LegalContext.string(body.get("conclusion")), LegalContext.string(body.get("rectify_item")),
                LegalContext.string(body.get("attachment_ref")), LegalContext.string(body.get("sla_deadline")), userId);
        String status = switch (action) {
            case "TRIAGE" -> "TRIAGING";
            case "INVESTIGATE" -> "INVESTIGATING";
            case "CONFIRM" -> "HANDLED";
            case "FALSE_POSITIVE" -> "FALSE_POSITIVE";
            case "ESCALATE" -> "ESCALATED";
            default -> "CLOSED";
        };
        mapper.updateEventStatus(tenantId, eventId, status, null, null, userId);
        writeAudit("EVENT_HANDLE", "DLP_EVENT", eventId,
                Map.of("action", action, "status", status, "idem_key", key), "SUCCESS", userId);
        audit.high("dlp.event.handle", "DLP_EVENT", eventId);
        return Map.of("status", status, "finished", terminal);
    }

    public Map<String, Object> reportEvent(Map<String, Object> body) {
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String channelCode = LegalContext.requireText(LegalContext.string(body.get("channel_code")));
        Map<String, Object> channel = mapper.findChannel(tenantId, null, channelCode);
        if (channel != null && !Boolean.TRUE.equals(channel.get("enabled"))) {
            throw new BizException(LegalErrorCode.CHANNEL_DISABLED);
        }
        Long terminalId = LegalContext.longValue(body.get("terminal_id"));
        String eventNo = LegalContext.string(body.get("event_no"));
        if (eventNo == null) {
            eventNo = "DLP" + Year.now().getValue()
                    + String.format("%06d", commons.nextDlpEventNo() % 1_000_000);
        }
        long id = IdWorker.getId();
        int inserted = mapper.insertEvent(id, tenantId, eventNo,
                defaultIfNull(LegalContext.string(body.get("event_type")), "OUTBOUND"), channelCode,
                LegalContext.longValue(body.get("user_id")), LegalContext.longValue(body.get("org_id")), terminalId,
                LegalContext.string(body.get("data_level")), LegalContext.string(body.get("file_name")),
                LegalContext.string(body.get("file_fingerprint")), LegalMaps.json(body.get("matched_rule_ids")),
                LegalContext.string(body.get("matched_snippet")),
                defaultIfNull(LegalContext.string(body.get("action_taken")), "ALLOW"),
                decimal(body.get("risk_score")), defaultIfNull(LegalContext.string(body.get("status")), "NEW"),
                defaultIfNull(LegalContext.string(body.get("occurred_at")), Instant.now().toString()),
                defaultIfNull(LegalContext.string(body.get("source")), "AGENT"), userId);
        if (inserted == 0) {
            Map<String, Object> existing = mapper.findEventByNo(tenantId, eventNo);
            return Map.of("event_id", existing == null ? 0L : Rows.lngVal(existing, "id"), "merged", true);
        }
        writeAudit("EVENT_REPORT", "DLP_EVENT", id, Map.of("event_no", eventNo, "channel", channelCode),
                "SUCCESS", userId);
        audit.low("dlp.event.raised", "DLP_EVENT", id);
        return Map.of("event_id", id, "event_no", eventNo, "merged", false);
    }

    public Map<String, Object> batchReport(Map<String, Object> body) {
        List<?> events = body.get("events") instanceof List<?> list ? list : List.of();
        if (events.size() > 500) {
            throw new BizException(LegalErrorCode.REPORT_QUOTA_EXCEEDED);
        }
        int accepted = 0;
        int merged = 0;
        int failed = 0;
        for (Object item : events) {
            if (!(item instanceof Map<?, ?> raw)) {
                failed++;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) raw;
            try {
                Map<String, Object> result = reportEvent(event);
                if (Boolean.TRUE.equals(result.get("merged"))) {
                    merged++;
                } else {
                    accepted++;
                }
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return Map.of("accepted", accepted, "merged", merged, "failed", failed);
    }

    // ---------- 决策/识别 ----------
    public Map<String, Object> decide(Map<String, Object> body) {
        long tenantId = LegalContext.tenantId();
        String channelCode = LegalContext.requireText(LegalContext.string(body.get("channel_code")));
        Map<String, Object> channel = mapper.findChannel(tenantId, null, channelCode);
        if (channel != null && !Boolean.TRUE.equals(channel.get("enabled"))) {
            throw new BizException(LegalErrorCode.CHANNEL_DISABLED);
        }
        Detection detection = detect(tenantId, LegalContext.string(body.get("content_sample")));
        String decision = "ALLOW";
        String policyNo = null;
        String reason = "未命中敏感规则";
        if (detection.level != null) {
            Map<String, Object> policy = pickPolicy(tenantId, detection.level, channelCode);
            if (policy != null) {
                policyNo = Rows.str(policy, "policy_no");
                decision = defaultIfNull(Rows.str(policy, "action"), "WARN");
                reason = "命中 " + detection.level + " 级敏感数据管控";
            } else {
                decision = "WARN";
                reason = "命中 " + detection.level + " 级敏感规则";
            }
        }
        writeAudit("DECIDE", "DLP_DECIDE", null,
                Map.of("channel", channelCode, "decision", decision), "SUCCESS", LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", decision);
        result.put("data_level", detection.level);
        result.put("policy_no", policyNo);
        result.put("reason", reason);
        result.put("cache_ttl", 300);
        return result;
    }

    public Map<String, Object> detectSensitive(Map<String, Object> body) {
        long tenantId = LegalContext.tenantId();
        Detection detection = detect(tenantId, LegalContext.string(body.get("content")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data_level", detection.level);
        result.put("matched_rule_ids", detection.ruleIds);
        result.put("hits", detection.hits);
        result.put("confidence", detection.hits.isEmpty() ? 0 : 0.96);
        return result;
    }

    // ---------- 水印 ----------
    public Map<String, Object> createWatermark(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:dlp:watermark:manage");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String fileHash = LegalContext.string(body.get("file_hash"));
        String watermarkNo = "WM" + Year.now().getValue()
                + String.format("%06d", commons.nextWatermarkNo() % 1_000_000);
        String embedCode = "ENC-" + hashChain.sha256(String.valueOf(tenantId), String.valueOf(userId),
                String.valueOf(body.get("biz_id")), String.valueOf(body.get("terminal_id")), watermarkNo)
                .substring(0, 24);
        long id = IdWorker.getId();
        mapper.insertWatermark(id, tenantId, watermarkNo,
                defaultIfNull(LegalContext.string(body.get("biz_type")), "FILE"),
                LegalContext.longValue(body.get("biz_id")),
                defaultIfNull(LegalContext.string(body.get("watermark_type")), "BOTH"), embedCode,
                LegalContext.longValue(body.get("user_id")), LegalContext.longValue(body.get("terminal_id")),
                LegalContext.string(body.get("ip")), fileHash, userId);
        writeAudit("WATERMARK", "WATERMARK", id, Map.of("watermark_no", watermarkNo), "SUCCESS", userId);
        return Map.of("watermark_no", watermarkNo, "embed_code", embedCode,
                "file_ref", body.get("file_ref") == null ? "" : body.get("file_ref"));
    }

    public PageResult<Map<String, Object>> listWatermarks(String permissions, Integer page, Integer size,
                                                          String bizType, String traceStatus) {
        PermissionGuard.require(permissions, "legal:dlp:watermark:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = mapper.listWatermarks(tenantId, LegalContext.text(bizType),
                LegalContext.text(traceStatus), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, mapper.countWatermarks(tenantId), LegalContext.page(page), limit);
    }

    public Map<String, Object> traceWatermark(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:dlp:trace");
        long tenantId = LegalContext.tenantId();
        String embedCode = LegalContext.string(body.get("embed_code"));
        String fileHash = LegalContext.string(body.get("file_hash"));
        Map<String, Object> record = embedCode != null
                ? mapper.findWatermarkByEmbed(tenantId, embedCode)
                : (fileHash != null ? mapper.findWatermarkByFileHash(tenantId, fileHash) : null);
        if (record == null) {
            throw new BizException(LegalErrorCode.WATERMARK_MISMATCH);
        }
        Long eventId = LegalContext.longValue(body.get("event_id"));
        mapper.markTraced(tenantId, Rows.lngVal(record, "id"), eventId, LegalContext.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trace_status", "TRACED");
        result.put("watermark_no", Rows.str(record, "watermark_no"));
        result.put("user_id", Rows.lngVal(record, "user_id"));
        result.put("terminal_id", Rows.lngVal(record, "terminal_id"));
        result.put("ip", Rows.str(record, "ip"));
        result.put("related_event_no", eventId == null ? "" : String.valueOf(eventId));
        return result;
    }

    // ---------- 终端 ----------
    public Map<String, Object> registerTerminal(Map<String, Object> body) {
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String mac = LegalContext.requireText(LegalContext.string(body.get("mac")));
        String hash = hashChain.sha256(String.valueOf(tenantId), mac.toUpperCase());
        String terminalNo = "TM" + Year.now().getValue() + hash.substring(0, 6).toUpperCase();
        Map<String, Object> existing = mapper.findTerminalByNo(tenantId, terminalNo);
        if (existing != null) {
            mapper.heartbeat(tenantId, Rows.lngVal(existing, "id"),
                    LegalContext.string(body.get("agent_version")), null, userId);
            return Map.of("terminal_no", terminalNo, "status", "ONLINE",
                    "policy_version", Rows.intVal(existing, "policy_version"), "heartbeat_interval", 60);
        }
        mapper.insertTerminal(IdWorker.getId(), tenantId, terminalNo,
                LegalContext.string(body.get("hostname")), hash,
                LegalContext.string(body.get("os_type")), LegalContext.string(body.get("os_version")),
                LegalContext.longValue(body.get("user_id")), LegalContext.string(body.get("agent_version")),
                intOrDefault(body.get("policy_version"), 1), userId);
        return Map.of("terminal_no", terminalNo, "status", "ONLINE", "policy_version", 1,
                "heartbeat_interval", 60);
    }

    public PageResult<Map<String, Object>> listTerminals(String permissions, Integer page, Integer size,
                                                         String status) {
        PermissionGuard.require(permissions, "tenant:legal:dlp:terminal:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listTerminals(tenantId, LegalContext.text(status),
                        LegalContext.offset(page, size), limit),
                mapper.countTerminals(tenantId, LegalContext.text(status)), LegalContext.page(page), limit);
    }

    // ---------- 审计 ----------
    public PageResult<Map<String, Object>> listAuditLogs(String permissions, Integer page, Integer size,
                                                         String action) {
        PermissionGuard.require(permissions, "legal:dlp:audit:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = LegalMaps.parseAll(mapper.listAuditLogs(tenantId,
                LegalContext.text(action), LegalContext.offset(page, size), limit), "detail");
        return PageResult.of(rows, mapper.countAuditLogs(tenantId, LegalContext.text(action)),
                LegalContext.page(page), limit);
    }

    public Map<String, Object> verifyAuditChain(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:dlp:audit:integrity");
        long tenantId = LegalContext.tenantId();
        String chainId = LegalContext.string(body.get("chain_id"));
        long fromSeq = longOrDefault(body.get("from_seq"), 1);
        long toSeq = longOrDefault(body.get("to_seq"), Long.MAX_VALUE);
        List<Map<String, Object>> chain = mapper.listAuditChain(tenantId, chainId);
        String prev = null;
        long checked = 0;
        Long brokenAt = null;
        for (Map<String, Object> row : chain) {
            long seq = Rows.lngVal(row, "seq_no");
            if (seq < fromSeq || seq > toSeq) {
                continue;
            }
            String rowPrev = Rows.str(row, "prev_hash");
            if (prev != null && !prev.equals(rowPrev)) {
                brokenAt = seq;
                break;
            }
            prev = Rows.str(row, "block_hash");
            checked++;
        }
        if (brokenAt != null) {
            audit.high("dlp.audit.invalid", "DLP_AUDIT", chainId);
            throw new BizException(LegalErrorCode.DLP_AUDIT_CHAIN_INVALID);
        }
        return Map.of("chain_id", chainId == null ? "" : chainId, "valid", true,
                "broken_at", "", "checked", checked);
    }

    // ---------- 内部 ----------
    private void validateExpression(String ruleType, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new BizException(LegalErrorCode.RULE_EXPRESSION_INVALID);
        }
        if ("REGEX".equals(ruleType)) {
            try {
                Pattern.compile(expression);
            } catch (PatternSyntaxException ex) {
                throw new BizException(LegalErrorCode.RULE_EXPRESSION_INVALID);
            }
        }
    }

    private List<Map<String, Object>> matchRule(Map<String, Object> rule, String text) {
        List<Map<String, Object>> hits = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        String expression = Rows.str(rule, "expression");
        String ruleType = Rows.str(rule, "rule_type");
        if (expression == null || expression.isBlank()) {
            return hits;
        }
        if ("KEYWORD".equals(ruleType)) {
            int index = text.indexOf(expression);
            if (index >= 0) {
                hits.add(hit(rule, text.substring(index, Math.min(text.length(), index + expression.length())), index));
            }
            return hits;
        }
        try {
            Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(text);
            while (matcher.find() && hits.size() < 20) {
                hits.add(hit(rule, matcher.group(), matcher.start()));
            }
        } catch (PatternSyntaxException ignored) {
            // 历史非法表达式跳过，不阻断实时链路
        }
        return hits;
    }

    private Map<String, Object> hit(Map<String, Object> rule, String snippet, int position) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("rule_id", Rows.lngVal(rule, "id"));
        hit.put("snippet", maskSnippet(snippet));
        hit.put("position", position);
        return hit;
    }

    private Detection detect(long tenantId, String text) {
        Detection detection = new Detection();
        if (text == null || text.isBlank()) {
            return detection;
        }
        for (Map<String, Object> rule : mapper.listEnabledRules(tenantId)) {
            List<Map<String, Object>> hits = matchRule(rule, text);
            if (hits.isEmpty()) {
                continue;
            }
            detection.ruleIds.add(Rows.lngVal(rule, "id"));
            detection.hits.addAll(hits);
            detection.level = highest(detection.level, Rows.str(rule, "data_level"));
        }
        return detection;
    }

    private Map<String, Object> pickPolicy(long tenantId, String level, String channelCode) {
        for (Map<String, Object> policy : mapper.listEffectivePolicies(tenantId)) {
            if (!level.equals(Rows.str(policy, "data_level"))) {
                continue;
            }
            String channels = Rows.str(policy, "channel_codes");
            if (channels != null && !channels.contains(channelCode)) {
                continue;
            }
            return policy;
        }
        return null;
    }

    private static String highest(String current, String candidate) {
        if (candidate == null) {
            return current;
        }
        int currentIndex = current == null ? -1 : LEVELS.indexOf(current);
        int candidateIndex = LEVELS.indexOf(candidate);
        return candidateIndex > currentIndex ? candidate : current;
    }

    private void writeAudit(String action, String targetType, Long targetId, Map<String, Object> detail,
                            String result, long userId) {
        long tenantId = LegalContext.tenantId();
        String chainId = "CHAIN_DLP_" + YearMonth.now();
        Map<String, Object> last = mapper.lastAudit(tenantId, chainId);
        long seqNo = last == null ? 1 : Rows.lngVal(last, "seq_no") + 1;
        String prevHash = last == null ? null : Rows.str(last, "block_hash");
        long id = IdWorker.getId();
        String detailJson = LegalMaps.json(detail);
        String blockHash = hashChain.block(String.valueOf(tenantId), "legal_dlp_audit_log", String.valueOf(id),
                action + "|" + targetType + "|" + targetId + "|" + detailJson, prevHash);
        mapper.insertAuditLog(id, tenantId, "LOG" + id, action, userId, targetType, targetId, detailJson, result,
                chainId, seqNo, prevHash, blockHash, "worm://legal/dlp/" + id, userId);
    }

    private static String normalizeInstant(String value) {
        String text = value.strip();
        return text.endsWith("Z") || text.contains("+") ? text : text + "Z";
    }

    private static String maskSnippet(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static String defaultIfNull(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intOrDefault(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static long longOrDefault(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static final class Detection {
        private String level;
        private final List<Long> ruleIds = new ArrayList<>();
        private final List<Map<String, Object>> hits = new ArrayList<>();
    }
}
