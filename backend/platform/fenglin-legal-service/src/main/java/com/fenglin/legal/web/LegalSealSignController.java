package com.fenglin.legal.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.legal.service.SealSignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * G1 印章与电子签署接口：印章、用印申请/审批/执行、签署方、签署任务、回执与存证。
 */
@RestController
@RequestMapping("/api/legal/v1")
public class LegalSealSignController {

    private final SealSignService service;

    public LegalSealSignController(SealSignService service) {
        this.service = service;
    }

    // ---------- 印章 ----------
    @PostMapping("/admin/seals")
    public ApiResponse<Map<String, Object>> createSeal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createSeal(permissions, body));
    }

    @GetMapping("/admin/seals")
    public ApiResponse<PageResult<Map<String, Object>>> listSeals(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "seal_type", required = false) String sealType,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(service.listSeals(permissions, page, size, sealType, status));
    }

    @PostMapping("/admin/seals/{id}/revoke")
    public ApiResponse<Map<String, Object>> revokeSeal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(service.revokeSeal(permissions, id));
    }

    // ---------- 用印 ----------
    @PostMapping("/seal-applications")
    public ApiResponse<Map<String, Object>> applySeal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.applySeal(permissions, body, idem, requestId));
    }

    @GetMapping("/seal-applications")
    public ApiResponse<PageResult<Map<String, Object>>> listSealApplications(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(service.listSealApplications(permissions, page, size, status));
    }

    @PostMapping("/seal-applications/{id}/approve")
    public ApiResponse<Map<String, Object>> approveSeal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.approveSeal(permissions, id, body, idem));
    }

    @PostMapping("/seal-applications/{id}/execute")
    public ApiResponse<Map<String, Object>> executeSeal(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.executeSeal(permissions, id, body, idem));
    }

    @GetMapping("/seal-records")
    public ApiResponse<PageResult<Map<String, Object>>> listSealRecords(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "contract_id", required = false) Long contractId) {
        return ApiResponse.success(service.listSealRecords(permissions, page, size, contractId));
    }

    // ---------- 签署方 ----------
    @PostMapping("/contract-signatories")
    public ApiResponse<Map<String, Object>> createSignatory(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.createSignatory(permissions, body));
    }

    @GetMapping("/contract-signatories")
    public ApiResponse<List<Map<String, Object>>> listSignatories(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("contract_id") long contractId) {
        return ApiResponse.success(service.listSignatories(permissions, contractId));
    }

    // ---------- 签署 ----------
    @PostMapping("/contracts/{id}/sign/initiate")
    public ApiResponse<Map<String, Object>> initiateSign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(service.initiateSign(permissions, id, body == null ? Map.of() : body,
                idem, requestId));
    }

    @GetMapping("/sign-tasks")
    public ApiResponse<PageResult<Map<String, Object>>> listSignTasks(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.success(service.listSignTasks(permissions, page, size));
    }

    @PostMapping("/sign-tasks/{taskId}/sign")
    public ApiResponse<Map<String, Object>> selfSign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("taskId") long taskId, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.selfSign(permissions, taskId, body, idem));
    }

    @PostMapping("/sign-tasks/{taskId}/reject")
    public ApiResponse<Map<String, Object>> rejectSign(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idem,
            @PathVariable("taskId") long taskId, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.rejectSign(permissions, taskId, body, idem));
    }

    @GetMapping("/sign-records")
    public ApiResponse<PageResult<Map<String, Object>>> listSignRecords(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "contract_id", required = false) Long contractId,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(service.listSignRecords(permissions, page, size, contractId, status));
    }

    @PostMapping("/internal/legal/sign/callback")
    public ApiResponse<Map<String, Object>> signCallback(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(service.signCallback(body));
    }

    // ---------- 存证 ----------
    @GetMapping("/evidences")
    public ApiResponse<PageResult<Map<String, Object>>> listEvidence(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "contract_id", required = false) Long contractId,
            @RequestParam(value = "evidence_type", required = false) String evidenceType) {
        return ApiResponse.success(service.listEvidence(permissions, page, size, contractId, evidenceType));
    }

    @PostMapping("/evidences/{id}/verify")
    public ApiResponse<Map<String, Object>> verifyEvidence(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        return ApiResponse.success(service.verifyEvidence(permissions, id));
    }

    @GetMapping("/evidences/chain-verify")
    public ApiResponse<Map<String, Object>> verifyEvidenceChain(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("contract_id") long contractId) {
        return ApiResponse.success(service.verifyEvidenceChain(permissions, contractId));
    }
}
