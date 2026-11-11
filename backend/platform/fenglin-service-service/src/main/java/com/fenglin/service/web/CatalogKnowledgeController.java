package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.CatalogService;
import com.fenglin.service.service.CsatAssetService;
import com.fenglin.service.service.KbService;
import com.fenglin.service.service.ProblemChangeService;
import com.fenglin.service.service.TicketService;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务目录、知识库、满意度、问题/变更与 IT 资产。
 */
@RestController
@RequestMapping("/api/service/v1")
public class CatalogKnowledgeController {

    private static final String CATALOG_VIEW = "service:catalog:view";
    private static final String CATALOG_APPLY = "service:catalog:apply";
    private static final String ASSET_REMINDER_VIEW = "service:asset:reminder:view";
    private static final String KB_VIEW = "service:kb:view";
    private static final String KB_EDIT = "service:kb:edit";
    private static final String KB_PUBLISH = "service:kb:publish";
    private static final String KB_SUGGEST = "service:kb:suggest";
    private static final String CSAT_SUBMIT = "service:csat:submit";
    private static final String PROBLEM = "service:problem:manage";
    private static final String CHANGE = "service:change:manage";
    private static final String ASSET_VIEW = "service:asset:view";
    private static final String ASSET_MANAGE = "service:asset:manage";

    private final CatalogService catalogService;
    private final KbService kbService;
    private final CsatAssetService csatAssetService;
    private final ProblemChangeService problemChangeService;
    private final TicketService ticketService;

    public CatalogKnowledgeController(CatalogService catalogService, KbService kbService,
                                      CsatAssetService csatAssetService,
                                      ProblemChangeService problemChangeService, TicketService ticketService) {
        this.catalogService = catalogService;
        this.kbService = kbService;
        this.csatAssetService = csatAssetService;
        this.problemChangeService = problemChangeService;
        this.ticketService = ticketService;
    }

