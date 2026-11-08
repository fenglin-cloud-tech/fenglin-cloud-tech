package com.fenglin.message.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.message.mapper.MessageTemplateMapper;
import com.fenglin.message.web.dto.TemplateRequest;
import com.fenglin.message.web.dto.TemplateView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MessageTemplateService {

    private final MessageTemplateMapper templateMapper;

    public MessageTemplateService(MessageTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        long tenantId = TenantContext.requireTenantId();
        return templateMapper.list(tenantId).stream()
                .map(row -> new TemplateView(
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("channel"),
                        (String) row.get("title_tpl"),
                        (String) row.get("content_tpl"),
                        (String) row.get("variables"),
                        (String) row.get("locale"),
                        (String) row.get("status")))
                .toList();
    }

    @Transactional
    public TemplateView upsert(TemplateRequest request) {
        long tenantId = TenantContext.requireTenantId();
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        templateMapper.upsert(IdWorker.getId(), tenantId, request.code().trim(), request.name().trim(),
                request.channel().trim(), request.titleTpl(), request.contentTpl(),
                request.variables() == null ? null : String.join(",", request.variables()),
                request.locale() == null || request.locale().isBlank() ? "zh-CN" : request.locale(),
                request.status() == null || request.status().isBlank() ? "ENABLED" : request.status(),
                operatorId);
        Map<String, Object> row = templateMapper.find(tenantId, request.code().trim());
        if (row == null) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "模板写入失败: " + request.code());
        }
        return new TemplateView((String) row.get("code"), (String) row.get("name"),
                (String) row.get("channel"), (String) row.get("title_tpl"),
                (String) row.get("content_tpl"), (String) row.get("variables"),
                (String) row.get("locale"), (String) row.get("status"));
    }
}
