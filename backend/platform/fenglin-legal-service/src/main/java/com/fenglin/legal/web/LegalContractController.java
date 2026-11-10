package com.fenglin.legal.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.service.ContractService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * G1 合同域接口：模板、合同、相对方、履约与到期提醒。
 */
@RestController
@RequestMapping("/api/legal/v1")
public class LegalContractController {

    private final ContractService contractService;

    public LegalContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // ---------- 模板 ----------
    @PostMapping("/contract-templates")
    public ApiResponse<Map<String, Object>> createTemplate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.createTemplate(permissions, body));
    }

    @GetMapping("/contract-templates")
    public ApiResponse<PageResult<Map<String, Object>>> listTemplates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status) {
        PermissionGuard.require(permissions, "legal:template:view");
        return ApiResponse.success(contractService.listTemplates(page, size, category, status));
    }

    @PostMapping("/contract-templates/{id}/versions")
    public ApiResponse<Map<String, Object>> publishTemplate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.publishTemplate(permissions, id, body));
    }

    @PutMapping("/admin/contract-templates/{id}/variables")
    public ApiResponse<List<Map<String, Object>>> saveVariables(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.saveVariables(permissions, id, body));
    }

    // ---------- 合同 ----------
    @PostMapping("/contracts")
    public ApiResponse<Map<String, Object>> createContract(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.createContract(permissions, body, idem));
    }

    @GetMapping("/contracts")
    public ApiResponse<PageResult<Map<String, Object>>> listContracts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "owner_id", required = false) Long ownerId,
            @RequestParam(value = "org_id", required = false) Long orgId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PermissionGuard.requireAny(permissions, "legal:contract:view", "legal:contract:view:self");
        return ApiResponse.success(contractService.listContracts(permissions, page, size, status, category, ownerId, orgId, keyword));
    }

    @GetMapping("/contracts/search")
    public ApiResponse<PageResult<Map<String, Object>>> searchContracts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.requireAny(permissions, "legal:contract:view", "legal:contract:view:self");
        return ApiResponse.success(contractService.listContracts(permissions, page, size, status, null, null, null, keyword));
    }

    @GetMapping("/contracts/{id}")
    public ApiResponse<Map<String, Object>> contractDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.requireAny(permissions, "legal:contract:view", "legal:contract:view:self");
        return ApiResponse.success(contractService.contractDetail(permissions, id));
    }

    @PutMapping("/contracts/{id}")
    public ApiResponse<Map<String, Object>> updateContract(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.updateContract(permissions, id, body));
    }

    @DeleteMapping("/contracts/{id}")
    public ApiResponse<Map<String, Object>> deleteDraft(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(contractService.deleteDraft(permissions, id));
    }

    @PostMapping("/contracts/{id}/versions")
    public ApiResponse<Map<String, Object>> saveVersion(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.saveVersion(permissions, id, body));
    }

    @PutMapping("/contracts/{id}/clauses")
    public ApiResponse<List<Map<String, Object>>> saveClauses(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.saveClauses(permissions, id, body));
    }

    @PostMapping("/contracts/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(contractService.submit(permissions, id,
                body == null ? Map.of() : body, idem, requestId));
    }

    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(contractService.approve(permissions, id, body == null ? Map.of() : body, idem));
    }

    @PostMapping("/contracts/{id}/archive")
    public ApiResponse<Map<String, Object>> archive(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id) {
        return ApiResponse.success(contractService.archive(permissions, id, idem));
    }

    @PostMapping("/contracts/{id}/terminate")
    public ApiResponse<Map<String, Object>> terminate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(contractService.terminate(permissions, id, body == null ? Map.of() : body, idem));
    }

    @PostMapping("/contracts/{id}/verify-integrity")
    public ApiResponse<Map<String, Object>> verifyIntegrity(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(contractService.verifyIntegrity(permissions, id));
    }

    // ---------- 相对方 ----------
    @PostMapping("/contract-parties")
    public ApiResponse<Map<String, Object>> createParty(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.createParty(permissions, body));
    }

    @GetMapping("/contract-parties")
    public ApiResponse<PageResult<Map<String, Object>>> listParties(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        PermissionGuard.require(permissions, "legal:party:view");
        return ApiResponse.success(contractService.listParties(page, size, keyword));
    }

    // ---------- 履约与提醒 ----------
    @PostMapping("/performance-nodes")
    public ApiResponse<Map<String, Object>> createPerformanceNode(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.createPerformanceNode(permissions, body));
    }

    @GetMapping("/performance-nodes")
    public ApiResponse<PageResult<Map<String, Object>>> listPerformanceNodes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "contract_id", required = false) Long contractId,
            @RequestParam(value = "status", required = false) String status) {
        PermissionGuard.require(permissions, "legal:performance:view");
        return ApiResponse.success(contractService.listPerformanceNodes(page, size, contractId, status));
    }

    @PostMapping("/performance-nodes/{id}/status")
    public ApiResponse<Map<String, Object>> updatePerformanceStatus(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.updatePerformanceStatus(permissions, id, body));
    }

    @GetMapping("/expiry-reminders")
    public ApiResponse<PageResult<Map<String, Object>>> listReminders(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "contract_id", required = false) Long contractId,
            @RequestParam(value = "status", required = false) String status) {
        PermissionGuard.require(permissions, "legal:contract:view");
        return ApiResponse.success(contractService.listReminders(page, size, contractId, status));
    }

    @PostMapping("/expiry-reminders")
    public ApiResponse<Map<String, Object>> createReminder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(contractService.createReminder(permissions, body));
    }
}
