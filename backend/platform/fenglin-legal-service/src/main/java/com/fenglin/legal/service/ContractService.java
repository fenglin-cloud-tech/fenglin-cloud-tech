package com.fenglin.legal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.client.WorkflowClient;
import com.fenglin.legal.mapper.ContractMapper;
import com.fenglin.legal.mapper.SealSignMapper;
import com.fenglin.legal.support.HashChain;
import com.fenglin.legal.support.LegalAudit;
import com.fenglin.legal.support.LegalContext;
import com.fenglin.legal.support.LegalCrypto;
import com.fenglin.legal.support.LegalIdempotency;
import com.fenglin.legal.support.LegalMaps;
import com.fenglin.legal.support.Rows;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G1 合同域服务：模板/变量、起草/版本/条款、审批、归档、终止、完整性校验、相对方、履约、到期提醒。
 */
@Service
@Transactional
public class ContractService {

    private final ContractMapper contractMapper;
    private final SealSignMapper sealSignMapper;
    private final LegalCrypto crypto;
    private final LegalAudit audit;
    private final LegalIdempotency idempotency;
    private final HashChain hashChain;
    private final WorkflowClient workflowClient;
    private final com.fenglin.legal.mapper.CommonsMapper commons;

    public ContractService(ContractMapper contractMapper, SealSignMapper sealSignMapper, LegalCrypto crypto,
                           LegalAudit audit, LegalIdempotency idempotency, HashChain hashChain,
                           WorkflowClient workflowClient, com.fenglin.legal.mapper.CommonsMapper commons) {
        this.contractMapper = contractMapper;
        this.sealSignMapper = sealSignMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.idempotency = idempotency;
        this.hashChain = hashChain;
        this.workflowClient = workflowClient;
        this.commons = commons;
    }

    // ---------- 模板 ----------
    public Map<String, Object> createTemplate(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:template:manage");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String name = LegalContext.requireText(LegalContext.string(body.get("name")));
        String category = LegalContext.requireText(LegalContext.string(body.get("category")));
        long id = IdWorker.getId();
        String templateNo = "TPL" + Year.now().getValue()
                + String.format("%06d", commons.nextTemplateNo() % 1_000_000);
        contractMapper.insertTemplate(id, tenantId, templateNo, name, category,
                LegalContext.longValue(body.get("owner_id")), defaultIfNull(LegalContext.string(body.get("visible_scope")), "TENANT"),
                LegalMaps.json(body.get("applied_org_ids")), userId);
        audit.low("template.create", "CONTRACT_TEMPLATE", id);
        return LegalMaps.parse(contractMapper.findTemplate(tenantId, id), "applied_org_ids");
    }

    public PageResult<Map<String, Object>> listTemplates(Integer page, Integer size, String category, String status) {
        long tenantId = LegalContext.tenantId();
        int offset = LegalContext.offset(page, size);
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = LegalMaps.parseAll(
                contractMapper.listTemplates(tenantId, LegalContext.text(category), LegalContext.text(status), offset, limit),
                "applied_org_ids");
        return PageResult.of(rows, contractMapper.countTemplates(tenantId, LegalContext.text(category),
                LegalContext.text(status)), LegalContext.page(page), limit);
    }

