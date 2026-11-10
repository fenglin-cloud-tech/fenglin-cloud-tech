package com.fenglin.legal.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.legal.service.LegalAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 云法管理端报表与配置接口。
 */
@RestController
@RequestMapping("/api/legal/v1/admin")
public class LegalAdminController {

    private final LegalAdminService service;

    public LegalAdminController(LegalAdminService service) {
        this.service = service;
    }

    @GetMapping("/reports/contracts")
    public ApiResponse<Map<String, Object>> contractReport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "dim", required = false) String dim) {
        return ApiResponse.success(service.contractReport(permissions, dim));
    }

    @GetMapping("/reports/dlp")
    public ApiResponse<Map<String, Object>> dlpReport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "dim", required = false) String dim) {
        return ApiResponse.success(service.dlpReport(permissions, dim));
    }

    @GetMapping("/reports/sign-compliance")
    public ApiResponse<Map<String, Object>> signComplianceReport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        return ApiResponse.success(service.signComplianceReport(permissions));
    }

    @GetMapping("/config/reminder")
    public ApiResponse<Map<String, Object>> reminderConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        return ApiResponse.success(service.reminderConfig(permissions));
    }

    @PutMapping("/config/reminder")
    public ApiResponse<Map<String, Object>> saveReminderConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.saveReminderConfig(permissions, body));
    }

    @GetMapping("/config/contract-fields")
    public ApiResponse<Map<String, Object>> contractFieldConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        return ApiResponse.success(service.contractFieldConfig(permissions));
    }

    @PutMapping("/config/contract-fields")
    public ApiResponse<Map<String, Object>> saveContractFieldConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.saveContractFieldConfig(permissions, body));
    }

    @GetMapping("/config/dictionary")
    public ApiResponse<Map<String, Object>> dictionary(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.success(service.dictionary(permissions, type));
    }

    @PutMapping("/config/dictionary")
    public ApiResponse<Map<String, Object>> saveDictionary(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.saveDictionary(permissions, body));
    }

    @PostMapping("/config/ca-providers")
    public ApiResponse<Map<String, Object>> saveCaProvider(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.saveCaProvider(permissions, body));
    }

    @GetMapping("/config/ca-providers")
    public ApiResponse<List<Map<String, Object>>> listCaProviders(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        return ApiResponse.success(service.listCaProviders(permissions));
    }
}
