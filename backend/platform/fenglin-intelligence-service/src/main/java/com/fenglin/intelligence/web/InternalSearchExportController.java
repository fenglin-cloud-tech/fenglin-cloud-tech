package com.fenglin.intelligence.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.intelligence.mapper.KnowledgeMapper;
import com.fenglin.intelligence.support.IntelligenceContext;
import com.fenglin.intelligence.support.Rows;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PLAT-26 二期：云智域内部检索导出。
 * 只暴露检索所需字段（doc_id/title/content/secret_level），租户内只读、游标分页；
 * 仅导出已索引、且知识库非 PRIVATE 的分块，沿用该域既有可见性口径。
 * 网关只路由 /api/intelligence/v1/**，本接口供 search-service 内部拉取。
 */
@RestController
public class InternalSearchExportController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final KnowledgeMapper knowledgeMapper;

    public InternalSearchExportController(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    @GetMapping("/internal/search-export")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> export(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "200") Integer limit) {
        long tenantId = IntelligenceContext.tenantId();
        int size = Math.min(Math.max(limit == null ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        long after = cursor == null ? 0L : cursor;
        List<Map<String, Object>> rows = knowledgeMapper.exportSearchChunks(tenantId, after, size);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String title = Rows.str(row, "title");
            String section = Rows.str(row, "section_path");
            if (section != null && !section.isBlank()) {
                title = (title == null ? "" : title) + " · " + section;
            }
            items.add(doc("ai-chunk:" + Rows.lngVal(row, "chunk_id"),
                    title,
                    Rows.str(row, "content"),
                    Rows.intVal(row, "sec_level")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("next_cursor", rows.isEmpty() ? after : Rows.lngVal(rows.get(rows.size() - 1), "chunk_id"));
        result.put("has_more", rows.size() == size);
        return ApiResponse.success(result);
    }

    private Map<String, Object> doc(String docId, String title, String content, int secretLevel) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("doc_id", docId);
        doc.put("title", title == null || title.isBlank() ? docId : title);
        doc.put("content", content == null ? "" : content);
        doc.put("secret_level", secretLevel);
        return doc;
    }
}
