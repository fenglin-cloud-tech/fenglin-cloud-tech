package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.DocMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
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
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档主链：创建、读取、更新（版本自增 + 乐观校验）、版本列表/手动版本/回滚、协作者、分享链接、评论。
 */
@Service
public class DocService {

    private static final Set<String> DOC_TYPES = Set.of("RICH", "SHEET", "KANBAN");
    private static final Set<String> SECRET_LEVELS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL");
    private static final Set<String> PERMISSIONS = Set.of("READ", "COMMENT", "EDIT", "MANAGE");

    private final DocMapper docMapper;
    private final CollabCrypto crypto;
    private final CollabAudit audit;
    private final ObjectMapper objectMapper;

    public DocService(DocMapper docMapper, CollabCrypto crypto, CollabAudit audit, ObjectMapper objectMapper) {
        this.docMapper = docMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocView create(DocCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "文档标题不能为空");
        }
        String docType = request.docType() == null ? "RICH" : request.docType();
        if (!DOC_TYPES.contains(docType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "文档类型必须为 RICH/SHEET/KANBAN");
        }
        String secretLevel = request.secretLevel() == null ? "INTERNAL" : request.secretLevel();
        if (!SECRET_LEVELS.contains(secretLevel)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "密级必须为 PUBLIC/INTERNAL/CONFIDENTIAL");
        }
        String docId = UUID.randomUUID().toString();
        docMapper.insertDocument(docId, tenantId, request.title(), docType, userId, request.folderId(),
                null, null, secretLevel);
        audit.publish("COLLAB_DOC_CREATED", "CREATE", "collab_document", docId, "LOW");
        return detail(docId);
    }

    @Transactional(readOnly = true)
    public PageResult<DocView> list(String keyword, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<DocView> records = docMapper
                .listDocuments(tenantId, userId, keyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(row -> toDocView(row, tenantId, userId, false)).toList();
        return PageResult.of(records, docMapper.countDocuments(tenantId, userId, keyword), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public DocView detail(String docId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        String permission = resolvePermission(tenantId, doc, userId);
        if (permission == null) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION);
        }
        return toDocView(doc, tenantId, userId, true);
    }

    @Transactional
    public DocView update(String docId, DocUpdateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = docMapper.lockDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        Map<String, Object> full = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, full, userId), "EDIT");
        int currentVersion = Rows.intVal(doc, "currentVersion");
        if (request != null && request.version() != null && request.version() != currentVersion) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT,
                    "文档版本已变化，请刷新后重试（当前版本 " + currentVersion + "）");
        }
        int newVersion = currentVersion + 1;
        String cipher = request == null ? null : crypto.encrypt(tenantId, "collab_document", "content", request.content());
        String hash = request == null ? null : crypto.hash(tenantId, request.content());
        String title = request == null ? null : request.title();
        if (docMapper.updateContent(tenantId, docId, title, cipher, hash, newVersion, currentVersion, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "文档已被其他协作者修改");
        }
        docMapper.insertVersion(IdWorker.getId(), tenantId, docId, newVersion, cipher, userId, "AUTO");
        audit.publish("COLLAB_DOC_UPDATED", "UPDATE", "collab_document", docId, "LOW");
        return detail(docId);
    }

    @Transactional(readOnly = true)
    public PageResult<DocVersionView> versions(String docId, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "READ");
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<DocVersionView> records = docMapper
                .listVersions(tenantId, docId, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new DocVersionView(Rows.intVal(row, "versionNo"), Rows.str(row, "versionType"),
                        Rows.lng(row, "editorId"), Rows.instant(row, "createdAt")))
                .toList();
        return PageResult.of(records, docMapper.countVersions(tenantId, docId), safePage, safeSize);
    }

    @Transactional
    public DocVersionResult createVersion(String docId, DocVersionCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = docMapper.lockDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        Map<String, Object> full = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, full, userId), "EDIT");
        int versionNo = Rows.intVal(doc, "currentVersion");
        Map<String, Object> existing = docMapper.findVersion(tenantId, docId, versionNo);
        if (existing == null) {
            docMapper.insertVersion(IdWorker.getId(), tenantId, docId, versionNo,
                    Rows.str(doc, "contentCipher"), userId, "MANUAL");
            existing = docMapper.findVersion(tenantId, docId, versionNo);
        }
        audit.publish("COLLAB_DOC_VERSION_CREATED", "CREATE", "collab_document", docId, "LOW");
        return new DocVersionResult(versionNo, null, Rows.lng(existing, "editorId"));
    }

    /**
     * 缺口补齐 API-DOC-007：回滚到指定历史版本，生成新的当前版本（保留历史不可变）。
     */
    @Transactional
    public DocRollbackResult rollback(String docId, int versionNo) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = docMapper.lockDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        Map<String, Object> full = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, full, userId), "MANAGE");
        Map<String, Object> version = docMapper.findVersion(tenantId, docId, versionNo);
        if (version == null || Rows.str(version, "contentCipher") == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目标版本不存在或不含内容快照");
        }
        int currentVersion = Rows.intVal(doc, "currentVersion");
        int newVersion = currentVersion + 1;
        String cipher = Rows.str(version, "contentCipher");
        if (docMapper.updateContent(tenantId, docId, null, cipher, null, newVersion, currentVersion, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "文档已被其他协作者修改");
        }
        docMapper.insertVersion(IdWorker.getId(), tenantId, docId, newVersion, cipher, userId, "AUTO");
        audit.publish("COLLAB_DOC_ROLLED_BACK", "UPDATE", "collab_document", docId, "MEDIUM");
        return new DocRollbackResult(newVersion, versionNo);
    }

    @Transactional
    public CollaboratorView addCollaborator(String docId, CollaboratorRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "MANAGE");
        if (request == null || request.subjectId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "协作者不能为空");
        }
        String subjectType = request.subjectType() == null ? "USER" : request.subjectType();
        if (!Set.of("USER", "DEPT", "ROLE").contains(subjectType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "协作者类型必须为 USER/DEPT/ROLE");
        }
        String permission = request.permission() == null ? "READ" : request.permission();
        if (!PERMISSIONS.contains(permission)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "权限必须为 READ/COMMENT/EDIT/MANAGE");
        }
        long collaboratorId = IdWorker.getId();
        docMapper.insertCollaborator(collaboratorId, tenantId, docId, subjectType, request.subjectId(),
                permission, userId);
        audit.publish("COLLAB_DOC_COLLABORATOR_ADDED", "CREATE", "collab_document", docId, "LOW");
        return new CollaboratorView(collaboratorId, subjectType, request.subjectId(), permission, userId);
    }

    @Transactional(readOnly = true)
    public List<CollaboratorView> collaborators(String docId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "READ");
        return docMapper.listCollaborators(tenantId, docId).stream()
                .map(row -> new CollaboratorView(Rows.lngVal(row, "collaboratorId"), Rows.str(row, "subjectType"),
                        Rows.lngVal(row, "subjectId"), Rows.str(row, "permission"), Rows.lng(row, "grantedBy")))
                .toList();
    }

    @Transactional
    public ShareLinkView shareLink(String docId, ShareLinkRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "MANAGE");
        if ("CONFIDENTIAL".equals(Rows.str(doc, "secretLevel"))) {
            throw new BizException(CollabErrorCode.DOC_CONFIDENTIAL_FORBIDDEN);
        }
        int expireDays = request == null || request.expireDays() == null ? 7 : request.expireDays();
        if (expireDays < 1 || expireDays > 365) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "分享有效期需在 1-365 天");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expireAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(expireDays);
        docMapper.setShareLink(tenantId, docId, token, expireAt, userId);
        audit.publish("COLLAB_DOC_SHARE_LINK_CREATED", "UPDATE", "collab_document", docId, "MEDIUM");
        return new ShareLinkView(token, "/collab/docs/" + token, expireAt.toInstant());
    }

    @Transactional(readOnly = true)
    public DocView openByLink(String token) {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> doc = docMapper.findByLinkToken(tenantId, token);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "分享链接不存在");
        }
        Instant expireAt = Rows.instant(doc, "linkExpireAt");
        if (expireAt != null && expireAt.isBefore(Instant.now())) {
            throw new BizException(CollabErrorCode.DOC_LINK_EXPIRED);
        }
        if ("CONFIDENTIAL".equals(Rows.str(doc, "secretLevel"))) {
            throw new BizException(CollabErrorCode.DOC_CONFIDENTIAL_FORBIDDEN);
        }
        String content = crypto.decrypt(tenantId, "collab_document", "content", Rows.str(doc, "contentCipher"));
        return new DocView(Rows.str(doc, "docId"), Rows.str(doc, "title"), "RICH",
                Rows.str(doc, "secretLevel"), Rows.intVal(doc, "currentVersion"), "PUBLISHED",
                Rows.lngVal(doc, "ownerId"), "READ", content, false, "LINK", null);
    }

    @Transactional
    public CommentView createComment(String docId, CommentCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "评论内容不能为空");
        }
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "COMMENT");
        long commentId = IdWorker.getId();
        String cipher = crypto.encrypt(tenantId, "collab_document_comment", "content", request.content());
        String anchor = null;
        if (request.anchor() != null) {
            try {
                anchor = objectMapper.writeValueAsString(request.anchor());
            } catch (JsonProcessingException ex) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "评论锚点格式非法");
            }
        }
        String mentionIds = request.mentionIds() == null || request.mentionIds().isEmpty()
                ? null : "{" + request.mentionIds().stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("") + "}";
        docMapper.insertComment(commentId, tenantId, docId, request.parentId(), userId, cipher, anchor, mentionIds);
        audit.publish("COLLAB_DOC_COMMENT_CREATED", "CREATE", "collab_document_comment", commentId, "LOW");
        return new CommentView(commentId, request.parentId(), userId, request.content(), false, Instant.now());
    }

    /**
     * 缺口补齐 API-DOC-013：文档评论列表。
     */
    @Transactional(readOnly = true)
    public List<CommentView> comments(String docId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        requirePermission(resolvePermission(tenantId, doc, userId), "READ");
        return docMapper.listComments(tenantId, docId).stream()
                .map(row -> new CommentView(Rows.lngVal(row, "commentId"), Rows.lng(row, "parentId"),
                        Rows.lngVal(row, "authorId"),
                        crypto.decrypt(tenantId, "collab_document_comment", "content", Rows.str(row, "contentCipher")),
                        Rows.intVal(row, "resolved") == 1, Rows.instant(row, "createdAt")))
                .toList();
    }

    private Map<String, Object> requireDoc(long tenantId, String docId) {
        Map<String, Object> doc = docMapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private String resolvePermission(long tenantId, Map<String, Object> doc, long userId) {
        if (Rows.lngVal(doc, "ownerId") == userId) {
            return "MANAGE";
        }
        String collaborator = docMapper.findCollaboratorPermission(tenantId, Rows.str(doc, "docId"), userId);
        if (collaborator != null) {
            return collaborator;
        }
        String scope = Rows.str(doc, "shareScope");
        if ("DEPT".equals(scope) || "LINK".equals(scope)) {
            return "READ";
        }
        return null;
    }

    private void requirePermission(String actual, String required) {
        if (actual == null) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION);
        }
        List<String> order = List.of("READ", "COMMENT", "EDIT", "MANAGE");
        if (order.indexOf(actual) < order.indexOf(required)) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION,
                    "需要 " + required + " 权限，当前为 " + actual);
        }
    }

    private DocView toDocView(Map<String, Object> row, long tenantId, long userId, boolean withContent) {
        String permission = resolvePermission(tenantId, row, userId);
        String content = withContent && permission != null
                ? crypto.decrypt(tenantId, "collab_document", "content", Rows.str(row, "contentCipher")) : null;
        return new DocView(Rows.str(row, "docId"), Rows.str(row, "title"),
                Rows.str(row, "docType") == null ? "RICH" : Rows.str(row, "docType"),
                Rows.str(row, "secretLevel"), Rows.intVal(row, "currentVersion"),
                Rows.str(row, "status"), Rows.lngVal(row, "ownerId"), permission, content,
                Rows.intVal(row, "watermarkEnabled") == 1,
                Rows.str(row, "shareScope") == null ? "PRIVATE" : Rows.str(row, "shareScope"),
                Rows.instant(row, "updatedAt"));
    }
}
