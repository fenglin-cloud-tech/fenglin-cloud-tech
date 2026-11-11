package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.client.WorkflowClient;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务目录：目录树、目录项申请（可触发审批）、管理端目录/SLA 配置。
 */
@Service
@Transactional
public class CatalogService {

    private final ConfigMapper configMapper;
    private final TicketService ticketService;
    private final WorkflowClient workflowClient;
    private final ServiceAudit audit;

    public CatalogService(ConfigMapper configMapper, TicketService ticketService,
                          WorkflowClient workflowClient, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.ticketService = ticketService;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    public List<Map<String, Object>> tree() {
        long tenantId = ServiceContext.tenantId();
        List<Map<String, Object>> catalogs = ServiceMaps.parseAll(configMapper.listCatalogs(tenantId), "visible_scope");
        List<Map<String, Object>> items = ServiceMaps.parseAll(configMapper.listItems(tenantId, null), "visible_scope");
        for (Map<String, Object> catalog : catalogs) {
            Object catalogId = catalog.get("id");
            List<Map<String, Object>> children = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (String.valueOf(catalogId).equals(String.valueOf(item.get("catalog_id")))) {
                    children.add(item);
                }
            }
            catalog.put("items", children);
        }
        return catalogs;
    }

    public List<Map<String, Object>> items(Long catalogId) {
        return ServiceMaps.parseAll(configMapper.listItems(ServiceContext.tenantId(), catalogId), "visible_scope");
    }

