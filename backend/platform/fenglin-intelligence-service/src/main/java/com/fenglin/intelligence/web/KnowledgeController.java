package com.fenglin.intelligence.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.intelligence.service.KnowledgeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H1 知识库/检索/图谱接口（/api/intelligence/v1）。
 */
@RestController
@RequestMapping("/api/intelligence/v1")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping("/admin/knowledge-bases")
    public ApiResponse<Map<String, Object>> createKnowledgeBase(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kb:manage");
        return ApiResponse.success(service.createKnowledgeBase(body));
    }

    @GetMapping("/knowledge-bases")
    public ApiResponse<PageResult<Map<String, Object>>> listKnowledgeBases(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.listKnowledgeBases(page, size));
    }

    @PostMapping("/admin/data-sources")
    public ApiResponse<Map<String, Object>> createDataSource(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:source:manage");
        return ApiResponse.success(service.createDataSource(body));
    }

    @GetMapping("/admin/data-sources")
    public ApiResponse<PageResult<Map<String, Object>>> listDataSources(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "tenant:ai:source:manage");
        return ApiResponse.success(service.listDataSources(page, size));
    }

    @PostMapping("/admin/documents")
    public ApiResponse<Map<String, Object>> createDocument(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kb:review");
        return ApiResponse.success(service.createDocument(body));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<PageResult<Map<String, Object>>> listDocuments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long kbId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PermissionGuard.require(permissions, "ai:chat:use");
        return ApiResponse.success(service.listDocuments(kbId, page, size));
    }

    @GetMapping("/admin/documents/{id}")
    public ApiResponse<Map<String, Object>> documentDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:kb:review");
        return ApiResponse.success(service.documentDetail(id));
    }

    @PostMapping("/admin/documents/{id}/review:submit")
    public ApiResponse<Map<String, Object>> submitReview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:kb:review");
        return ApiResponse.success(service.submitReview(id, idemKey, permissions));
    }

    @PostMapping("/admin/documents/{id}/review")
    public ApiResponse<Map<String, Object>> review(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kb:review");
        String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
        return ApiResponse.success(service.review(id, action));
    }

    @PostMapping("/admin/documents/{id}/index")
    public ApiResponse<Map<String, Object>> index(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:index:build");
        return ApiResponse.success(service.index(id));
    }

    @PostMapping("/search/hybrid")
    public ApiResponse<Map<String, Object>> hybridSearch(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:search:query");
        return ApiResponse.success(service.search(body, false));
    }

    @PostMapping("/search/secure")
    public ApiResponse<Map<String, Object>> secureSearch(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:search:secure");
        return ApiResponse.success(service.search(body, true));
    }

    @GetMapping("/documents/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestParam(name = "sec_level", required = false, defaultValue = "2") int secLevel) {
        PermissionGuard.require(permissions, "ai:search:view");
        return ApiResponse.success(service.preview(id, secLevel));
    }

    @GetMapping("/kg/entities")
    public ApiResponse<List<Map<String, Object>>> listKgEntities(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "kb_id", required = false) Long kbId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "ai:kg:query");
        return ApiResponse.success(service.listKgEntities(kbId, keyword, limit));
    }

    @GetMapping("/kg/paths")
    public ApiResponse<Map<String, Object>> kgPath(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "from_id") long fromId,
            @RequestParam(name = "to_id") long toId,
            @RequestParam(name = "max_depth", required = false) Integer maxDepth) {
        PermissionGuard.require(permissions, "ai:kg:query");
        return ApiResponse.success(service.kgPath(fromId, toId, maxDepth));
    }

    @PostMapping("/admin/kg/entities")
    public ApiResponse<Map<String, Object>> createKgEntity(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kg:manage");
        return ApiResponse.success(service.createKgEntity(body));
    }

    @PostMapping("/admin/kg/relations")
    public ApiResponse<Map<String, Object>> createKgRelation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kg:manage");
        return ApiResponse.success(service.createKgRelation(body));
    }

    @GetMapping("/admin/search/analytics")
    public ApiResponse<Map<String, Object>> searchAnalytics(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        PermissionGuard.require(permissions, "tenant:ai:search:analytics");
        return ApiResponse.success(service.searchAnalytics(limit, from, to));
    }

    @GetMapping("/search/suggest")
    public ApiResponse<List<String>> suggest(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam String q,
            @RequestParam(required = false) Integer limit) {
        PermissionGuard.require(permissions, "ai:search:suggest");
        return ApiResponse.success(service.suggest(q, limit));
    }

    @GetMapping("/kg/graph")
    public ApiResponse<Map<String, Object>> kgGraph(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "kbId", required = false) Long kbId,
            @RequestParam(name = "entityId", required = false) Long entityId,
            @RequestParam(name = "depth", required = false) Integer depth) {
        PermissionGuard.require(permissions, "ai:kg:query");
        return ApiResponse.success(service.kgGraph(kbId, entityId, depth));
    }

    @GetMapping("/kg/entities/{id}")
    public ApiResponse<Map<String, Object>> kgEntityDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "ai:kg:query");
        return ApiResponse.success(service.kgEntityDetail(id));
    }

    @DeleteMapping("/admin/kg/entities/{id}")
    public ApiResponse<Map<String, Object>> deleteKgEntity(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:kg:manage");
        return ApiResponse.success(service.deleteKgEntity(id));
    }

    @GetMapping("/admin/knowledge-bases/{id}/acl")
    public ApiResponse<List<Map<String, Object>>> listAcl(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:kb:manage");
        return ApiResponse.success(service.listAcl(id));
    }

    @PutMapping("/admin/knowledge-bases/{id}/acl")
    public ApiResponse<Map<String, Object>> saveAcl(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, "tenant:ai:kb:manage");
        return ApiResponse.success(service.saveAcl(id, body));
    }

    @PostMapping("/admin/documents/{id}/parse")
    public ApiResponse<Map<String, Object>> parseDocument(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        PermissionGuard.require(permissions, "ai:document:parse");
        return ApiResponse.success(service.parseDocument(id, body == null ? Map.of() : body));
    }

    @PostMapping("/admin/documents/upload")
    public ApiResponse<Map<String, Object>> uploadDocument(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> form) throws java.io.IOException {
        PermissionGuard.require(permissions, "ai:document:parse");
        Map<String, Object> params = new LinkedHashMap<>(form);
        return ApiResponse.success(service.uploadDocument(file.getOriginalFilename(), file.getBytes(), params));
    }

    @PostMapping("/admin/data-sources/{id}/test")
    public ApiResponse<Map<String, Object>> testDataSource(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:source:manage");
        return ApiResponse.success(service.testDataSource(id));
    }

    @PostMapping("/admin/data-sources/{id}/sync")
    public ApiResponse<Map<String, Object>> syncDataSource(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, "tenant:ai:source:manage");
        return ApiResponse.success(service.syncDataSource(id));
    }
}
