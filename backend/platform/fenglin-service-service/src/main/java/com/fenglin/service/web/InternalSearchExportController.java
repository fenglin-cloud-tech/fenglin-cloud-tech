package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PLAT-26 一期：云服域内部检索导出。
 * 只暴露检索所需字段（doc_id/title/content/secret_level），租户内只读、游标分页；
 * 网关只路由 /api/service/v1/**，本接口供 search-service 内部拉取。
 */
@RestController
public class InternalSearchExportController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final ConfigMapper configMapper;

    public InternalSearchExportController(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @GetMapping("/internal/search-export")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> export(
            @RequestParam(defaultValue = "catalog") String type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "200") Integer limit) {
        long tenantId = ServiceContext.tenantId();
        int size = Math.min(Math.max(limit == null ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        long after = cursor == null ? 0L : cursor;
        List<Map<String, Object>> rows;
        List<Map<String, Object>> items = new ArrayList<>();
        switch (type) {
            case "catalog" -> {
                rows = configMapper.exportCatalogItems(tenantId, after, size);
                for (Map<String, Object> row : rows) {
                    String code = Rows.str(row, "code");
                    String description = Rows.str(row, "description");
                    StringBuilder content = new StringBuilder();
                    if (code != null) {
                        content.append(code);
                    }
                    if (description != null) {
                        if (content.length() > 0) {
                            content.append(' ');
                        }
                        content.append(description);
                    }
                    items.add(doc("svc-catalog:" + Rows.lngVal(row, "id"), Rows.str(row, "name"), content.toString()));
                }
            }
            case "kb" -> {
                rows = configMapper.exportKbArticles(tenantId, after, size);
                for (Map<String, Object> row : rows) {
                    String summary = Rows.str(row, "summary");
                    String content = Rows.str(row, "content");
                    String merged = ((summary == null ? "" : summary) + "\n"
                            + (content == null ? "" : content)).strip();
                    items.add(doc("svc-kb:" + Rows.lngVal(row, "id"), Rows.str(row, "title"), merged));
                }
            }
            default -> throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的导出类型: " + type);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("next_cursor", rows.isEmpty() ? after : Rows.lngVal(rows.get(rows.size() - 1), "id"));
        result.put("has_more", rows.size() == size);
        return ApiResponse.success(result);
    }

    private Map<String, Object> doc(String docId, String title, String content) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("doc_id", docId);
        doc.put("title", title == null ? docId : title);
        doc.put("content", content == null ? "" : content);
        doc.put("secret_level", 0);
        return doc;
    }
}