    public Map<String, Object> publishTemplate(String permissions, long templateId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:template:version");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> template = contractMapper.findTemplate(tenantId, templateId);
        if (template == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String contentRef = LegalContext.text(LegalContext.string(body.get("content_ref")));
        String contentHash = LegalContext.text(LegalContext.string(body.get("content_hash")));
        if (contentHash == null) {
            contentHash = hashChain.sha256(String.valueOf(templateId), contentRef, String.valueOf(body.get("content")));
        }
        int versionNo = contractMapper.nextTemplateVersionNo(tenantId, templateId);
        long versionId = IdWorker.getId();
        contractMapper.insertTemplateVersion(versionId, tenantId, templateId, versionNo, contentRef, contentHash,
                LegalContext.string(body.get("change_summary")), userId);
        contractMapper.publishTemplateVersion(tenantId, versionId, userId);
        contractMapper.touchTemplatePublished(tenantId, templateId, versionId, userId);
        audit.low("template.publish", "CONTRACT_TEMPLATE", templateId);
        return LegalMaps.parse(contractMapper.findTemplateVersion(tenantId, versionId));
    }

    public List<Map<String, Object>> saveVariables(String permissions, long templateId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:template:variable");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Long versionId = LegalContext.longValue(body.get("version_id"));
        if (versionId == null) {
            Map<String, Object> published = contractMapper.findPublishedTemplateVersion(tenantId, templateId);
            if (published == null) {
                throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
            }
            versionId = Rows.lng(published, "id");
        }
        contractMapper.deleteTemplateVariables(tenantId, versionId);
        List<?> variables = body.get("variables") instanceof List<?> list ? list : List.of();
        int sort = 0;
        for (Object item : variables) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> variable = (Map<String, Object>) raw;
            contractMapper.insertTemplateVariable(IdWorker.getId(), tenantId, versionId,
                    LegalContext.requireText(LegalContext.string(variable.get("var_key"))),
                    LegalContext.requireText(LegalContext.string(variable.get("var_label"))),
                    defaultIfNull(LegalContext.string(variable.get("data_type")), "TEXT"),
                    Boolean.TRUE.equals(variable.get("required")),
                    LegalContext.string(variable.get("default_value")),
                    LegalContext.string(variable.get("value_source")),
                    LegalMaps.json(variable.get("options")), sort++, userId);
        }
        audit.low("template.variables", "TEMPLATE_VERSION", versionId);
        return LegalMaps.parseAll(contractMapper.listTemplateVariables(tenantId, versionId), "options");
    }

    // ---------- 合同 ----------
    public Map<String, Object> createContract(String permissions, Map<String, Object> body, String idemHeader) {
        PermissionGuard.require(permissions, "legal:contract:create");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = LegalContext.idempotencyKey(idemHeader);
        Long existing = idempotency.find(key, "/api/legal/v1/contracts");
        if (existing != null) {
            return contractDetail(tenantId, existing);
        }
        String name = LegalContext.requireText(LegalContext.string(body.get("name")));
        String category = LegalContext.requireText(LegalContext.string(body.get("category")));
        Long templateId = LegalContext.longValue(body.get("template_id"));
        Long templateVersionId = LegalContext.longValue(body.get("template_version_id"));
        if (templateId != null && templateVersionId == null) {
            Map<String, Object> published = contractMapper.findPublishedTemplateVersion(tenantId, templateId);
            if (published != null) {
                templateVersionId = Rows.lng(published, "id");
            }
        }
        JsonNode variables = LegalMaps.node(body.get("variable_values"));
        validateVariables(tenantId, templateVersionId, variables);

        long contractId = IdWorker.getId();
        long versionId = IdWorker.getId();
        String contractNo = "CT" + Year.now().getValue()
                + String.format("%06d", commons.nextContractNo() % 1_000_000);
        BigDecimal amount = decimal(body.get("amount"));
        Long ownerId = LegalContext.longValue(body.get("owner_id"));
        if (!PermissionGuard.has(permissions, "legal:contract:view")) {
            // 无全量查看权限的员工只能创建归属于自己的合同，避免出现创建后本人列表不可见的记录。
            ownerId = userId;
        }
        String contentRef = LegalContext.string(body.get("content_ref"));
        String contentHash = hashChain.sha256(contractNo, name, String.valueOf(amount), String.valueOf(variables));
        contractMapper.insertContract(contractId, tenantId, contractNo, name, category, templateId,
                templateVersionId, versionId, LegalContext.longValue(body.get("party_a_id")),
                LegalContext.longValue(body.get("party_b_id")), amount,
                defaultIfNull(LegalContext.string(body.get("currency")), "CNY"),
                date(body.get("start_date")), date(body.get("end_date")),
                ownerId, LegalContext.longValue(body.get("org_id")),
                defaultIfNull(LegalContext.string(body.get("risk_level")), "LOW"), userId);
        contractMapper.insertContractVersion(versionId, tenantId, contractId, 1, contentRef, contentHash,
                LegalMaps.json(variables), "起草创建", "DRAFT", userId);
        idempotency.remember(key, "/api/legal/v1/contracts", contractId);
        audit.low("contract.create", "CONTRACT", contractId);
        return contractDetail(tenantId, contractId);
    }

