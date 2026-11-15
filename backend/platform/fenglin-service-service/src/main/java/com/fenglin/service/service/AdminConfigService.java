package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 管理端配置：工单类型、处理组、值班表、自动分派、资产提醒、RPA 重试策略等。
 *
 * <p>文档未单列管理端配置表，按总纲兜底落 svc_tenant_config（config_type + config_key）。</p>
 */
@Service
@Transactional
public class AdminConfigService {

    private final ConfigMapper configMapper;
    private final ServiceAudit audit;

    public AdminConfigService(ConfigMapper configMapper, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.audit = audit;
    }

    /**
     * 保存配置：config_json 既可以是对象也可以是数组（页面默认提交 {@code []}），整段覆盖写入。
     */
    public Map<String, Object> save(String configType, String configKey, Object body) {
        long tenantId = ServiceContext.tenantId();
        String key = configKey == null || configKey.isBlank() ? "default" : configKey;
        configMapper.upsertConfig(IdWorker.getId(), tenantId, configType, key,
                ServiceMaps.json(body == null ? Map.of() : body), "ACTIVE", ServiceContext.userId());
        audit.high("config.save", "tenant_config", configType + ":" + key);
        return ServiceMaps.of("config_type", configType, "config_key", key, "saved", true);
    }

    public List<Map<String, Object>> list(String configType) {
        return ServiceMaps.parseAll(configMapper.listConfigs(ServiceContext.tenantId(),
                ServiceContext.text(configType)), "config_json");
    }

    /**
     * 删除单个配置键（软删），删除后该类型回落到内置默认值。
     */
    public Map<String, Object> delete(String configType, String configKey) {
        String key = configKey == null || configKey.isBlank() ? "default" : configKey;
        int removed = configMapper.deleteConfig(ServiceContext.tenantId(), configType, key,
                ServiceContext.userId());
        if (removed == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        audit.high("config.delete", "tenant_config", configType + ":" + key);
        return ServiceMaps.of("config_type", configType, "config_key", key, "deleted", true);
    }
}
