package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.mapper.TicketMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 满意度与 IT 资产。
 */
@Service
@Transactional
public class CsatAssetService {

    private final ConfigMapper configMapper;
    private final TicketMapper ticketMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceAudit audit;

    public CsatAssetService(ConfigMapper configMapper, TicketMapper ticketMapper,
                            CommonsMapper commonsMapper, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.ticketMapper = ticketMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> submitCsat(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long ticketId = ServiceContext.requireLong(body.get("ticket_id"));
        Map<String, Object> ticket = ticketMapper.findById(tenantId, ticketId);
        if (ticket == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (configMapper.countCsat(tenantId, ticketId) > 0) {
            throw new BizException(ServiceErrorCode.TICKET_ALREADY_RATED);
        }
        int score = body.get("score") == null ? 0 : (int) ServiceContext.requireLong(body.get("score"));
        if (score < 1 || score > 5) {
            throw new BizException(ServiceErrorCode.CSAT_SCORE_INVALID);
        }
        long id = IdWorker.getId();
        configMapper.insertCsat(id, tenantId, ticketId, score, ServiceMaps.json(body.get("tags")),
                ServiceContext.string(body.get("comment")),
                body.get("respondent_id") == null ? userId : ServiceContext.requireLong(body.get("respondent_id")),
                body.get("channel") == null ? "PORTAL" : ServiceContext.string(body.get("channel")));
        audit.low("csat.submit", "csat", id);
        return ServiceMaps.of("id", id, "ticket_id", ticketId, "score", score);
    }

    public Map<String, Object> csatReport() {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> summary = configMapper.csatReport(tenantId);
        return ServiceMaps.of("summary", summary, "distribution", configMapper.csatDistribution(tenantId));
    }

    public PageResult<Map<String, Object>> listAssets(String status, Long ownerId, String assetType,
                                                      Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = configMapper.listAssets(tenantId, blank(status), ownerId,
                blank(assetType), limit, offset);
        long total = configMapper.countAssets(tenantId, blank(status), ownerId, blank(assetType));
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    public Map<String, Object> asset(long id) {
        Map<String, Object> asset = configMapper.findAsset(ServiceContext.tenantId(), id);
        if (asset == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return asset;
    }

    /**
     * 读资产详情：持管理权限可读全租户；仅持查看权限时只能读本人名下资产。
     */
    public Map<String, Object> asset(long id, boolean canViewAll) {
        Map<String, Object> asset = asset(id);
        if (!canViewAll) {
            Long ownerId = Rows.lng(asset, "owner_id");
            if (ownerId == null || ownerId.longValue() != ServiceContext.userId()) {
                throw new BizException(ServiceErrorCode.NO_PERMISSION, "只能查看本人名下的资产");
            }
        }
        return asset;
    }

    public Map<String, Object> createAsset(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long id = IdWorker.getId();
        String assetNo = ServiceContext.string(body.get("asset_no")) == null
                ? "CI" + id : ServiceContext.string(body.get("asset_no"));
        configMapper.insertAsset(id, tenantId, assetNo,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                ServiceContext.string(body.get("asset_type")),
                body.get("owner_id") == null ? null : ServiceContext.requireLong(body.get("owner_id")),
                body.get("dept_id") == null ? null : ServiceContext.requireLong(body.get("dept_id")),
                body.get("status") == null ? "IN_USE" : ServiceContext.string(body.get("status")),
                null, ServiceContext.string(body.get("ext_ref")), ServiceContext.userId());
        return asset(id);
    }

    @Transactional
    public Map<String, Object> assetAction(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> asset = configMapper.findAsset(tenantId, id);
        if (asset == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        String action = ServiceContext.requireText(ServiceContext.string(body.get("action"))).toUpperCase();
        String current = Rows.str(asset, "status");
        String status;
        Long ownerId = Rows.lng(asset, "owner_id");
        Timestamp dueAt = null;
        switch (action) {
            case "BORROW" -> {
                if (!"IDLE".equals(current) && !"IN_USE".equals(current)) {
                    throw new BizException(ServiceErrorCode.ASSET_STATE_INVALID);
                }
                status = "BORROWED";
                ownerId = ServiceContext.requireLong(body.get("owner_id"));
                int days = body.get("days") == null ? 7 : (int) ServiceContext.requireLong(body.get("days"));
                dueAt = Timestamp.from(Instant.now().plus(days, ChronoUnit.DAYS));
            }
            case "RETURN" -> {
                if (!"BORROWED".equals(current)) {
                    throw new BizException(ServiceErrorCode.ASSET_STATE_INVALID);
                }
                status = "IDLE";
                ownerId = null;
            }
            case "RETIRE" -> {
                status = "RETIRED";
                ownerId = null;
            }
            default -> throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        configMapper.updateAsset(tenantId, id, status, ownerId, dueAt, userId);
        audit.low("asset." + action.toLowerCase(), "asset", id);
        return asset(id);
    }

    /**
     * 员工自助归还：仅借用人本人可归还，且资产必须处于借用中；归还直接置为在库（IDLE）。
     */
    @Transactional
    public Map<String, Object> returnSelf(long id) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> asset = configMapper.findAsset(tenantId, id);
        if (asset == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"BORROWED".equals(Rows.str(asset, "status"))) {
            throw new BizException(ServiceErrorCode.ASSET_STATE_INVALID, "该资产当前不在借用中，无法归还");
        }
        Long ownerId = Rows.lng(asset, "owner_id");
        if (ownerId == null || ownerId.longValue() != userId) {
            throw new BizException(ServiceErrorCode.NO_PERMISSION, "只能归还本人借用中的资产");
        }
        configMapper.updateAsset(tenantId, id, "IDLE", null, null, userId);
        audit.low("asset.return_self", "asset", id);
        return asset(id);
    }

    public Map<String, Object> assetReminder(String permissions) {
        Long ownerId = PermissionGuard.has(permissions, "service:asset:manage")
                ? null : ServiceContext.userId();
        return configMapper.assetSummary(ServiceContext.tenantId(), ownerId);
    }

    /**
     * 员工自助：只查询当前登录用户名下资产。
     */
    public PageResult<Map<String, Object>> myAssets(String status, String assetType, Integer page, Integer size) {
        return listAssets(status, ServiceContext.userId(), assetType, page, size);
    }

    /**
     * 员工自助：只汇总当前登录用户名下资产。
     */
    public Map<String, Object> myAssetReminder() {
        return configMapper.assetSummary(ServiceContext.tenantId(), ServiceContext.userId());
    }

    public Map<String, Object> now() {
        return ServiceMaps.of("now", commonsMapper.now());
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