    public PageResult<Map<String, Object>> listContracts(String permissions, Integer page, Integer size,
                                                         String status, String category,
                                                         Long ownerId, Long orgId, String keyword) {
        boolean fullScope = PermissionGuard.has(permissions, "legal:contract:view");
        if (!fullScope) {
            PermissionGuard.require(permissions, "legal:contract:view:self");
        }
        // 只有本人查看权限时强制按 owner 收敛，忽略前端传入的 owner_id。
        Long scopedOwner = ownerId;
        if (!fullScope) {
            scopedOwner = LegalContext.userId();
        }
        long tenantId = LegalContext.tenantId();
        int offset = LegalContext.offset(page, size);
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = contractMapper.listContracts(tenantId, LegalContext.text(status),
                LegalContext.text(category), scopedOwner, orgId, LegalContext.text(keyword), offset, limit);
        return PageResult.of(rows, contractMapper.countContracts(tenantId, LegalContext.text(status),
                LegalContext.text(category), scopedOwner, orgId, LegalContext.text(keyword)),
                LegalContext.page(page), limit);
    }

    public Map<String, Object> contractDetail(long tenantId, long contractId) {
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(LegalMaps.parse(contract));
        Long currentVersionId = Rows.lng(contract, "current_version_id");
        result.put("versions", contractMapper.listContractVersions(tenantId, contractId));
        if (currentVersionId != null) {
            result.put("clauses", contractMapper.listClauses(tenantId, contractId, currentVersionId));
        } else {
            result.put("clauses", List.of());
        }
        result.put("approvals", contractMapper.listApprovalRecords(tenantId, "CONTRACT", contractId));
        result.put("signatories", sealSignMapper.listSignatories(tenantId, contractId));
        return result;
    }

    public Map<String, Object> contractDetail(String permissions, long contractId) {
        Map<String, Object> result = contractDetail(LegalContext.tenantId(), contractId);
        ensureContractScope(permissions, result);
        return result;
    }

