package com.fenglin.collab.service;

import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.DocImageMapper;
import com.fenglin.collab.mapper.DocMapper;
import com.fenglin.collab.support.Rows;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 图片元数据与分享链接校验的事务边界：短事务只做 DB 读写，
 * 加解密与文件 IO 在事务外执行，避免流式输出期间占用数据库连接。
 */
@Service
public class DocImageStore {

    private final DocImageMapper imageMapper;
    private final DocMapper docMapper;

    public DocImageStore(DocImageMapper imageMapper, DocMapper docMapper) {
        this.imageMapper = imageMapper;
        this.docMapper = docMapper;
    }

    @Transactional
    public void insert(long id, long tenantId, String docId, long fileId, long uploaderId,
                       String mime, long sizeBytes, String sha256, int width, int height) {
        imageMapper.insertImage(id, tenantId, docId, fileId, uploaderId, mime, sizeBytes, sha256, width, height);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> find(long tenantId, long fileId) {
        return imageMapper.findImage(tenantId, fileId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByDoc(long tenantId, String docId) {
        return imageMapper.listByDoc(tenantId, docId);
    }

    /**
     * 上传前校验：文档存在且当前用户为所有者或具备 EDIT/MANAGE 协作权限。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> requireEdit(long tenantId, long userId, String docId) {
        Map<String, Object> doc = docMapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        if (Rows.lngVal(doc, "ownerId") == userId) {
            return doc;
        }
        String permission = docMapper.findCollaboratorPermission(tenantId, docId, userId);
        if ("EDIT".equals(permission) || "MANAGE".equals(permission)) {
            return doc;
        }
        throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "需要 EDIT 权限才能上传图片");
    }

    /**
     * 分享链接校验：存在、未过期、非机密禁链。返回文档行供图片归属校验。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> requireValidLink(long tenantId, String linkToken) {
        Map<String, Object> doc = docMapper.findByLinkToken(tenantId, linkToken);
        if (doc == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "分享链接不存在");
        }
        Instant expireAt = Rows.instant(doc, "linkExpireAt");
        if (expireAt != null && expireAt.isBefore(Instant.now())) {
            throw new BizException(CollabErrorCode.DOC_LINK_EXPIRED);
        }
        return doc;
    }
}