    // ---------- 服务目录 ----------
    @GetMapping("/catalog/tree")
    public ApiResponse<List<Map<String, Object>>> tree(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, CATALOG_VIEW);
        return ApiResponse.success(catalogService.tree());
    }

    @GetMapping("/catalog/items")
    public ApiResponse<List<Map<String, Object>>> items(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "catalogId", required = false) Long catalogId) {
        PermissionGuard.require(permissions, CATALOG_VIEW);
        return ApiResponse.success(catalogService.items(catalogId));
    }

    @GetMapping("/catalog/items/{id}")
    public ApiResponse<Map<String, Object>> item(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CATALOG_VIEW);
        return ApiResponse.success(catalogService.item(id));
    }

    @PostMapping("/catalog/items/{id}/apply")
    public ApiResponse<Map<String, Object>> apply(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CATALOG_APPLY);
        return ApiResponse.success(catalogService.apply(id, body, permissions, requestId));
    }

    // ---------- 知识库 ----------
    @GetMapping("/kb/categories")
    public ApiResponse<List<Map<String, Object>>> categories(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, KB_VIEW);
        return ApiResponse.success(kbService.categories());
    }

    @PostMapping("/kb/categories")
    public ApiResponse<Map<String, Object>> createCategory(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, KB_EDIT);
        return ApiResponse.success(kbService.createCategory(body));
    }

    @GetMapping("/kb/articles")
    public ApiResponse<PageResult<Map<String, Object>>> articles(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, KB_VIEW);
        return ApiResponse.success(kbService.list(status, categoryId, keyword, page, size));
    }

    @GetMapping("/kb/articles/{id}")
    public ApiResponse<Map<String, Object>> article(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, KB_VIEW);
        return ApiResponse.success(kbService.detail(id));
    }

    @PostMapping("/kb/articles")
    public ApiResponse<Map<String, Object>> saveArticle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        boolean publishing = "PUBLISHED".equalsIgnoreCase(String.valueOf(body.get("status")));
        PermissionGuard.require(permissions, publishing ? KB_PUBLISH : KB_EDIT);
        return ApiResponse.success(kbService.save(body));
    }

    @PostMapping("/kb/articles/{id}/feedback")
    public ApiResponse<Map<String, Object>> feedback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, KB_VIEW);
        return ApiResponse.success(kbService.feedback(id, body));
    }

    @GetMapping("/kb/suggest")
    public ApiResponse<List<Map<String, Object>>> suggest(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "ticketId", required = false) Long ticketId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, KB_SUGGEST);
        return ApiResponse.success(kbService.suggest(ticketId, keyword, limit));
    }

    // ---------- 满意度 ----------
    @PostMapping("/csat")
    public ApiResponse<Map<String, Object>> submitCsat(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CSAT_SUBMIT);
        return ApiResponse.success(csatAssetService.submitCsat(body));
    }

    // ---------- 问题 / 变更 ----------
    @PostMapping("/problems")
    public ApiResponse<Map<String, Object>> createProblem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PROBLEM);
        return ApiResponse.success(problemChangeService.createProblem(body));
    }

    @GetMapping("/problems")
    public ApiResponse<PageResult<Map<String, Object>>> problems(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, PROBLEM);
        return ApiResponse.success(problemChangeService.problems(status, keyword, page, size));
    }

    @GetMapping("/problems/{id}")
    public ApiResponse<Map<String, Object>> problem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, PROBLEM);
        return ApiResponse.success(problemChangeService.problem(id));
    }

    @PostMapping("/problems/{id}/aggregate")
    public ApiResponse<Map<String, Object>> aggregateProblem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, PROBLEM);
        Map<String, Object> problem = problemChangeService.updateProblem(id, body);
        Object ticketIds = body.get("ticket_ids");
        if (ticketIds instanceof List<?> list) {
            for (Object ticketId : list) {
                ticketService.addRelation(ServiceContext.requireLong(ticketId),
                        ServiceMaps.of("rel_type", "PROBLEM", "rel_id", id));
            }
        }
        return ApiResponse.success(problem);
    }

    @PostMapping("/changes")
    public ApiResponse<Map<String, Object>> createChange(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CHANGE);
        return ApiResponse.success(problemChangeService.createChange(body, permissions, requestId));
    }

    @GetMapping("/changes")
    public ApiResponse<PageResult<Map<String, Object>>> changes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CHANGE);
        return ApiResponse.success(problemChangeService.changes(status, keyword, page, size));
    }

    @GetMapping("/changes/{id}")
    public ApiResponse<Map<String, Object>> change(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CHANGE);
        return ApiResponse.success(problemChangeService.change(id));
    }

    @PostMapping("/changes/{id}/approve")
    public ApiResponse<Map<String, Object>> approveChange(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CHANGE);
        return ApiResponse.success(problemChangeService.approveChange(id, body));
    }

    // ---------- IT 资产 ----------
    @GetMapping("/assets")
    public ApiResponse<PageResult<Map<String, Object>>> assets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "assetType", required = false) String assetType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        // 仅持查看权限时强制收敛为本人资产，忽略客户端传入的 ownerId
        Long scopedOwnerId = ownerId;
        if (!PermissionGuard.has(permissions, ASSET_MANAGE)) {
            scopedOwnerId = ServiceContext.userId();
        }
        return ApiResponse.success(csatAssetService.listAssets(status, scopedOwnerId, assetType, page, size));
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<Map<String, Object>> asset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(csatAssetService.asset(id, PermissionGuard.has(permissions, ASSET_MANAGE)));
    }

    @PostMapping("/assets")
    public ApiResponse<Map<String, Object>> createAsset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ASSET_MANAGE);
        return ApiResponse.success(csatAssetService.createAsset(body));
    }

    @PostMapping("/assets/{id}/borrow")
    public ApiResponse<Map<String, Object>> borrow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ASSET_MANAGE);
        body.put("action", "BORROW");
        return ApiResponse.success(csatAssetService.assetAction(id, body));
    }

    @PostMapping("/assets/{id}/return")
    public ApiResponse<Map<String, Object>> returnAsset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, ASSET_MANAGE);
        Map<String, Object> payload = body == null ? new java.util.LinkedHashMap<>() : body;
        payload.put("action", "RETURN");
        return ApiResponse.success(csatAssetService.assetAction(id, payload));
    }

    @PostMapping("/assets/{id}/return-self")
    public ApiResponse<Map<String, Object>> returnSelf(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(csatAssetService.returnSelf(id));
    }

    @GetMapping("/assets/reminders")
    public ApiResponse<Map<String, Object>> assetReminders(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, ASSET_REMINDER_VIEW);
        return ApiResponse.success(csatAssetService.assetReminder(permissions));
    }
}
