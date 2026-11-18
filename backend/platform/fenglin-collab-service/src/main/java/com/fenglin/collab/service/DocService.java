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
import com.fenglin.collab.web.dto.CollabDtos.FolderMoveResult;
import com.fenglin.collab.web.dto.CollabDtos.FolderRequest;
import com.fenglin.collab.web.dto.CollabDtos.FolderResult;
import com.fenglin.collab.web.dto.CollabDtos.FolderView;
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
    private static final Set<String> CONTENT_FORMATS = Set.of("PLAIN", "RICH_JSON");
    /** 富文本 JSON 上限；只做接口层校验，不给 content_cipher 列设长度上限。 */
    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    private final DocMapper docMapper;
    private final CollabCrypto crypto;
    private final CollabAudit audit;
    private final CollabSettingService settingService;
    private final ObjectMapper objectMapper;

    public DocService(DocMapper docMapper, CollabCrypto crypto, CollabAudit audit,
                      CollabSettingService settingService, ObjectMapper objectMapper) {
        this.docMapper = docMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.settingService = settingService;
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

    /**
     * 员工自助：只返回当前登录用户创建的文档，owner 由服务端固定。
     */
    @Transactional(readOnly = true)
    public PageResult<DocView> myDocuments(String keyword, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<DocView> records = docMapper
                .listDocumentsByOwner(tenantId, userId, keyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(row -> toDocView(row, tenantId, userId, false)).toList();
        return PageResult.of(records, docMapper.countDocumentsByOwner(tenantId, userId, keyword), safePage, safeSize);
    }

    /**
     * 员工自助：共享给我的文档（本人是 USER 协作者且非 owner）。
     */
    @Transactional(readOnly = true)
    public PageResult<DocView> sharedDocuments(String keyword, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        List<DocView> records = docMapper
                .listSharedDocuments(tenantId, userId, keyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(row -> toDocView(row, tenantId, userId, false)).toList();
        return PageResult.of(records, docMapper.countSharedDocuments(tenantId, userId, keyword),
                safePage, safeSize);
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
        String content = request == null ? null : request.content();
        String requestedFormat = request == null ? null : normalizeContentFormat(request.contentFormat());
        // 未传格式或只改标题时保持原格式，避免被列默认值覆盖成 PLAIN。
        String effectiveFormat = requestedFormat != null ? requestedFormat : Rows.str(doc, "contentFormat");
        validateContent(content, effectiveFormat);
        String cipher = content == null ? null : crypto.encrypt(tenantId, "collab_document", "content", content);
        String hash = content == null ? null : crypto.hash(tenantId, content);
        String title = request == null ? null : request.title();
        if (docMapper.updateContent(tenantId, docId, title, cipher, hash, requestedFormat, newVersion,
                currentVersion, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "文档已被其他协作者修改");
        }
        docMapper.insertVersion(IdWorker.getId(), tenantId, docId, newVersion, cipher,
                effectiveFormat == null ? "PLAIN" : effectiveFormat, userId, "AUTO");
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
                    Rows.str(doc, "contentCipher"), docFormat(doc), userId, "MANUAL");
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
        String versionFormat = Rows.str(version, "contentFormat");
        // 回滚必须连同格式一起复制，否则富文本版本回滚后会被按纯文本渲染。
        if (docMapper.updateContent(tenantId, docId, null, cipher, null, versionFormat, newVersion,
                currentVersion, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "文档已被其他协作者修改");
        }
        docMapper.insertVersion(IdWorker.getId(), tenantId, docId, newVersion, cipher,
                versionFormat == null ? "PLAIN" : versionFormat, userId, "AUTO");
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
    public ShareLinkView shareLink(String docId, ShareLinkRequest request, boolean manageGranted) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> doc = requireDoc(tenantId, docId);
        String permission = resolvePermission(tenantId, doc, userId);
        if (manageGranted) {
            requirePermission(permission, "MANAGE");
        } else {
            // self:share 仅允许给“自己拥有的文档”建链，不能借分享链接读取他人文档
            if (Rows.lngVal(doc, "ownerId") != userId) {
                throw new BizException(CollabErrorCode.NO_PERMISSION, "只能为自己拥有的文档创建分享链接");
            }
        }
        if ("CONFIDENTIAL".equals(Rows.str(doc, "secretLevel")) && settingService.docPolicy().confidentialNoLink()) {
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

    // ---------- COL-07 文档目录 ----------

    @Transactional(readOnly = true)
    public List<FolderView> folders() {
        return docMapper.listFolders(CollabContext.tenantId(), CollabContext.userId()).stream()
                .map(row -> new FolderView(Rows.str(row, "folderId"), Rows.str(row, "parentId"),
                        Rows.str(row, "name"), Rows.intVal(row, "sortNo"), Rows.lng(row, "docCount")))
                .toList();
    }

    @Transactional
    public FolderResult createFolder(FolderRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        String name = request == null ? null : trimName(request.name());
        if (name == null || name.isEmpty()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目录名不能为空");
        }
        if (name.length() > 64) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目录名不能超过 64 个字符");
        }
        String parentId = request.parentId() == null || request.parentId().isBlank() ? null : request.parentId();
        if (parentId != null && docMapper.findFolder(tenantId, userId, Long.parseLong(parentId)) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "上级目录不存在");
        }
        long id = IdWorker.getId();
        docMapper.insertFolder(id, tenantId, parentId, name, 0, userId);
        audit.publish("COLLAB_DOC_FOLDER_CREATED", "CREATE", "collab_doc_folder", String.valueOf(id), "LOW");
        return new FolderResult(String.valueOf(id), name, parentId);
    }

    @Transactional
    public FolderResult renameFolder(String folderId, FolderRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        String name = request == null ? null : trimName(request.name());
        if (name == null || name.isEmpty()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目录名不能为空");
        }
        long id = parseId(folderId);
        if (docMapper.renameFolder(tenantId, userId, id, name) == 0) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目录不存在");
        }
        audit.publish("COLLAB_DOC_FOLDER_RENAMED", "UPDATE", "collab_doc_folder", folderId, "LOW");
        return new FolderResult(String.valueOf(id), name, null);
    }

    /**
     * 删除目录：非空目录（有子目录或仍有文档归入）拒绝删除，避免文档指向已删目录。
     */
    @Transactional
    public void deleteFolder(String folderId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        long id = parseId(folderId);
        if (docMapper.findFolder(tenantId, userId, id) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目录不存在");
        }
        if (docMapper.countChildFolders(tenantId, userId, id) > 0 || docMapper.countFolderDocs(tenantId, userId, id) > 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "目录非空，请先移出文档或删除子目录");
        }
        docMapper.deleteFolder(tenantId, userId, id);
        audit.publish("COLLAB_DOC_FOLDER_DELETED", "DELETE", "collab_doc_folder", folderId, "MEDIUM");
    }

    @Transactional
    public FolderMoveResult moveDocToFolder(String folderId, String docId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (docId == null || docId.isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "缺少 doc_id");
        }
        long id = parseId(folderId);
        if (docMapper.findFolder(tenantId, userId, id) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目录不存在");
        }
        if (docMapper.moveDocToFolder(tenantId, userId, id, docId) == 0) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "只能归置自己拥有的文档");
        }
        audit.publish("COLLAB_DOC_MOVED_TO_FOLDER", "UPDATE", "collab_document", docId, "LOW");
        return new FolderMoveResult(String.valueOf(id), docId);
    }

    /**
     * 从目录移出：文档回到未归类（folder_id 置空），仅限本人文档。
     */
    @Transactional
    public void unfileDoc(String folderId, String docId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (docId == null || docId.isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "缺少 doc_id");
        }
        long id = parseId(folderId);
        if (docMapper.findFolder(tenantId, userId, id) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "目录不存在");
        }
        if (docMapper.clearDocFolder(tenantId, userId, docId) == 0) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "只能移出自己的文档");
        }
        audit.publish("COLLAB_DOC_MOVED_OUT_FOLDER", "UPDATE", "collab_document", docId, "LOW");
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "目录 ID 非法");
        }
    }

    private String trimName(String value) {
        return value == null ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public DocView openByLink(String token) {        long tenantId = CollabContext.tenantId();
        Map<String, Object> doc = docMapper.findByLinkToken(tenantId, token);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "分享链接不存在");
        }
        Instant expireAt = Rows.instant(doc, "linkExpireAt");
        if (expireAt != null && expireAt.isBefore(Instant.now())) {
            throw new BizException(CollabErrorCode.DOC_LINK_EXPIRED);
        }
        if ("CONFIDENTIAL".equals(Rows.str(doc, "secretLevel")) && settingService.docPolicy().confidentialNoLink()) {
            throw new BizException(CollabErrorCode.DOC_CONFIDENTIAL_FORBIDDEN);
        }
        String content = crypto.decrypt(tenantId, "collab_document", "content", Rows.str(doc, "contentCipher"));
        return new DocView(Rows.str(doc, "docId"), Rows.str(doc, "title"), "RICH", docFormat(doc),
                Rows.str(doc, "secretLevel"), Rows.intVal(doc, "currentVersion"), "PUBLISHED",
                Rows.lngVal(doc, "ownerId"), "READ", content, false, "LINK",
                Rows.str(doc, "folderId"), null);
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
                docFormat(row), Rows.str(row, "secretLevel"), Rows.intVal(row, "currentVersion"),
                Rows.str(row, "status"), Rows.lngVal(row, "ownerId"), permission, content,
                Rows.intVal(row, "watermarkEnabled") == 1,
                Rows.str(row, "shareScope") == null ? "PRIVATE" : Rows.str(row, "shareScope"),
                Rows.str(row, "folderId"), Rows.instant(row, "updatedAt"));
    }

    private String docFormat(Map<String, Object> row) {
        String format = Rows.str(row, "contentFormat");
        return format == null || format.isBlank() ? "PLAIN" : format;
    }

    private String normalizeContentFormat(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String format = requested.trim().toUpperCase(java.util.Locale.ROOT);
        if (!CONTENT_FORMATS.contains(format)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "正文格式必须为 PLAIN 或 RICH_JSON");
        }
        return format;
    }

    /**
     * 正文上限与格式校验：2 MB 按 UTF-8 字节计；RICH_JSON 必须是合法 JSON 对象。
     */
    private void validateContent(String content, String format) {
        if (content == null) {
            return;
        }
        int bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            throw new BizException(CollabErrorCode.PARAM_INVALID,
                    "正文超过 2 MB 上限（当前 " + bytes + " 字节，上限 " + MAX_CONTENT_BYTES + " 字节）");
        }
        if (!"RICH_JSON".equals(format)) {
            return;
        }
        try {
            var node = objectMapper.readTree(content);
            if (node == null || !node.isObject() || !node.path("blocks").isArray()) {
                throw new BizException(CollabErrorCode.PARAM_INVALID,
                        "富文本正文必须是合法 JSON：{\"version\":1,\"blocks\":[...]}");
            }
        } catch (JsonProcessingException ex) {
            throw new BizException(CollabErrorCode.PARAM_INVALID,
                    "富文本正文必须是合法 JSON：{\"version\":1,\"blocks\":[...]}");
        }
    }
}