    /**
     * 本人视角（无 legal:contract:view）只能读写 owner_id 为自己的合同。
     */
    private void ensureContractScope(String permissions, Map<String, Object> contract) {
        if (PermissionGuard.has(permissions, "legal:contract:view")) {
            return;
        }
        if (Rows.lngVal(contract, "owner_id") != LegalContext.userId()) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅能访问本人负责的合同");
        }
    }

    public Map<String, Object> updateContract(String permissions, long contractId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:contract:edit");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> contract = requireEditableContract(tenantId, contractId);
        ensureContractScope(permissions, contract);
        contractMapper.updateContract(contractId, tenantId,
                defaultIfNull(LegalContext.string(body.get("name")), Rows.str(contract, "name")),
                defaultIfNull(LegalContext.string(body.get("category")), Rows.str(contract, "category")),
                body.containsKey("amount") ? decimal(body.get("amount")) : Rows.dec(contract, "amount"),
                defaultIfNull(LegalContext.string(body.get("currency")), Rows.str(contract, "currency")),
                body.containsKey("start_date") ? date(body.get("start_date")) : Rows.date(contract, "start_date"),
                body.containsKey("end_date") ? date(body.get("end_date")) : Rows.date(contract, "end_date"),
                body.containsKey("owner_id") ? LegalContext.longValue(body.get("owner_id")) : Rows.lng(contract, "owner_id"),
                body.containsKey("org_id") ? LegalContext.longValue(body.get("org_id")) : Rows.lng(contract, "org_id"),
                defaultIfNull(LegalContext.string(body.get("risk_level")), Rows.str(contract, "risk_level")),
                body.containsKey("remark") ? LegalContext.string(body.get("remark")) : Rows.str(contract, "remark"),
                body.containsKey("sign_date") ? date(body.get("sign_date")) : Rows.date(contract, "sign_date"), userId);
        audit.low("contract.update", "CONTRACT", contractId);
        return contractDetail(tenantId, contractId);
    }

    public Map<String, Object> deleteDraft(String permissions, long contractId) {
        PermissionGuard.require(permissions, "legal:contract:edit");
        long tenantId = LegalContext.tenantId();
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"DRAFT".equals(Rows.str(contract, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID, "仅草稿合同允许删除");
        }
        ensureContractScope(permissions, contract);
        contractMapper.softDeleteDraft(tenantId, contractId, LegalContext.userId());
        audit.high("contract.delete", "CONTRACT", contractId);
        return Map.of("id", contractId, "deleted", true);
    }

    public Map<String, Object> saveVersion(String permissions, long contractId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:contract:edit");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> contract = requireEditableContract(tenantId, contractId);
        ensureContractScope(permissions, contract);
        int versionNo = contractMapper.nextContractVersionNo(tenantId, contractId);
        String contentRef = LegalContext.string(body.get("content_ref"));
        String contentHash = LegalContext.text(LegalContext.string(body.get("content_hash")));
        if (contentHash == null) {
            contentHash = hashChain.sha256(String.valueOf(contractId), String.valueOf(versionNo), contentRef,
                    String.valueOf(body.get("content")));
        }
        long versionId = IdWorker.getId();
        contractMapper.insertContractVersion(versionId, tenantId, contractId, versionNo, contentRef, contentHash,
                LegalMaps.json(body.get("variable_values")), LegalContext.string(body.get("change_summary")),
                LegalContext.string(body.get("stage")) == null ? "DRAFT" : LegalContext.string(body.get("stage")), userId);
        contractMapper.updateCurrentVersion(tenantId, contractId, versionId, userId);
        audit.low("contract.version", "CONTRACT", contractId);
        return LegalMaps.parse(contractMapper.findContract(tenantId, contractId));
    }

    public List<Map<String, Object>> saveClauses(String permissions, long contractId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:contract:edit");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> contract = requireEditableContract(tenantId, contractId);
        ensureContractScope(permissions, contract);
        Long versionId = LegalContext.longValue(body.get("version_id"));
        if (versionId == null) {
            versionId = Rows.lng(contract, "current_version_id");
        }
        if (versionId == null) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "合同尚无正文版本");
        }
        List<?> clauses = body.get("clauses") instanceof List<?> list ? list : List.of();
        int sort = 0;
        for (Object item : clauses) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> clause = (Map<String, Object>) raw;
            String clauseNo = LegalContext.requireText(LegalContext.string(clause.get("clause_no")));
            boolean mandatory = Boolean.TRUE.equals(clause.get("is_mandatory"));
            int updated = contractMapper.updateClause(tenantId, versionId, clauseNo,
                    LegalContext.string(clause.get("title")), LegalContext.string(clause.get("content")),
                    LegalContext.string(clause.get("risk_tag")), mandatory, userId);
            if (updated == 0) {
                contractMapper.insertClause(IdWorker.getId(), tenantId, contractId, versionId, clauseNo,
                        LegalContext.string(clause.get("title")), LegalContext.string(clause.get("content")),
                        mandatory, LegalContext.string(clause.get("risk_tag")), sort, userId);
            }
            sort++;
        }
        audit.low("contract.clauses", "CONTRACT", contractId);
        return contractMapper.listClauses(tenantId, contractId, versionId);
    }

    public Map<String, Object> submit(String permissions, long contractId, Map<String, Object> body,
                                      String idemHeader, String requestId) {
        PermissionGuard.require(permissions, "legal:contract:submit");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/contracts/{id}/submit");
        Long existing = idempotency.find(key, "/api/legal/v1/contracts/{id}/submit");
        if (existing != null) {
            return contractDetail(tenantId, existing);
        }
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        ensureContractScope(permissions, contract);
        String status = Rows.str(contract, "status");
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status)) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        Long versionId = Rows.lng(contract, "current_version_id");
        Map<String, Object> version = versionId == null ? null : contractMapper.findLatestContractVersion(tenantId, contractId);
        if (version != null) {
            Object submittedValues = body.get("variable_values");
            JsonNode values = submittedValues == null
                    ? LegalMaps.node(Rows.str(version, "variable_values"))
                    : LegalMaps.node(submittedValues);
            validateVariables(tenantId, versionId, values);
        }
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("contract_id", contractId);
        form.put("contract_no", Rows.str(contract, "contract_no"));
        form.put("amount", Rows.dec(contract, "amount"));
        form.put("category", Rows.str(contract, "category"));
        form.put("risk_level", Rows.str(contract, "risk_level"));
        String instanceNo = workflowClient.startApproval(permissions, requestId, "legalContractApproval",
                Rows.str(contract, "contract_no"), "CONTRACT", "合同审批：" + Rows.str(contract, "name"), form);
        contractMapper.updateContractState(tenantId, contractId, "APPROVING", null, null, null, userId);
        idempotency.remember(key, "/api/legal/v1/contracts/{id}/submit", contractId);
        audit.low("contract.submit", "CONTRACT", contractId);
        Map<String, Object> result = contractDetail(tenantId, contractId);
        result.put("process_instance_no", instanceNo);
        return result;
    }

    public Map<String, Object> approve(String permissions, long contractId, Map<String, Object> body,
                                       String idemHeader) {
        PermissionGuard.require(permissions, "legal:contract:approve");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/contracts/{id}/approve");
        Long existing = idempotency.find(key, "/api/legal/v1/contracts/{id}/approve");
        if (existing != null) {
            return contractDetail(tenantId, existing);
        }
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"APPROVING".equals(Rows.str(contract, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        String action = defaultIfNull(LegalContext.string(body.get("action")), "APPROVE");
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
        Long versionId = Rows.lng(contract, "current_version_id");
        contractMapper.insertApprovalRecord(IdWorker.getId(), tenantId, "CONTRACT", contractId,
                LegalContext.string(body.get("process_instance_id")), LegalContext.string(body.get("task_id")),
                LegalContext.string(body.get("node_name")), userId, action,
                LegalContext.string(body.get("comment")), versionId, userId);
        contractMapper.updateContractState(tenantId, contractId,
                "APPROVE".equals(action) ? "APPROVED" : "DRAFT", null, null, null, userId);
        idempotency.remember(key, "/api/legal/v1/contracts/{id}/approve", contractId);
        audit.high("contract.approve", "CONTRACT", contractId);
        return contractDetail(tenantId, contractId);
    }

    public Map<String, Object> archive(String permissions, long contractId, String idemHeader) {
        PermissionGuard.require(permissions, "legal:contract:archive");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/contracts/{id}/archive");
        Long existing = idempotency.find(key, "/api/legal/v1/contracts/{id}/archive");
        if (existing != null) {
            return contractDetail(tenantId, existing);
        }
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        String status = Rows.str(contract, "status");
        if (!List.of("SIGNED", "EFFECTIVE", "EXPIRED", "TERMINATED", "SIGNING").contains(status)) {
            throw new BizException(LegalErrorCode.CONTRACT_STATE_INVALID);
        }
        Map<String, Object> latest = contractMapper.findLatestContractVersion(tenantId, contractId);
        // 归档哈希以固定标记 "ARCHIVED" 计算，避免校验时因状态已变更导致重算不一致
        String archiveHash = hashChain.sha256(Rows.str(contract, "contract_no"), "ARCHIVED",
                latest == null ? "" : Rows.str(latest, "content_hash"), String.valueOf(contractId));
        contractMapper.updateContractState(tenantId, contractId, "ARCHIVED", null, null, archiveHash, userId);
        idempotency.remember(key, "/api/legal/v1/contracts/{id}/archive", contractId);
        audit.high("contract.archive", "CONTRACT", contractId);
        return contractDetail(tenantId, contractId);
    }

    public Map<String, Object> terminate(String permissions, long contractId, Map<String, Object> body,
                                         String idemHeader) {
        PermissionGuard.require(permissions, "legal:contract:terminate");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String key = idempotency.require(idemHeader, "/api/legal/v1/contracts/{id}/terminate");
        Long existing = idempotency.find(key, "/api/legal/v1/contracts/{id}/terminate");
        if (existing != null) {
            return contractDetail(tenantId, existing);
        }
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("ARCHIVED".equals(Rows.str(contract, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_ARCHIVED);
        }
        contractMapper.updateContractState(tenantId, contractId, "TERMINATED", null, null, null, userId);
        idempotency.remember(key, "/api/legal/v1/contracts/{id}/terminate", contractId);
        audit.high("contract.terminate", "CONTRACT", contractId);
        return contractDetail(tenantId, contractId);
    }

    public Map<String, Object> verifyIntegrity(String permissions, long contractId) {
        PermissionGuard.require(permissions, "legal:contract:integrity");
        long tenantId = LegalContext.tenantId();
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> latest = contractMapper.findLatestContractVersion(tenantId, contractId);
        boolean versionMatched = latest == null || Rows.str(latest, "content_hash") != null
                && !Rows.str(latest, "content_hash").isBlank();
        if (!versionMatched) {
            audit.high("contract.integrity.failed", "CONTRACT", contractId);
            throw new BizException(LegalErrorCode.HASH_VERIFY_FAILED);
        }
        String archiveHash = Rows.str(contract, "archive_hash");
        boolean archivedMatched = true;
        if (archiveHash != null) {
            String expected = hashChain.sha256(Rows.str(contract, "contract_no"), "ARCHIVED",
                    latest == null ? "" : Rows.str(latest, "content_hash"), String.valueOf(contractId));
            archivedMatched = expected.equals(archiveHash);
            if (!archivedMatched) {
                audit.high("contract.integrity.failed", "CONTRACT", contractId);
                throw new BizException(LegalErrorCode.HASH_VERIFY_FAILED);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract_id", contractId);
        result.put("status", Rows.str(contract, "status"));
        result.put("archive_hash", archiveHash);
        result.put("valid", versionMatched && archivedMatched);
        result.put("version_hash_matched", versionMatched);
        result.put("archived_hash_matched", archivedMatched);
        result.put("checked_at", java.time.Instant.now());
        return result;
    }

    // ---------- 相对方 ----------
    public Map<String, Object> createParty(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:party:manage");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        String name = LegalContext.requireText(LegalContext.string(body.get("name")));
        String uscc = LegalContext.string(body.get("uscc"));
        String usccHash = crypto.hash(tenantId, uscc);
        if (usccHash != null) {
            Map<String, Object> existing = contractMapper.findPartyByUsccHash(tenantId, usccHash);
            if (existing != null) {
                Map<String, Object> result = LegalMaps.parse(existing);
                result.put("duplicated", true);
                return result;
            }
        }
        String mobile = LegalContext.string(body.get("contact_mobile"));
        long id = IdWorker.getId();
        contractMapper.insertParty(id, tenantId, name, uscc, usccHash,
                LegalContext.string(body.get("contact_name")),
                crypto.encrypt(tenantId, "legal_contract_party", "contact_mobile", mobile),
                crypto.hash(tenantId, mobile), crypto.tail(mobile),
                LegalContext.string(body.get("email")), LegalContext.string(body.get("legal_rep")),
                LegalContext.string(body.get("address")),
                defaultIfNull(LegalContext.string(body.get("risk_level")), "LOW"),
                LegalContext.string(body.get("remark")), userId);
        audit.low("party.create", "CONTRACT_PARTY", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("uscc", uscc);
        result.put("contact_mobile_tail", crypto.tail(mobile));
        result.put("duplicated", false);
        return result;
    }

    public PageResult<Map<String, Object>> listParties(Integer page, Integer size, String keyword) {
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = contractMapper.listParties(tenantId, LegalContext.text(keyword),
                LegalContext.offset(page, size), limit);
        return PageResult.of(rows, contractMapper.countParties(tenantId, LegalContext.text(keyword)),
                LegalContext.page(page), limit);
    }

    // ---------- 履约与提醒 ----------
    public Map<String, Object> createPerformanceNode(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:performance:manage");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        long contractId = LegalContext.requireLong(body.get("contract_id"));
        if (contractMapper.findContract(tenantId, contractId) == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        long id = IdWorker.getId();
        contractMapper.insertPerformanceNode(id, tenantId, contractId,
                LegalContext.requireText(LegalContext.string(body.get("node_type"))),
                LegalContext.requireText(LegalContext.string(body.get("name"))), date(body.get("plan_date")),
                decimal(body.get("amount")), LegalContext.longValue(body.get("owner_id")),
                LegalContext.string(body.get("evidence_ref")), LegalContext.string(body.get("remark")), userId);
        audit.low("performance.create", "PERFORMANCE_NODE", id);
        return Map.of("id", id, "contract_id", contractId);
    }

    public PageResult<Map<String, Object>> listPerformanceNodes(Integer page, Integer size, Long contractId,
                                                               String status) {
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = contractMapper.listPerformanceNodes(tenantId, contractId,
                LegalContext.text(status), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, contractMapper.countPerformanceNodes(tenantId, contractId,
                LegalContext.text(status)), LegalContext.page(page), limit);
    }

    public Map<String, Object> updatePerformanceStatus(String permissions, long nodeId, Map<String, Object> body) {
        PermissionGuard.require(permissions, "legal:performance:manage");
        long tenantId = LegalContext.tenantId();
        String status = LegalContext.requireText(LegalContext.string(body.get("status")));
        if (!List.of("PENDING", "IN_PROGRESS", "DONE", "OVERDUE", "CANCELED").contains(status)) {
            throw new BizException(LegalErrorCode.PARAM_INVALID);
        }
        contractMapper.updatePerformanceStatus(tenantId, nodeId, status, LegalContext.userId());
        audit.low("performance.status", "PERFORMANCE_NODE", nodeId);
        return Map.of("id", nodeId, "status", status);
    }

    public Map<String, Object> createReminder(String permissions, Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:legal:reminder:config");
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        long contractId = LegalContext.requireLong(body.get("contract_id"));
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        Long offsetValue = LegalContext.longValue(body.get("offset_days"));
        int offsetDays = offsetValue == null || offsetValue <= 0 ? 30 : offsetValue.intValue();
        LocalDate endDate = Rows.date(contract, "end_date");
        LocalDate triggerDate = endDate == null ? LocalDate.now() : endDate.minusDays(offsetDays);
        long id = IdWorker.getId();
        int inserted = contractMapper.insertReminder(id, tenantId, contractId, offsetDays, triggerDate,
                defaultIfNull(LegalContext.string(body.get("channel")), "INBOX"),
                LegalMaps.json(body.get("receiver_ids")), userId);
        return Map.of("id", id, "inserted", inserted == 1, "trigger_date", triggerDate.toString());
    }

    public PageResult<Map<String, Object>> listReminders(Integer page, Integer size, Long contractId,
                                                         String status) {
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = LegalMaps.parseAll(contractMapper.listReminders(tenantId, contractId,
                LegalContext.text(status), LegalContext.offset(page, size), limit), "receiver_ids");
        return PageResult.of(rows, contractMapper.countReminders(tenantId, contractId,
                LegalContext.text(status)), LegalContext.page(page), limit);
    }

    public Map<String, Object> sendReminder(String permissions, long reminderId) {
        PermissionGuard.require(permissions, "tenant:legal:reminder:config");
        contractMapper.updateReminderStatus(LegalContext.tenantId(), reminderId, "SENT", LegalContext.userId());
        audit.low("reminder.send", "EXPIRY_REMINDER", reminderId);
        return Map.of("id", reminderId, "status", "SENT");
    }

    // ---------- 内部 ----------
    private Map<String, Object> requireEditableContract(long tenantId, long contractId) {
        Map<String, Object> contract = contractMapper.findContract(tenantId, contractId);
        if (contract == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("ARCHIVED".equals(Rows.str(contract, "status"))) {
            throw new BizException(LegalErrorCode.CONTRACT_ARCHIVED);
        }
        return contract;
    }

    private void validateVariables(long tenantId, Long templateVersionId, JsonNode values) {
        if (templateVersionId == null) {
            return;
        }
        List<Map<String, Object>> variables = contractMapper.listTemplateVariables(tenantId, templateVersionId);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> variable : variables) {
            if (!Rows.bool(variable, "required")) {
                continue;
            }
            String key = Rows.str(variable, "var_key");
            JsonNode value = values == null ? null : values.get(key);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(LegalErrorCode.TEMPLATE_VAR_MISSING, "模板变量未填充: " + String.join(",", missing));
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDate date(Object value) {
        String text = LegalContext.string(value);
        return text == null ? null : LocalDate.parse(text);
    }

    private static String defaultIfNull(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
