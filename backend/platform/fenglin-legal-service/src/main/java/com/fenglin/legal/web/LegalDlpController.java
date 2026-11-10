package com.fenglin.legal.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.legal.service.DlpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G2 内部信息防泄漏接口：规则、策略、通道、白名单、终端、事件、决策、放行、水印与审计。
 */
@RestController
@RequestMapping("/api/legal/v1")
public class LegalDlpController {

    private final DlpService service;

    public LegalDlpController(DlpService service) {
        this.service = service;
    }

    // ---------- 规则 ----------
    @PostMapping("/admin/dlp/rules")
    public ApiResponse<Map<String, Object>> createRule(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createRule(permissions, body));
    }

    @GetMapping("/admin/dlp/rules")
    public ApiResponse<PageResult<Map<String, Object>>> listRules(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "data_level", required = false) String dataLevel,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return ApiResponse.success(service.listRules(permissions, page, size, dataLevel, enabled));
    }

    @PostMapping("/admin/dlp/rules/{id}/test")
    public ApiResponse<Map<String, Object>> testRule(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("rule_id", id);
        return ApiResponse.success(service.testRule(permissions, payload));
    }

    // ---------- 策略/通道 ----------
    @PostMapping("/admin/dlp/policies")
    public ApiResponse<Map<String, Object>> createPolicy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createPolicy(permissions, body));
    }

    @GetMapping("/admin/dlp/policies")
    public ApiResponse<PageResult<Map<String, Object>>> listPolicies(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "data_level", required = false) String dataLevel,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return ApiResponse.success(service.listPolicies(permissions, page, size, dataLevel, action, enabled));
    }

    @PutMapping("/admin/dlp/channels")
    public ApiResponse<Map<String, Object>> saveChannels(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.saveChannels(permissions, body));
    }

    @GetMapping("/admin/dlp/channels")
    public ApiResponse<List<Map<String, Object>>> listChannels(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "policy_id", required = false) Long policyId) {
        return ApiResponse.success(service.listChannels(permissions, policyId));
    }

    // ---------- 白名单 ----------
    @PostMapping("/admin/dlp/whitelist")
    public ApiResponse<Map<String, Object>> createWhitelist(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createWhitelist(permissions, body));
    }

    @GetMapping("/admin/dlp/whitelist")
    public ApiResponse<PageResult<Map<String, Object>>> listWhitelist(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "channel_code", required = false) String channelCode,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(service.listWhitelist(permissions, page, size, channelCode, status));
    }

    // ---------- 事件与放行 ----------
    @GetMapping("/dlp/events")
    public ApiResponse<PageResult<Map<String, Object>>> listEvents(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "data_level", required = false) String dataLevel,
            @RequestParam(value = "channel_code", required = false) String channelCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_type", required = false) String eventType) {
        return ApiResponse.success(service.listEvents(permissions, page, size, dataLevel, channelCode, status, eventType));
    }

    @GetMapping("/dlp/events/{id}")
    public ApiResponse<Map<String, Object>> eventDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(service.eventDetail(permissions, id));
    }

    @PostMapping("/dlp/events/{id}/handle")
    public ApiResponse<Map<String, Object>> handleEvent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.handleEvent(permissions, id, body, idem));
    }

    @PostMapping("/dlp/releases")
    public ApiResponse<Map<String, Object>> createRelease(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createRelease(permissions, body, requestId));
    }

    @PostMapping("/dlp/releases/{id}/approve")
    public ApiResponse<Map<String, Object>> approveRelease(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.approveRelease(permissions, id, body, idem));
    }

    @GetMapping("/dlp/releases")
    public ApiResponse<PageResult<Map<String, Object>>> listReleases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "applicant_id", required = false) Long applicantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ApiResponse.success(service.listReleases(permissions, page, size, applicantId, status, from, to));
    }

    @GetMapping("/dlp/releases/{id}")
    public ApiResponse<Map<String, Object>> releaseDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(service.releaseDetail(permissions, id));
    }

    @GetMapping("/dlp/releases/{id}/progress")
    public ApiResponse<Map<String, Object>> releaseProgress(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(service.releaseProgress(permissions, id));
    }

    // ---------- 水印 ----------
    @PostMapping("/admin/dlp/watermarks")
    public ApiResponse<Map<String, Object>> createWatermark(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createWatermark(permissions, body));
    }

    @GetMapping("/admin/dlp/watermarks")
    public ApiResponse<PageResult<Map<String, Object>>> listWatermarks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "biz_type", required = false) String bizType,
            @RequestParam(value = "trace_status", required = false) String traceStatus) {
        return ApiResponse.success(service.listWatermarks(permissions, page, size, bizType, traceStatus));
    }

    @PostMapping("/admin/dlp/watermarks/trace")
    public ApiResponse<Map<String, Object>> traceWatermark(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.traceWatermark(permissions, body));
    }

    // ---------- 终端/审计 ----------
    @GetMapping("/admin/dlp/terminals")
    public ApiResponse<PageResult<Map<String, Object>>> listTerminals(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(service.listTerminals(permissions, page, size, status));
    }

    @GetMapping("/admin/dlp/audit-logs")
    public ApiResponse<PageResult<Map<String, Object>>> listAuditLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "action", required = false) String action) {
        return ApiResponse.success(service.listAuditLogs(permissions, page, size, action));
    }

    @PostMapping("/admin/dlp/audit-logs/verify")
    public ApiResponse<Map<String, Object>> verifyAuditChain(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.verifyAuditChain(permissions, body));
    }

    // ---------- 内部能力 ----------
    @PostMapping("/internal/legal/dlp/decide")
    public ApiResponse<Map<String, Object>> decide(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.decide(body));
    }

    @PostMapping("/internal/legal/dlp/detect")
    public ApiResponse<Map<String, Object>> detect(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.detectSensitive(body));
    }

    @PostMapping("/internal/legal/dlp/report")
    public ApiResponse<Map<String, Object>> report(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.reportEvent(body));
    }

    @PostMapping("/internal/legal/dlp/report/batch")
    public ApiResponse<Map<String, Object>> batchReport(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.batchReport(body));
    }

    @PostMapping("/internal/legal/terminal/register")
    public ApiResponse<Map<String, Object>> registerTerminal(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.registerTerminal(body));
    }
}
