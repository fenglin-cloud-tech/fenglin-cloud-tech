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
import com.fenglin.collab.web.dto.CollabDtos.FolderMoveResult;
import com.fenglin.collab.web.dto.CollabDtos.FolderRequest;
import com.fenglin.collab.web.dto.CollabDtos.FolderResult;
import com.fenglin.collab.web.dto.CollabDtos.FolderView;
import com.fenglin.collab.web.dto.CollabDtos.ShareLinkRequest;
import com.fenglin.collab.web.dto.CollabDtos.ShareLinkView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
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

import java.util.List;
import java.util.Map;

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
    private static final String SELF_SHARE = "biz:collab:doc:self:share";

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

    /**
     * 员工自助：共享给我的文档（当前用户为 USER 协作者，且非本人创建）。
     * 与「我的文档」互补，解决管理端加协作者后门户看不到的问题。
     */
    @GetMapping("/shared")
    public ApiResponse<PageResult<DocView>> sharedDocuments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_READ);
        return ApiResponse.success(docService.sharedDocuments(keyword, page, size));
    }

    @GetMapping("/{docId}")
    public ApiResponse<DocView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
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
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
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
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.collaborators(docId));
    }

    @PostMapping("/{docId}/share-link")
    public ApiResponse<ShareLinkView> shareLink(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestBody(required = false) ShareLinkRequest request) {
        PermissionGuard.requireAny(permissions, MANAGE, SELF_SHARE);
        boolean manageGranted = PermissionGuard.has(permissions, MANAGE);
        return ApiResponse.success(docService.shareLink(docId, request, manageGranted));
    }

    // ---------- 文档目录（V1_0_7） ----------

    @GetMapping("/folders")
    public ApiResponse<List<FolderView>> folders(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.folders());
    }

    @PostMapping("/folders")
    public ApiResponse<FolderResult> createFolder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody FolderRequest request) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.createFolder(request));
    }

    @PutMapping("/folders/{folderId}")
    public ApiResponse<FolderResult> renameFolder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("folderId") String folderId,
            @RequestBody FolderRequest request) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.renameFolder(folderId, request));
    }

    @DeleteMapping("/folders/{folderId}")
    public ApiResponse<Void> deleteFolder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("folderId") String folderId) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        docService.deleteFolder(folderId);
        return ApiResponse.success(null);
    }

    @PostMapping("/folders/{folderId}/documents")
    public ApiResponse<FolderMoveResult> moveDocToFolder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("folderId") String folderId,
            @RequestBody Map<String, String> body) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.moveDocToFolder(folderId,
                body == null ? null : body.get("doc_id")));
    }

    @DeleteMapping("/folders/{folderId}/documents/{docId}")
    public ApiResponse<Void> unfileDoc(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("folderId") String folderId,
            @PathVariable("docId") String docId) {
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        docService.unfileDoc(folderId, docId);
        return ApiResponse.success(null);
    }

    @GetMapping("/shared/{token}")
    public ApiResponse<DocView> openShared(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("token") String token) {
        Long tenantId = com.fenglin.collab.support.TenantScope.tenantOfLinkToken(token);
        if (tenantId == null) {
            // 旧 UUID 令牌没有租户信息，只能由已登录调用方在可信租户头下打开
            PermissionGuard.requireAny(permissions, READ, SELF_READ);
            return ApiResponse.success(docService.openByLink(token));
        }
        // v2 令牌自带租户：未登录也可打开，租户只从验签后的令牌解析，不信任客户端头
        return com.fenglin.collab.support.TenantScope.call(tenantId,
                () -> ApiResponse.success(docService.openByLink(token)));
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
        PermissionGuard.requireAny(permissions, READ, SELF_READ);
        return ApiResponse.success(docService.comments(docId));
    }
}
