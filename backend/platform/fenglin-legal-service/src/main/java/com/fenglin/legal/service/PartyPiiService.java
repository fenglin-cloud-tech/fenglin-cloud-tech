package com.fenglin.legal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.mapper.ContractMapper;
import com.fenglin.legal.support.LegalAudit;
import com.fenglin.legal.support.LegalContext;
import com.fenglin.legal.support.LegalCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LEG-03 相对方手机号明文查看：申请-审批-放行，放行后解密并写审计。
 */
@Service
public class PartyPiiService {

    private static final String PII_APPLY = "legal:party:pii:apply";
    private static final String PII_APPROVE = "legal:party:pii:approve";
    private static final String PII_VIEW = "legal:party:pii:view";

    private final ContractMapper contractMapper;
    private final LegalCrypto crypto;
    private final LegalAudit audit;

    public PartyPiiService(ContractMapper contractMapper, LegalCrypto crypto, LegalAudit audit) {
        this.contractMapper = contractMapper;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> apply(String permissions, long partyId, Map<String, Object> body) {
        PermissionGuard.require(permissions, PII_APPLY);
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> party = contractMapper.findPartyPii(tenantId, partyId);
        if (party == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (party.get("contact_mobile_enc") == null) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "该相对方未登记联系人手机号");
        }
        String reason = LegalContext.requireText(LegalContext.string(body == null ? null : body.get("reason")));
        if (reason.length() > 500) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "申请理由不能超过 500 字");
        }
        if (contractMapper.findPendingMobileRequest(tenantId, partyId, userId) != null) {
            throw new BizException(LegalErrorCode.PII_REQUEST_EXISTS);
        }
        long id = IdWorker.getId();
        contractMapper.insertMobileRequest(id, tenantId, partyId, reason, userId);
        audit.high("party.pii.apply", "CONTRACT_PARTY", partyId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("party_id", partyId);
        result.put("status", "PENDING");
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> myRequests(Integer page, Integer size, String status) {
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = contractMapper.listMyMobileRequests(tenantId, userId,
                LegalContext.text(status), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, contractMapper.countMyMobileRequests(tenantId, userId, LegalContext.text(status)),
                LegalContext.page(page), limit);
    }

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> reviewList(String permissions, Integer page, Integer size,
                                                      String status) {
        PermissionGuard.require(permissions, PII_APPROVE);
        long tenantId = LegalContext.tenantId();
        int limit = LegalContext.size(size);
        List<Map<String, Object>> rows = contractMapper.listMobileRequestsForReview(tenantId,
                LegalContext.text(status), LegalContext.offset(page, size), limit);
        return PageResult.of(rows, contractMapper.countMobileRequestsForReview(tenantId, LegalContext.text(status)),
                LegalContext.page(page), limit);
    }

    @Transactional
    public Map<String, Object> review(String permissions, long id, Map<String, Object> body) {
        PermissionGuard.require(permissions, PII_APPROVE);
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> request = contractMapper.findMobileRequest(tenantId, id);
        if (request == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PENDING".equals(String.valueOf(request.get("status")))) {
            throw new BizException(LegalErrorCode.PII_REQUEST_DECIDED);
        }
        if (String.valueOf(request.get("applied_by")).equals(String.valueOf(userId))) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "不能审批本人提交的申请");
        }
        String approveText = LegalContext.string(body == null ? null : body.get("approve"));
        boolean approve = "true".equalsIgnoreCase(approveText) || "APPROVED".equalsIgnoreCase(approveText);
        String comment = LegalContext.string(body == null ? null : body.get("comment"));
        if (!approve && comment == null) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "驳回时必须填写审批意见");
        }
        String status = approve ? "APPROVED" : "REJECTED";
        int updated = contractMapper.decideMobileRequest(tenantId, id, status, userId, comment);
        if (updated == 0) {
            throw new BizException(LegalErrorCode.PII_REQUEST_DECIDED);
        }
        audit.high(approve ? "party.pii.approve" : "party.pii.reject", "CONTRACT_PARTY",
                request.get("party_id"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        return result;
    }

    @Transactional
    public Map<String, Object> reveal(String permissions, long partyId) {
        PermissionGuard.require(permissions, PII_VIEW);
        long tenantId = LegalContext.tenantId();
        long userId = LegalContext.userId();
        Map<String, Object> party = contractMapper.findPartyPii(tenantId, partyId);
        if (party == null) {
            throw new BizException(LegalErrorCode.RESOURCE_NOT_FOUND);
        }
        if (contractMapper.findApprovedMobileRequest(tenantId, partyId, userId) == null) {
            throw new BizException(LegalErrorCode.PII_NOT_APPROVED);
        }
        String mobile = crypto.decrypt(tenantId, "legal_contract_party", "contact_mobile",
                party.get("contact_mobile_enc") == null ? null : String.valueOf(party.get("contact_mobile_enc")));
        audit.high("party.pii.view", "CONTRACT_PARTY", partyId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", partyId);
        result.put("contact_mobile", mobile);
        return result;
    }
}
