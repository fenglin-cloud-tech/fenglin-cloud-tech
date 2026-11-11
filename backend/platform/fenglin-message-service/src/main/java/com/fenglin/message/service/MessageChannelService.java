package com.fenglin.message.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.message.mapper.MessageChannelMapper;
import com.fenglin.message.web.dto.ChannelDtos.ChannelRequest;
import com.fenglin.message.web.dto.ChannelDtos.ChannelView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PLAT-02 消息通道管理。
 */
@Service
public class MessageChannelService {

    private static final Set<String> TYPES = Set.of("INAPP", "SMS", "EMAIL", "WECOM", "DINGTALK", "WEBHOOK");

    private final MessageChannelMapper channelMapper;
    private final ObjectMapper objectMapper;

    public MessageChannelService(MessageChannelMapper channelMapper, ObjectMapper objectMapper) {
        this.channelMapper = channelMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ChannelView> list() {
        long tenantId = TenantContext.requireTenantId();
        return channelMapper.list(tenantId).stream().map(this::toView).toList();
    }

    @Transactional
    public ChannelView save(ChannelRequest request) {
        long tenantId = TenantContext.requireTenantId();
        long operatorId = operatorId();
        if (request == null || isBlank(request.channelType()) || isBlank(request.name())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "通道类型与名称不能为空");
        }
        String channelType = request.channelType().trim().toUpperCase();
        if (!TYPES.contains(channelType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "通道类型必须为 INAPP/SMS/EMAIL/WECOM/DINGTALK/WEBHOOK");
        }
        String name = request.name().trim();
        String config = writeConfig(request.config());
        int enabled = Boolean.FALSE.equals(request.enabled()) ? 0 : 1;
        int isDefault = Boolean.TRUE.equals(request.isDefault()) ? 1 : 0;
        Long excludeId = request.id();
        if (channelMapper.findDuplicate(tenantId, channelType, name, excludeId) != null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "同类型下已存在同名通道");
        }
        if (request.id() == null) {
            long id = IdWorker.getId();
            if (isDefault == 1) {
                channelMapper.clearDefault(tenantId, channelType, id, operatorId);
            }
            channelMapper.insert(id, tenantId, channelType, name, config, enabled, isDefault,
                    request.remark(), operatorId);
            return toView(channelMapper.find(tenantId, id));
        }
        if (isDefault == 1) {
            channelMapper.clearDefault(tenantId, channelType, request.id(), operatorId);
        }
        int updated = channelMapper.update(tenantId, request.id(), channelType, name, config, enabled, isDefault,
                request.remark(), operatorId);
        if (updated == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "通道不存在");
        }
        return toView(channelMapper.find(tenantId, request.id()));
    }

    @Transactional
    public ChannelView toggle(long id, boolean enabled) {
        long tenantId = TenantContext.requireTenantId();
        if (channelMapper.toggle(tenantId, id, enabled ? 1 : 0, operatorId()) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "通道不存在");
        }
        return toView(channelMapper.find(tenantId, id));
    }

    @Transactional
    public void delete(long id) {
        long tenantId = TenantContext.requireTenantId();
        if (channelMapper.softDelete(tenantId, id, operatorId()) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "通道不存在");
        }
    }

    private String writeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Map.of() : config);
        } catch (JsonProcessingException e) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "通道配置必须是合法的 JSON 对象");
        }
    }

    private ChannelView toView(Map<String, Object> row) {
        return new ChannelView(((Number) row.get("id")).longValue(), (String) row.get("channel_type"),
                (String) row.get("name"), (String) row.get("config"),
                ((Number) row.get("enabled")).intValue() == 1, ((Number) row.get("is_default")).intValue() == 1,
                (String) row.get("remark"));
    }

    private long operatorId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        return snapshot == null || snapshot.userId() == null ? 0L : snapshot.userId();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
