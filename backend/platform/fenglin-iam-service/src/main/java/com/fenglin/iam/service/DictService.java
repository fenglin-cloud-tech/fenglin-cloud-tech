package com.fenglin.iam.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.iam.mapper.DictMapper;
import com.fenglin.iam.web.dto.DictItemView;
import com.fenglin.iam.web.dto.DictTypeView;
import com.fenglin.iam.web.dto.UpsertDictItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 字典管理：平台字典项（tenant_id=0）只读继承，租户可按 item_value 覆盖自定义。
 */
@Service
public class DictService {

    private final DictMapper dictMapper;
    private final AuditSink auditSink;

    public DictService(DictMapper dictMapper, AuditSink auditSink) {
        this.dictMapper = dictMapper;
        this.auditSink = auditSink;
    }

    @Transactional(readOnly = true)
    public List<DictTypeView> listTypes(String scope) {
        return dictMapper.listTypes(normalize(scope)).stream()
                .map(row -> new DictTypeView(
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("scope"),
                        ((Number) row.get("status")).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DictItemView> listItems(String dictCode) {
        long tenantId = TenantContext.requireTenantId();
        requireType(dictCode);
        Map<String, DictItemView> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : dictMapper.listItems(tenantId, dictCode)) {
            boolean tenantOwned = ((Number) row.get("tenant_id")).longValue() == tenantId;
            String itemValue = (String) row.get("item_value");
            DictItemView existing = merged.get(itemValue);
            if (existing == null || tenantOwned) {
                merged.put(itemValue, new DictItemView(
                        itemValue,
                        (String) row.get("item_label"),
                        ((Number) row.get("sort")).intValue(),
                        ((Number) row.get("status")).intValue(),
                        tenantOwned));
            }
        }
        return List.copyOf(merged.values());
    }

    @Transactional
    public DictItemView upsertItem(String dictCode, String itemValue, UpsertDictItemRequest request) {
        long tenantId = TenantContext.requireTenantId();
        requireType(dictCode);
        String normalizedValue = normalize(itemValue);
        if (normalizedValue == null || normalizedValue.length() > 64) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "字典项值不合法");
        }
        int sort = request.sort() == null ? 0 : request.sort();
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        dictMapper.upsertItem(IdWorker.getId(), tenantId, dictCode, normalizedValue,
                request.itemLabel().trim(), sort, operatorId);
        audit("IAM_DICT_ITEM_SAVED", dictCode + ":" + normalizedValue, operatorId);
        return new DictItemView(normalizedValue, request.itemLabel().trim(), sort, 1, true);
    }

    @Transactional
    public void deleteItem(String dictCode, String itemValue) {
        long tenantId = TenantContext.requireTenantId();
        requireType(dictCode);
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        if (dictMapper.deleteItem(tenantId, dictCode, normalize(itemValue), operatorId) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典项不存在或为平台默认项: " + itemValue);
        }
        audit("IAM_DICT_ITEM_DELETED", dictCode + ":" + itemValue, operatorId);
    }

    private void requireType(String dictCode) {
        if (normalize(dictCode) == null || dictMapper.countType(dictCode) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "字典类型不存在: " + dictCode);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String resourceId, Long operatorId) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "iam",
                eventType,
                "MEDIUM",
                snapshot == null ? null : snapshot.tenantId(),
                operatorId,
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                "DICT",
                resourceId,
                Instant.now()));
    }
}
