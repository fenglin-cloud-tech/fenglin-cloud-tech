package com.fenglin.collab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.collab.support.ImageAssetTokenService;
import com.fenglin.collab.support.RichImageBlocks;
import com.fenglin.collab.support.Rows;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为富文本正文中的图片签发短期访问令牌：只对确实属于该文档的 fileId 签发，
 * 令牌主体区分登录用户（u*）与分享链接哈希（lh*）。
 */
@Component
public class DocImageTokenIssuer {

    private final DocImageStore imageStore;
    private final ImageAssetTokenService tokenService;
    private final ObjectMapper objectMapper;

    public DocImageTokenIssuer(DocImageStore imageStore, ImageAssetTokenService tokenService,
                               ObjectMapper objectMapper) {
        this.imageStore = imageStore;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    public Map<String, String> issue(long tenantId, String docId, String content, String format, String subject) {
        List<Long> fileIds = RichImageBlocks.fileIds(content, format, objectMapper);
        if (fileIds.isEmpty()) {
            return null;
        }
        Set<Long> owned = new HashSet<>();
        for (Map<String, Object> row : imageStore.listByDoc(tenantId, docId)) {
            if ("AVAILABLE".equals(Rows.str(row, "status"))) {
                owned.add(Rows.lngVal(row, "fileId"));
            }
        }
        Map<String, String> tokens = new HashMap<>();
        for (Long fileId : fileIds) {
            if (owned.contains(fileId)) {
                tokens.put(String.valueOf(fileId), tokenService.issue(tenantId, docId, fileId, subject));
            }
        }
        return tokens.isEmpty() ? null : tokens;
    }
}
