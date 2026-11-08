package com.fenglin.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.ai.AiErrorCode;
import com.fenglin.ai.mapper.AiGrantMapper;
import com.fenglin.ai.mapper.AiMeteringMapper;
import com.fenglin.ai.web.dto.AiDtos.GrantItem;
import com.fenglin.ai.web.dto.AiDtos.GrantResult;
import com.fenglin.ai.web.dto.AiDtos.GrantView;
import com.fenglin.ai.web.dto.AiDtos.MeteringRecord;
import com.fenglin.ai.web.dto.AiDtos.MeteringResult;
import com.fenglin.ai.web.dto.AiDtos.ModeValidateResult;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUB-API-1306 能力授权、1307 计量查询、1308 双模式一致性校验。
 * 1308 文档未定义“双模式”判定口径，本实现约定：
 *   宿主产品（host_product != hub）启用的能力必须同时在本租户 hub 侧启用，且额度必须大于 0。
 */
@Service
public class AiGovernanceService {

    private static final Set<String> CAPABILITIES = Set.of("RAG", "TEXT", "AGENT", "SEARCH");
    private static final Set<String> BILLING_MODES = Set.of("TOKEN", "CALL", "SUBSCRIPTION");
    private static final String SELF_HOST = "hub";

    private final AiGrantMapper grantMapper;
    private final AiMeteringMapper meteringMapper;
    private final AuditSink auditSink;

