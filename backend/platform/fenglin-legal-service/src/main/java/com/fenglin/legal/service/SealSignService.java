package com.fenglin.legal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.client.WorkflowClient;
import com.fenglin.legal.mapper.CommonsMapper;
import com.fenglin.legal.mapper.ContractMapper;
import com.fenglin.legal.mapper.SealSignMapper;
import com.fenglin.legal.support.HashChain;
import com.fenglin.legal.support.LegalAudit;
import com.fenglin.legal.support.LegalContext;
import com.fenglin.legal.support.LegalCrypto;
import com.fenglin.legal.support.LegalIdempotency;
import com.fenglin.legal.support.LegalMaps;
import com.fenglin.legal.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G1 用印与签署服务：印章、用印申请/审批/执行、签署方、签署发起/执行、存证与核验。
 */
@Service
@Transactional
public class SealSignService {

    private final SealSignMapper mapper;
    private final ContractMapper contractMapper;
    private final CommonsMapper commonsMapper;
    private final LegalCrypto crypto;
    private final LegalAudit audit;
    private final LegalIdempotency idempotency;
    private final HashChain hashChain;
    private final WorkflowClient workflowClient;

    public SealSignService(SealSignMapper mapper, ContractMapper contractMapper, CommonsMapper commonsMapper,
                           LegalCrypto crypto, LegalAudit audit, LegalIdempotency idempotency,
                           HashChain hashChain, WorkflowClient workflowClient) {
        this.mapper = mapper;
        this.contractMapper = contractMapper;
        this.commonsMapper = commonsMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.idempotency = idempotency;
        this.hashChain = hashChain;
        this.workflowClient = workflowClient;
    }

    // ---------- 印章 ----------
    public Map<String, Object> createSeal(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:seal:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        long id = IdWorker.getId();
        String sealNo = "SEAL" + Year.now().getValue()
                + String.format("%06d", commonsMapper.nextSealApplyNo() % 1_000_000);
        mapper.insertSeal(id, tenantId, sealNo,
                LegalContext.requireText(LegalContext.string(body.get("name"))),
                LegalContext.requireText(LegalContext.string(body.get("seal_type"))),
                LegalContext.string(body.get("subject_name")), LegalContext.string(body.get("image_ref")),
                LegalContext.string(body.get("cert_serial")), LegalContext.string(body.get("cert_algorithm")),
                LegalContext.string(body.get("cert_not_before")), LegalContext.string(body.get("cert_not_after")),
                LegalContext.string(body.get("provider_code")), LegalContext.longValue(body.get("keeper_id")),
                LegalContext.string(body.get("remark")), userId);
        audit.low("seal.create", "SEAL", id);
        return LegalMaps.parse(mapper.findSeal(tenantId, id));
    }

    public PageResult<Map<String, Object>> listSeals(String permissions, Integer page, Integer size,
                                                     String sealType, String status) {
        PermissionGuard.require(permissions, "tenant:legal:seal:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = mapper.listSeals(tenantId, LegalContext.text(sealType),
                LegalContext.text(status), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, mapper.countSeals(tenantId, LegalContext.text(sealType),
                LegalContext.text(status)), LegalContext.page(page), limit);
    }

    public Map<String, Object> revokeSeal(String permissions, long sealId) {
        PermissionGuard.require(permissions, "tenant:legal:seal:config");
        mapper.revokeSeal(LegalContext.tenantId(), sealId, LegalContext.userId());
        audit.high("seal.revoke", "SEAL", sealId);
        return Map.of("id", sealId, "status", "REVOKED");
    }

