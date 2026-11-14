package com.fenglin.collab.web;

import com.fenglin.collab.service.DocService;
import com.fenglin.collab.web.dto.CollabDtos.CollaboratorRequest;
import com.fenglin.collab.web.dto.CollabDtos.CollaboratorView;
import com.fenglin.collab.web.dto.CollabDtos.CommentCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.CommentView;
import com.fenglin.collab.web.dto.CollabDtos.DocCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocRollbackResult;
import com.fenglin.collab.web.dto.CollabDtos.DocUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocVersionCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocVersionResult;
import com.fenglin.collab.web.dto.CollabDtos.DocVersionView;
import com.fenglin.collab.web.dto.CollabDtos.DocView;
import com.fenglin.collab.web.dto.CollabDtos.ShareLinkRequest;
import com.fenglin.collab.web.dto.CollabDtos.ShareLinkView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档接口（API-DOC-001/002/003/004/005/006/007/008/009/010/011/012/013）。
 */
@RestController
@RequestMapping("/api/collab/v1/docs")
public class CollabDocController {

    private static final String READ = "biz:collab:doc:read";
    private static final String SELF_READ = "biz:collab:doc:self:read";
    private static final String EDIT = "biz:collab:doc:edit";
    private static final String COMMENT = "biz:collab:doc:comment";
    private static final String MANAGE = "biz:collab:doc:manage";

    private final DocService docService;

    public CollabDocController(DocService docService) {
        this.docService = docService;
    }

    @PostMapping
    public ApiResponse<DocView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DocCreateRequest request) {
        PermissionGuard.require(permissions, EDIT);
        return ApiResponse.success(docService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<DocView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.list(keyword, page, size));
    }

    /**
     * 员工自助：只返回当前登录用户创建的文档，owner 由服务端固定。
     */
    @GetMapping("/my")
    public ApiResponse<PageResult<DocView>> myDocuments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_READ);
        return ApiResponse.success(docService.myDocuments(keyword, page, size));
    }

    @GetMapping("/{docId}")
    public ApiResponse<DocView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.detail(docId));
    }

    @PutMapping("/{docId}")
    public ApiResponse<DocView> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody DocUpdateRequest request) {
        PermissionGuard.require(permissions, EDIT);
        return ApiResponse.success(docService.update(docId, request));
    }

    @GetMapping("/{docId}/versions")
    public ApiResponse<PageResult<DocVersionView>> versions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.versions(docId, page, size));
    }

    @PostMapping("/{docId}/versions")
    public ApiResponse<DocVersionResult> createVersion(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody(required = false) DocVersionCreateRequest request) {
        PermissionGuard.require(permissions, EDIT);
        return ApiResponse.success(docService.createVersion(docId, request));
    }

    @PostMapping("/{docId}/rollback/{versionNo}")
    public ApiResponse<DocRollbackResult> rollback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @PathVariable("versionNo") int versionNo) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(docService.rollback(docId, versionNo));
    }

    @PostMapping("/{docId}/collaborators")
    public ApiResponse<CollaboratorView> addCollaborator(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody CollaboratorRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(docService.addCollaborator(docId, request));
    }

    @GetMapping("/{docId}/collaborators")
    public ApiResponse<List<CollaboratorView>> collaborators(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.collaborators(docId));
    }

    @PostMapping("/{docId}/share-link")
    public ApiResponse<ShareLinkView> shareLink(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody(required = false) ShareLinkRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(docService.shareLink(docId, request));
    }

    @GetMapping("/shared/{token}")
    public ApiResponse<DocView> openShared(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("token") String token) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.openByLink(token));
    }

    @PostMapping("/{docId}/comments")
    public ApiResponse<CommentView> createComment(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody CommentCreateRequest request) {
        PermissionGuard.require(permissions, COMMENT);
        return ApiResponse.success(docService.createComment(docId, request));
    }

    @GetMapping("/{docId}/comments")
    public ApiResponse<List<CommentView>> comments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(docService.comments(docId));
    }
}
