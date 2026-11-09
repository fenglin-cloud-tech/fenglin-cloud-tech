package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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

    public Map<String, Object> save(String configType, String configKey, Map<String, Object> body) {
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
}
