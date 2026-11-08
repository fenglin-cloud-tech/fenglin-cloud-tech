package com.fenglin.search.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.search.mapper.SearchQueryLogMapper;
import com.fenglin.search.web.dto.AnalyticsItem;
import com.fenglin.search.web.dto.AnalyticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HUB-API-904 检索分析报表：HOT / ZERO_RESULT / ZERO_CLICK。
 */
@Service
public class SearchAnalyticsService {

    private static final Set<String> TYPES = Set.of("HOT", "ZERO_RESULT", "ZERO_CLICK");
    private static final int LIMIT = 20;

    private final SearchQueryLogMapper queryLogMapper;

    public SearchAnalyticsService(SearchQueryLogMapper queryLogMapper) {
        this.queryLogMapper = queryLogMapper;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse report(String type, Instant from, Instant to) {
        String normalized = type == null || type.isBlank() ? "HOT" : type.trim().toUpperCase();
        if (!TYPES.contains(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的报表类型: " + type);
        }
        Instant start = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = to;
        List<Map<String, Object>> rows = switch (normalized) {
            case "ZERO_RESULT" -> queryLogMapper.zeroResultKeywords(start, end, LIMIT);
            case "ZERO_CLICK" -> queryLogMapper.zeroClickKeywords(start, end, LIMIT);
            default -> queryLogMapper.hotKeywords(start, end, LIMIT);
        };
        List<AnalyticsItem> items = rows.stream()
                .map(row -> new AnalyticsItem((String) row.get("keyword"), ((Number) row.get("cnt")).longValue()))
                .toList();
        return new AnalyticsResponse(normalized, items);
    }
}
