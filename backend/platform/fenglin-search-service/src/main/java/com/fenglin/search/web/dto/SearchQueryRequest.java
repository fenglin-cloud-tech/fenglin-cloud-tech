package com.fenglin.search.web.dto;

import java.util.List;

/**
 * HUB-API-901 统一检索请求。mode 支持 BM25/HYBRID，P2 仅实现关键词检索。
 */
public record SearchQueryRequest(
        String query,
        List<String> domains,
        String mode,
        Integer page,
        Integer size) {
}
