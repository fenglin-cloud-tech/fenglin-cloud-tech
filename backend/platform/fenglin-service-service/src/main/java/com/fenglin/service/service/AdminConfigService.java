package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.support.ConfigSchemaValidator;
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
    private final ConfigSchemaValidator schemaValidator;

    public AdminConfigService(ConfigMapper configMapper, ServiceAudit audit,
                              ConfigSchemaValidator schemaValidator) {
        this.configMapper = configMapper;
        this.audit = audit;
        this.schemaValidator = schemaValidator;
    }

    /**
     * 保存配置：config_json 既可以是对象也可以是数组（页面默认提交 {@code []}），整段覆盖写入。
     */
    public Map<String, Object> save(String configType, String configKey, Object body) {
        long tenantId = ServiceContext.tenantId();
        String key = configKey == null || configKey.isBlank() ? "default" : configKey;
        List<String> errors = schemaValidator.validate(configType, body);
        if (!errors.isEmpty()) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "配置字段类型不合法：" + String.join("；", errors));
        }
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
     * 对现网存量的 7 类配置做只读 dry-run：逐条按字段骨架校验，不写库。
     * 上线前要求 rejected = 0。
     */
    public Map<String, Object> dryRun() {
        long tenantId = ServiceContext.tenantId();
        List<Map<String, Object>> rows = configMapper.listConfigs(tenantId, null);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        int rejected = 0;
        for (Map<String, Object> row : rows) {
            String type = ServiceContext.string(row.get("config_type"));
            if (type == null || !ConfigSchemaValidator.CONFIG_TYPES.contains(type)) {
                continue;
            }
            Object body = ServiceMaps.read(ServiceContext.string(row.get("config_json")));
            List<String> errors = schemaValidator.validate(type, body);
            if (!errors.isEmpty()) {
                rejected++;
            }
            items.add(ServiceMaps.of("config_type", type, "config_key", row.get("config_key"),
                    "errors", errors));
        }
        return ServiceMaps.of("config_types", ConfigSchemaValidator.CONFIG_TYPES.size(),
                "records", items.size(), "rejected", rejected, "items", items);
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