    // ---------- 用印 ----------
    public Map<String, Object> applySeal(String permissions, Map<String, Object> body, String idemHeader,
                                         String requestId) {
        PermissionGuard.require(permissions, "legal:seal:apply");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/seal-applications");
        Long existing = idempotency.find(key, "/api/legal/v1/seal-applications");
        if (existing != null) {
            return LegalMaps.parse(mapper.findSealApplication(tenantId, existing));
        }
        long contractId = LegalContext.requireLong(body.get("contract_id"));
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String status = Rows.str(contract, "status");
        if (!List.of("APPROVED", "SEALING").contains(status)) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        if ("SEALING".equals(status)) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID, "合同已处于用印流程");
        }
        long sealId = LegalContext.requireLong(body.get("seal_id"));
        Map<String, Object> seal = mapper.findSeal(tenantId, sealId);
        if (seal == null) {
            throw new BizException(LegalErrorCode.SEAL_UNAVAILABLE);
        }
        if (!"ACTIVE".equals(Rows.str(seal, "status"))) {
            throw new BizException(LegalErrorCode.SEAL_UNAVAILABLE);
        }
        Long contractVersionId = Rows.lng(contract, "current_version_id");
        long applicationId = IdWorker.getId();
        String applyNo = "SEAL" + Year.now().getValue()
                + String.format("%06d", commonsMapper.nextSealApplyNo() % 1_000_000);
        mapper.insertSealApplication(applicationId, tenantId, applyNo, contractId, contractVersionId, sealId,
                LegalContext.requireText(LegalContext.string(body.get("seal_type"))),
                intOrDefault(body.get("copies"), 1), LegalContext.string(body.get("purpose")),
                LegalContext.string(body.get("position_desc")), userId);
        contractMapper.updateContractState(tenantId, contractId, "SEALING", "PENDING", null, null, userId);
        String instanceNo = workflowClient.startApproval(permissions, requestId, "legalSealApproval", applyNo,
                "SEAL", "用印审批：" + Rows.str(contract, "name"),
                Map.of("application_id", applicationId, "contract_id", contractId, "seal_id", sealId));
        idempotency.remember(key, "/api/legal/v1/seal-applications", applicationId);
        audit.high("seal.apply", "SEAL_APPLICATION", applicationId);
        Map<String, Object> result = LegalMaps.parse(mapper.findSealApplication(tenantId, applicationId));
        result.put("process_instance_no", instanceNo);
        return result;
    }

    public PageResult<Map<String, Object>> listSealApplications(String permissions, Integer page, Integer size,
                                                               String status) {
        PermissionGuard.require(permissions, "legal:seal:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listSealApplications(tenantId, LegalContext.text(status),
                        LegalContext.offset(page, size), limit),
                mapper.countSealApplications(tenantId, LegalContext.text(status)), LegalContext.page(page), limit);
    }

    public Map<String, Object> approveSeal(String permissions, long applicationId, Map<String, Object> body,
                                           String idemHeader) {
        PermissionGuard.require(permissions, "legal:seal:approve");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/seal-applications/{id}/approve");
        Long existing = idempotency.find(key, "/api/legal/v1/seal-applications/{id}/approve");
        if (existing != null) {
            return LegalMaps.parse(mapper.findSealApplication(tenantId, existing));
        }
        Map<String, Object> application = mapper.findSealApplication(tenantId, applicationId);
        if (application == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(Rows.str(application, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        String action = "REJECT".equals(LegalContext.string(body.get("action"))) ? "REJECT" : "APPROVE";
        mapper.updateSealApplicationStatus(tenantId, applicationId, action, userId);
        long contractId = Rows.lngVal(application, "contract_id");
        if ("REJECT".equals(action)) {
            contractMapper.updateContractState(tenantId, contractId, "APPROVED", "NONE", null, null, userId);
        }
        contractMapper.insertApprovalRecord(IdWorker.getId(), tenantId, "SEAL", applicationId,
                LegalContext.string(body.get("process_instance_id")), LegalContext.string(body.get("task_id")),
                LegalContext.string(body.get("node_name")), userId, action,
                LegalContext.string(body.get("comment")), Rows.lng(application, "contract_version_id"), userId);
        idempotency.remember(key, "/api/legal/v1/seal-applications/{id}/approve", applicationId);
        audit.high("seal.approve", "SEAL_APPLICATION", applicationId);
        return LegalMaps.parse(mapper.findSealApplication(tenantId, applicationId));
    }

    public Map<String, Object> executeSeal(String permissions, long applicationId, Map<String, Object> body,
                                           String idemHeader) {
        PermissionGuard.require(permissions, "legal:seal:execute");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/seal-applications/{id}/execute");
        Long existing = idempotency.find(key, "/api/legal/v1/seal-applications/{id}/execute");
        if (existing != null) {
            return Map.of("seal_record_id", existing);
        }
        Map<String, Object> application = mapper.findSealApplication(tenantId, applicationId);
        if (application == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"APPROVE".equals(Rows.str(application, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID, "用印申请未审批通过");
        }
        long contractId = Rows.lngVal(application, "contract_id");
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        long applicationVersionId = Rows.lngVal(application, "contract_version_id");
        long currentVersionId = Rows.lngVal(contract, "current_version_id");
        if (applicationVersionId != currentVersionId) {
            throw new BizException(LegalErrorCode.SEAL_VERSION_MISMATCH);
        }
        Map<String, Object> seal = mapper.findSeal(tenantId, Rows.lngVal(application, "seal_id"));
        if (seal == null || !"ACTIVE".equals(Rows.str(seal, "status"))) {
            throw new BizException(LegalErrorCode.SEAL_UNAVAILABLE);
        }
        Map<String, Object> version = contractMapper.findLatestContractVersion(tenantId, contractId);
        String versionHash = version == null ? "" : Rows.str(version, "content_hash");
        String chainId = "SEAL-" + contractId;
        String prevHash = mapper.lastSealHash(tenantId, chainId);
        long seqNo = 1;
        Long latestCount = null;
        List<Map<String, Object>> records = mapper.listSealRecords(tenantId, contractId, 0, 200);
        if (!records.isEmpty()) {
            seqNo = Rows.lngVal(records.get(0), "seq_no") + 1;
            latestCount = Rows.lng(records.get(0), "seq_no");
        }
        long recordId = IdWorker.getId();
        String recordNo = "SEALR" + Year.now().getValue()
                + String.format("%06d", commonsMapper.nextSealRecordNo() % 1_000_000);
        String blockHash = hashChain.block(String.valueOf(tenantId), "legal_seal_record", String.valueOf(recordId),
                versionHash + "|" + applicationId, prevHash);
        mapper.insertSealRecord(recordId, tenantId, recordNo, applicationId, contractId,
                Rows.lngVal(application, "seal_id"), applicationVersionId, versionHash,
                intOrDefault(application.get("copies"), 1), LegalContext.string(body.get("image_ref")),
                chainId, seqNo, prevHash, blockHash, userId);
        mapper.updateSealApplicationStatus(tenantId, applicationId, "EXECUTED", userId);
        contractMapper.updateContractState(tenantId, contractId, "APPROVED", "SEALED", null, null, userId);
        idempotency.remember(key, "/api/legal/v1/seal-applications/{id}/execute", recordId);
        audit.high("seal.execute", "SEAL_RECORD", recordId);
        return Map.of("seal_record_id", recordId, "record_no", recordNo, "block_hash", blockHash,
                "seq_no", seqNo, "previous_seq", latestCount == null ? 0 : latestCount);
    }

    public PageResult<Map<String, Object>> listSealRecords(String permissions, Integer page, Integer size,
                                                           Long contractId) {
        PermissionGuard.require(permissions, "legal:seal:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = mapper.listSealRecords(tenantId, contractId,
                LegalContext.offset(page, size), limit);
        return PageResult.of(rows, mapper.countSealRecords(tenantId, contractId), LegalContext.page(page), limit);
    }

    // ---------- 签署方 ----------
    public Map<String, Object> createSignatory(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:signatory:manage");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        long contractId = LegalContext.requireLong(body.get("contract_id"));
        if (contractMapper.findContract(tenantId, contractId) == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String idNo = LegalContext.string(body.get("signer_id_no"));
        String mobile = LegalContext.string(body.get("signer_mobile"));
        long id = IdWorker.getId();
        mapper.insertSignatory(id, tenantId, contractId,
                defaultIfNull(LegalContext.string(body.get("party_type")), "EXTERNAL"),
                LegalContext.longValue(body.get("party_id")),
                LegalContext.requireText(LegalContext.string(body.get("signer_name"))),
                crypto.encrypt(tenantId, "legal_signatory", "signer_id_no", idNo),
                crypto.hash(tenantId, idNo),
                crypto.encrypt(tenantId, "legal_signatory", "signer_mobile", mobile),
                crypto.hash(tenantId, mobile), crypto.tail(mobile),
                defaultIfNull(LegalContext.string(body.get("seal_mode")), "HANDWRITE"),
                intOrDefault(body.get("sign_order"), 1),
                defaultIfNull(LegalContext.string(body.get("sign_mode")), "SEQ"), userId);
        audit.low("signatory.create", "SIGNATORY", id);
        return Map.of("id", id, "contract_id", contractId, "signer_mobile_tail", crypto.tail(mobile));
    }

    public List<Map<String, Object>> listSignatories(String permissions, long contractId) {
        PermissionGuard.require(permissions, "legal:sign:view");
        return mapper.listSignatories(LegalContext.tenantId(), contractId);
    }

    // ---------- 签署 ----------
    public Map<String, Object> initiateSign(String permissions, long contractId, Map<String, Object> body,
                                            String idemHeader, String requestId) {
        PermissionGuard.require(permissions, "legal:sign:initiate");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/contracts/{id}/sign/initiate");
        Long existing = idempotency.find(key, "/api/legal/v1/contracts/{id}/sign/initiate");
        if (existing != null) {
            return Map.of("contract_id", contractId, "sign_record_id", existing);
        }
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"APPROVED".equals(Rows.str(contract, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID, "合同须审批通过后才能发起签署");
        }
        List<Map<String, Object>> signatories = mapper.listSignatories(tenantId, contractId);
        if (signatories.isEmpty()) {
            throw new BizException(LegalErrorCode.REQUIRED_MISSING, "缺少签署方");
        }
        Map<String, Object> version = contractMapper.findLatestContractVersion(tenantId, contractId);
        String docHash = version == null ? "" : Rows.str(version, "content_hash");
        String chainId = "SIGN-" + contractId;
        String prevHash = mapper.lastSignHash(tenantId, chainId);
        long lastRecordId = 0;
        for (Map<String, Object> signatory : signatories) {
            String taskNo = "SIGN" + Year.now().getValue()
                    + String.format("%06d", commonsMapper.nextSignTaskNo() % 1_000_000);
            long recordId = IdWorker.getId();
            String blockHash = hashChain.block(String.valueOf(tenantId), "legal_sign_record",
                    String.valueOf(recordId), docHash + "|" + Rows.lngVal(signatory, "id"), prevHash);
            mapper.insertSignRecord(recordId, tenantId, taskNo, contractId,
                    Rows.lngVal(contract, "current_version_id"), Rows.lngVal(signatory, "id"),
                    LegalContext.string(body.get("provider_code")),
                    LegalContext.string(body.get("provider_code")) == null ? null
                            : "TASK-" + java.util.UUID.randomUUID(),
                    false, false, null, null, docHash, null, null, null, "PENDING", null,
                    chainId, prevHash, blockHash, userId);
            prevHash = blockHash;
            lastRecordId = recordId;
        }
        contractMapper.updateContractState(tenantId, contractId, "SIGNING", null, "SIGNING", null, userId);
        String instanceNo = workflowClient.startApproval(permissions, requestId, "legalContractSign",
                Rows.str(contract, "contract_no"), "CONTRACT_SIGN", "合同签署：" + Rows.str(contract, "name"),
                Map.of("contract_id", contractId, "sign_record_id", lastRecordId));
        idempotency.remember(key, "/api/legal/v1/contracts/{id}/sign/initiate", lastRecordId);
        audit.high("sign.initiate", "CONTRACT", contractId);
        return Map.of("contract_id", contractId, "sign_record_id", lastRecordId,
                "sign_record_count", signatories.size(), "process_instance_no", instanceNo);
    }

    public Map<String, Object> selfSign(String permissions, long signRecordId, Map<String, Object> body,
                                        String idemHeader) {
        PermissionGuard.require(permissions, "legal:sign:self:execute");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/sign-records/{id}/sign");
        Long existing = idempotency.find(key, "/api/legal/v1/sign-records/{id}/sign");
        if (existing != null) {
            return Map.of("sign_record_id", existing, "status", "SIGNED");
        }
        Map<String, Object> record = mapper.findSignRecord(tenantId, signRecordId);
        if (record == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(Rows.str(record, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        if (!Boolean.TRUE.equals(body.get("step_up"))) {
            throw new BizException(LegalErrorCode.SIGNER_VERIFY_FAILED, "签署执行需 step_up 二次认证");
        }
        boolean realName = Boolean.TRUE.equals(body.get("real_name_verified"));
        boolean will = Boolean.TRUE.equals(body.get("will_confirmed"));
        String authMode = LegalContext.string(body.get("auth_mode"));
        String tsaToken = LegalContext.string(body.get("tsa_token"));
        if (!realName || !will || authMode == null || tsaToken == null) {
            throw new BizException(LegalErrorCode.EVIDENCE_CHAIN_INVALID,
                    "签署证据链缺少实名认证/意愿确认/时间戳");
        }
        long contractId = Rows.lngVal(record, "contract_id");
        mapper.updateSignRecordStatus(tenantId, signRecordId, "SIGNED", true, true, authMode, tsaToken,
                LegalContext.string(body.get("sign_data")), LegalContext.string(body.get("sign_ip")),
                LegalContext.string(body.get("sign_device")), userId);
        mapper.updateSignatoryStatus(tenantId, Rows.lngVal(record, "signatory_id"), "SIGNED", userId);
        long pending = mapper.countSignRecords(tenantId, contractId, "PENDING");
        if (pending == 0) {
            contractMapper.updateContractState(tenantId, contractId, "SIGNED", null, "SIGNED", null, userId);
            preserveEvidence(tenantId, userId, contractId, signRecordId, Rows.str(record, "doc_hash"));
        }
        idempotency.remember(key, "/api/legal/v1/sign-records/{id}/sign", signRecordId);
        audit.high("sign.complete", "SIGN_RECORD", signRecordId);
        return Map.of("sign_record_id", signRecordId, "status", "SIGNED",
                "contract_all_signed", pending == 0);
    }

    public Map<String, Object> rejectSign(String permissions, long signRecordId, Map<String, Object> body,
                                          String idemHeader) {
        PermissionGuard.require(permissions, "legal:sign:self:execute");
        long tenantId = LegalContext.tenantId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/sign-records/{id}/reject");
        Long existing = idempotency.find(key, "/api/legal/v1/sign-records/{id}/reject");
        if (existing != null) {
            return Map.of("sign_record_id", existing, "status", "REJECTED");
        }
        Map<String, Object> record = mapper.findSignRecord(tenantId, signRecordId);
        if (record == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        mapper.updateSignRecordStatus(tenantId, signRecordId, "REJECTED", Rows.bool(record, "real_name_verified"),
                Rows.bool(record, "will_confirmed"), Rows.str(record, "auth_mode"), Rows.str(record, "tsa_token"),
                null, null, null, LegalContext.userId());
        mapper.updateSignatoryStatus(tenantId, Rows.lngVal(record, "signatory_id"), "REJECTED", LegalContext.userId());
        idempotency.remember(key, "/api/legal/v1/sign-records/{id}/reject", signRecordId);
        audit.high("sign.reject", "SIGN_RECORD", signRecordId);
        return Map.of("sign_record_id", signRecordId, "status", "REJECTED");
    }

    public PageResult<Map<String, Object>> listSignTasks(String permissions, Integer page, Integer size) {
        PermissionGuard.require(permissions, "legal:sign:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        return PageResult.of(mapper.listSignTasks(tenantId, LegalContext.offset(page, size), limit),
                mapper.countSignTasks(tenantId), LegalContext.page(page), limit);
    }

    public PageResult<Map<String, Object>> listSignRecords(String permissions, Integer page, Integer size,
                                                           Long contractId, String status) {
        PermissionGuard.require(permissions, "legal:sign:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = mapper.listSignRecords(tenantId, contractId,
                LegalContext.text(status), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, mapper.countSignRecords(tenantId, contractId, LegalContext.text(status)),
                LegalContext.page(page), limit);
    }

    public Map<String, Object> signCallback(Map<String, Object> body) {
        long tenantId = LegalContext.tenantId();
        String providerCode = LegalContext.requireText(LegalContext.string(body.get("provider_code")));
        String providerTaskId = LegalContext.requireText(LegalContext.string(body.get("provider_task_id")));
        String docHash = LegalContext.string(body.get("doc_hash"));
        String signature = LegalContext.string(body.get("signature"));
        if (signature != null && !signature.equals(hashChain.sha256(providerCode, providerTaskId, docHash))) {
            audit.high("sign.callback.invalid", "SIGN_RECORD", providerTaskId);
            throw new BizException(LegalErrorCode.EVIDENCE_CHAIN_INVALID, "回调验签失败");
        }
        Map<String, Object> existing = mapper.findSignRecordByProviderTask(tenantId, providerCode, providerTaskId);
        if (existing != null) {
            return Map.of("sign_record_id", Rows.lngVal(existing, "id"),
                    "status", Rows.str(existing, "status"), "duplicated", true);
        }
        return Map.of("provider_task_id", providerTaskId, "duplicated", false, "accepted", true);
    }

    // ---------- 存证 ----------
    public PageResult<Map<String, Object>> listEvidence(String permissions, Integer page, Integer size,
                                                        Long contractId, String evidenceType) {
        PermissionGuard.require(permissions, "legal:evidence:view");
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = mapper.listEvidences(tenantId, contractId,
                LegalContext.text(evidenceType), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, mapper.countEvidences(tenantId, contractId, LegalContext.text(evidenceType)),
                LegalContext.page(page), limit);
    }

    public Map<String, Object> verifyEvidence(String permissions, long evidenceId) {
        PermissionGuard.require(permissions, "legal:evidence:verify");
        long tenantId = LegalContext.tenantId();
        Map<String, Object> evidence = mapper.findEvidence(tenantId, evidenceId);
        if (evidence == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean verified = Rows.str(evidence, "block_hash") != null && !Rows.str(evidence, "block_hash").isBlank();
        mapper.updateEvidenceVerified(tenantId, evidenceId, verified, LegalContext.userId());
        audit.low("evidence.verify", "EVIDENCE", evidenceId);
        return Map.of("evidence_id", evidenceId, "verified", verified,
                "block_hash", Rows.str(evidence, "block_hash"));
    }

    public Map<String, Object> verifyEvidenceChain(String permissions, Long contractId) {
        PermissionGuard.require(permissions, "legal:evidence:verify");
        long tenantId = LegalContext.tenantId();
        List<Map<String, Object>> chain = mapper.listEvidenceChain(tenantId, contractId);
        boolean valid = true;
        String lastHash = null;
        String chainId = null;
        for (Map<String, Object> row : chain) {
            String rowChain = Rows.str(row, "chain_id");
            if (chainId != null && !chainId.equals(rowChain)) {
                lastHash = null;
            }
            if (lastHash != null && !lastHash.equals(Rows.str(row, "prev_hash"))) {
                valid = false;
                break;
            }
            lastHash = Rows.str(row, "block_hash");
            chainId = rowChain;
        }
        if (!valid) {
            audit.high("evidence.chain.invalid", "CONTRACT", contractId);
            throw new BizException(LegalErrorCode.EVIDENCE_CHAIN_INVALID);
        }
        return Map.of("contract_id", contractId, "chain_length", chain.size(), "valid", true);
    }

    private void preserveEvidence(long tenantId, long userId, long contractId, long signRecordId, String docHash) {
        String chainId = "EVD-" + contractId;
        String prev = null;
        for (String type : List.of("SIGN_DATA", "TIMESTAMP")) {
            long evidenceId = IdWorker.getId();
            String evidenceNo = "EVD" + Year.now().getValue()
                    + String.format("%06d", commonsMapper.nextEvidenceNo() % 1_000_000);
            String blockHash = hashChain.block(String.valueOf(tenantId), "legal_evidence",
                    String.valueOf(evidenceId), docHash + "|" + type, prev);
            mapper.insertEvidence(evidenceId, tenantId, evidenceNo, contractId, signRecordId, type, docHash,
                    "SHA256", "worm://legal/" + evidenceNo, null, null, chainId, prev, blockHash, userId);
            prev = blockHash;
        }
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
}