    public Map<String, Object> item(long id) {
        Map<String, Object> item = configMapper.findItem(ServiceContext.tenantId(), id);
        if (item == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(item, "visible_scope");
    }

    @Transactional
    public Map<String, Object> apply(long itemId, Map<String, Object> body, String permissions, String requestId) {
        Map<String, Object> item = item(itemId);
        if (!"ACTIVE".equals(Rows.str(item, "status"))) {
            throw new BizException(ServiceErrorCode.CATALOG_ITEM_DISABLED);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", ServiceContext.string(body.get("title")) == null
                ? Rows.str(item, "name") : ServiceContext.string(body.get("title")));
        payload.put("description", ServiceContext.string(body.get("description")));
        payload.put("ticket_type", Rows.str(item, "ticket_type"));
        payload.put("catalog_item_id", itemId);
        payload.put("priority", ServiceContext.string(body.get("priority")));
        payload.put("source", "CATALOG");
        payload.put("requester_id", body.get("requester_id"));
        payload.put("contact", body.get("contact"));
        payload.put("form_data", body.get("form_data"));
        Map<String, Object> ticket = ticketService.create(payload, null);
        String instance = null;
        if (Rows.bool(item, "need_approval")) {
            String flowKey = Rows.str(item, "approval_flow_key") == null
                    ? "svcCatalogItemApproval" : Rows.str(item, "approval_flow_key");
            instance = workflowClient.startApproval(permissions, requestId, flowKey,
                    String.valueOf(ticket.get("id")), "SERVICE_CATALOG",
                    "目录项申请：" + Rows.str(item, "name"), Map.of("ticket_id", ticket.get("id")));
        }
        audit.low("catalog.apply", "catalog_item", itemId);
        return ServiceMaps.of("ticket", ticket, "approval_instance_no", instance);
    }

    @Transactional
    public Map<String, Object> saveCatalog(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        // 兼容单条保存：管理端只提交一个对象时，按字段判断是目录还是目录项，不再强制外层包数组。
        if (!(body.get("catalogs") instanceof List<?>) && !(body.get("items") instanceof List<?>)
                && ServiceContext.string(body.get("name")) != null) {
            boolean looksLikeItem = body.get("catalog_id") != null || body.get("code") != null
                    || body.get("ticket_type") != null;
            Map<String, Object> wrapped = new LinkedHashMap<>(body);
            wrapped.put(looksLikeItem ? "items" : "catalogs", List.of(new LinkedHashMap<>(body)));
            body = wrapped;
        }
        int catalogs = 0;
        int items = 0;
        Object catalogList = body.get("catalogs");
        if (catalogList instanceof List<?> list) {
            for (Object each : list) {
                if (!(each instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> map = cast(raw);
                long id = map.get("id") == null ? IdWorker.getId() : ServiceContext.requireLong(map.get("id"));
                configMapper.upsertCatalog(id, tenantId,
                        ServiceContext.requireText(ServiceContext.string(map.get("name"))),
                        map.get("parent_id") == null ? null : ServiceContext.requireLong(map.get("parent_id")),
                        ServiceContext.string(map.get("icon")),
                        map.get("sort_no") == null ? 0 : (int) ServiceContext.requireLong(map.get("sort_no")),
                        ServiceMaps.json(map.get("visible_scope")),
                        normalizeStatus(ServiceContext.string(map.get("status"))), userId);
                catalogs++;
            }
        }
        Object itemList = body.get("items");
        if (itemList instanceof List<?> list) {
            for (Object each : list) {
                if (!(each instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> map = cast(raw);
                long catalogId = ServiceContext.requireLong(map.get("catalog_id"));
                long id = map.get("id") == null ? IdWorker.getId() : ServiceContext.requireLong(map.get("id"));
                String code = ServiceContext.string(map.get("code")) == null
                        ? "IT-" + id : ServiceContext.string(map.get("code"));
                configMapper.upsertCatalogItem(id, tenantId, catalogId, code,
                        ServiceContext.requireText(ServiceContext.string(map.get("name"))),
                        ServiceContext.string(map.get("description")),
                        map.get("ticket_type") == null ? "REQUEST" : ServiceContext.string(map.get("ticket_type")),
                        map.get("sla_policy_id") == null ? null : ServiceContext.requireLong(map.get("sla_policy_id")),
                        Boolean.TRUE.equals(map.get("need_approval")),
                        ServiceContext.string(map.get("approval_flow_key")),
                        ServiceMaps.json(map.get("visible_scope")),
                        map.get("sort_no") == null ? 0 : (int) ServiceContext.requireLong(map.get("sort_no")),
                        normalizeStatus(ServiceContext.string(map.get("status"))), userId);
                items++;
            }
        }
        Object removed = body.get("removed_item_ids");
        int disabled = 0;
        if (removed instanceof List<?> list) {
            for (Object id : list) {
                disabled += configMapper.updateItemStatus(tenantId, ServiceContext.requireLong(id), "DISABLED", userId);
            }
        }
        audit.high("catalog.config", "catalog", null);
        return ServiceMaps.of("catalogs", catalogs, "items", items, "disabled_items", disabled);
    }

    public List<Map<String, Object>> listPolicies() {
        return ServiceMaps.parseAll(configMapper.listPolicies(ServiceContext.tenantId()), "pause_on_status",
                "escalation_matrix");
    }

    @Transactional
    public Map<String, Object> savePolicy(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = body.get("id") == null ? IdWorker.getId() : ServiceContext.requireLong(body.get("id"));
        configMapper.upsertPolicy(id, tenantId,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                ServiceContext.string(body.get("priority")), ServiceContext.string(body.get("ticket_type")),
                body.get("catalog_item_id") == null ? null : ServiceContext.requireLong(body.get("catalog_item_id")),
                ServiceContext.string(body.get("calendar_id")),
                body.get("response_minutes") == null ? 60 : (int) ServiceContext.requireLong(body.get("response_minutes")),
                body.get("resolve_minutes") == null ? 1440 : (int) ServiceContext.requireLong(body.get("resolve_minutes")),
                ServiceMaps.json(body.get("pause_on_status")),
                body.get("warning_threshold") == null ? 80 : (int) ServiceContext.requireLong(body.get("warning_threshold")),
                ServiceMaps.json(body.get("escalation_matrix")),
                body.get("status") == null ? "ACTIVE" : ServiceContext.string(body.get("status")), userId);
        audit.high("sla.config", "sla_policy", id);
        return ServiceMaps.of("id", id);
    }

    /** 目录/目录项状态统一为 ACTIVE/DISABLED，兼容前端历史值 ENABLED。 */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "ACTIVE";
        }
        String upper = status.toUpperCase();
        return "ENABLED".equals(upper) ? "ACTIVE" : upper;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