    public AiGovernanceService(AiGrantMapper grantMapper, AiMeteringMapper meteringMapper,
                               AuditSink auditSink) {
        this.grantMapper = grantMapper;
        this.meteringMapper = meteringMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public GrantResult putGrants(List<GrantItem> items, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (items == null || items.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "items 不能为空");
        }
        List<GrantView> views = new ArrayList<>();
        for (GrantItem item : items) {
            if (item == null) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "授权项不能为空");
            }
            String hostProduct = require(item.hostProduct(), "hostProduct");
            String capability = require(item.capability(), "capability").toUpperCase();
            if (!CAPABILITIES.contains(capability)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST,
                        "capability 仅支持 RAG/TEXT/AGENT/SEARCH");
            }
            String billingMode = item.billingMode() == null
                    ? "TOKEN" : item.billingMode().trim().toUpperCase();
            if (!BILLING_MODES.contains(billingMode)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST,
                        "billing_mode 仅支持 TOKEN/CALL/SUBSCRIPTION");
            }
            int quotaQps = item.quotaQps() == null ? 50 : item.quotaQps();
            long quotaTokenMonth = item.quotaTokenMonth() == null ? 0L : item.quotaTokenMonth();
            if (quotaQps < 1 || quotaTokenMonth < 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "quota 取值非法");
            }
            grantMapper.upsert(IdWorker.getId(), tenantId, hostProduct, capability,
                    Boolean.FALSE.equals(item.enabled()) ? 0 : 1, quotaQps, quotaTokenMonth,
                    billingMode, operatorId);
            Map<String, Object> saved = grantMapper.find(tenantId, hostProduct, capability);
            views.add(toView(saved));
        }
        audit(tenantId, operatorId, "AI_GRANT_UPDATED", String.valueOf(items.size()));
        return new GrantResult(views);
    }

    /** 扩展：授权列表（总纲 1306 仅定义写入，列表按管理端展示最小语义实现）。 */
    @Transactional(readOnly = true)
    public GrantResult listGrants(String hostProduct) {
        long tenantId = TenantContext.requireTenantId();
        String filter = hostProduct == null || hostProduct.isBlank() ? null : hostProduct.trim();
        List<GrantView> views = new ArrayList<>();
        for (Map<String, Object> row : grantMapper.listAll(tenantId)) {
            if (filter == null || filter.equals(row.get("host_product"))) {
                views.add(toView(row));
            }
        }
        return new GrantResult(views);
    }

    /** 扩展：授权撤销（软删除），不影响其他能力。 */
    @Transactional
    public void deleteGrant(String hostProduct, String capability, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String product = require(hostProduct, "hostProduct");
        String caps = require(capability, "capability").toUpperCase();
        int affected = grantMapper.softDelete(tenantId, product, caps, operatorId);
        if (affected == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "授权不存在");
        }
        audit(tenantId, operatorId, "AI_GRANT_DELETED", product + ":" + caps);
    }

    @Transactional(readOnly = true)
    public boolean isAuthorized(long tenantId, String hostProduct, String capability) {
        Map<String, Object> grant = grantMapper.find(tenantId, hostProduct, capability);
        return grant != null && intValue(grant.get("enabled")) == 1;
    }

    @Transactional(readOnly = true)
    public MeteringResult metering(String mode, String hostProduct, LocalDate from, LocalDate to) {
        long tenantId = TenantContext.requireTenantId();
        String modeFilter = mode == null || mode.isBlank() ? null : mode.trim().toUpperCase();
        List<MeteringRecord> records = new ArrayList<>();
        for (Map<String, Object> row : meteringMapper.list(tenantId, modeFilter,
                hostProduct == null || hostProduct.isBlank() ? null : hostProduct.trim(), from, to)) {
            records.add(new MeteringRecord((String) row.get("host_product"),
                    (String) row.get("capability"), (String) row.get("mode"),
                    longValue(row.get("call_count")), longValue(row.get("token_in")),
                    longValue(row.get("token_out")),
                    row.get("cost") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(row.get("cost"))),
                    toLocalDate(row.get("stat_date"))));
        }
        return new MeteringResult(records);
    }

    @Transactional
    public void recordUsage(long tenantId, String hostProduct, String capability, String mode,
                            int tokenIn, int tokenOut, Long operatorId) {
        meteringMapper.upsert(IdWorker.getId(), tenantId, hostProduct, capability, mode,
                LocalDate.now(), 1, tokenIn, tokenOut,
                BigDecimal.ZERO, operatorId);
    }

    @Transactional(readOnly = true)
    public ModeValidateResult validateMode() {
        long tenantId = TenantContext.requireTenantId();
        List<Map<String, Object>> grants = grantMapper.listAll(tenantId);
        Set<String> selfEnabled = new HashSet<>();
        for (Map<String, Object> grant : grants) {
            if (SELF_HOST.equals(grant.get("host_product")) && intValue(grant.get("enabled")) == 1) {
                selfEnabled.add((String) grant.get("capability"));
            }
        }
        List<String> issues = new ArrayList<>();
        for (Map<String, Object> grant : grants) {
            String hostProduct = (String) grant.get("host_product");
            if (SELF_HOST.equals(hostProduct) || intValue(grant.get("enabled")) != 1) {
                continue;
            }
            String capability = (String) grant.get("capability");
            if (!selfEnabled.contains(capability)) {
                issues.add("capability " + capability + " 在宿主 " + hostProduct
                        + " 已启用，但 standalone(hub) 未授权");
            }
            if (longValue(grant.get("quota_token_month")) <= 0) {
                issues.add("capability " + capability + " 在宿主 " + hostProduct + " 缺少 token 月额度");
            }
        }
        if (grants.isEmpty()) {
            issues.add("租户未配置任何 AI 能力授权");
        }
        if (!issues.isEmpty()) {
            throw new BizException(AiErrorCode.MODE_INCONSISTENT,
                    "双模式配置不一致: " + String.join("; ", issues));
        }
        return new ModeValidateResult("embedded", true, List.of());
    }

    private GrantView toView(Map<String, Object> row) {
        if (row == null) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "授权写入失败");
        }
        return new GrantView(longValue(row.get("id")), (String) row.get("host_product"),
                (String) row.get("capability"), intValue(row.get("enabled")) == 1,
                intValue(row.get("quota_qps")), longValue(row.get("quota_token_month")),
                (String) row.get("billing_mode"));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        return null;
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private void audit(long tenantId, Long operatorId, String eventType, String bizId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), eventType, "ai", "UPDATE",
                "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "AI_GRANT", bizId, Instant.now()));
    }
}
