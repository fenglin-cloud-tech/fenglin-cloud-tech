package com.fenglin.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.search.mapper.SearchQueryLogMapper;
import com.fenglin.search.web.dto.SearchGroup;
import com.fenglin.search.web.dto.SearchQueryRequest;
import com.fenglin.search.web.dto.SearchQueryResponse;
import com.fenglin.search.web.dto.SearchRecord;
import com.fenglin.search.web.dto.SuggestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HUB-API-901/902：统一检索与全局建议。
 * 数据权限兜底：强制注入 tenant_id 与 secret_level 过滤；P2 尚无文档级数据权限模型，按租户+密级收敛。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    static final Set<String> DOMAINS = Set.of("hub", "intelligence", "service");
    static final Set<String> MODES = Set.of("BM25", "HYBRID");

    private final ElasticsearchClient client;
    private final SearchQueryLogMapper queryLogMapper;
    private final String indexPrefix;

    public SearchService(ElasticsearchClient client,
                         SearchQueryLogMapper queryLogMapper,
                         @Value("${fenglin.search.index-prefix:hub}") String indexPrefix) {
        this.client = client;
        this.queryLogMapper = queryLogMapper;
        this.indexPrefix = indexPrefix;
    }

    @Transactional
    public SearchQueryResponse query(SearchQueryRequest request, Long userId, int maxSecretLevel) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "检索关键词不能为空");
        }
        List<String> domains = normalizeDomains(request.domains());
        String mode = normalizeMode(request.mode());
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int size = request.size() == null ? 20 : Math.min(Math.max(request.size(), 1), 50);
        String keyword = request.query().trim();
        Long tenantId = TenantContext.requireTenantId();

        long start = System.nanoTime();
        int totalHits = 0;
        List<SearchGroup> groups = new ArrayList<>(domains.size());
        for (String domain : domains) {
            List<SearchRecord> records = new ArrayList<>();
            String index = indexPrefix + "_" + domain;
            if (indexExists(index)) {
                totalHits += searchDomain(index, keyword, tenantId, maxSecretLevel, page, size, records);
            }
            groups.add(new SearchGroup(domain, records));
        }
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
        queryLogMapper.insert(IdWorker.getId(), tenantId, userId, keyword, String.join(",", domains),
                mode, totalHits, latencyMs);
        return new SearchQueryResponse(groups, latencyMs);
    }

    public SuggestResponse suggest(String query, Integer limit, int maxSecretLevel) {
        if (query == null || query.isBlank()) {
            return new SuggestResponse(List.of());
        }
        Long tenantId = TenantContext.requireTenantId();
        int max = limit == null ? 10 : Math.min(Math.max(limit, 1), 20);
        Set<String> suggestions = new LinkedHashSet<>();
        for (String domain : DOMAINS) {
            if (suggestions.size() >= max) {
                break;
            }
            String index = indexPrefix + "_" + domain;
            if (!indexExists(index)) {
                continue;
            }
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .index(index)
                    .size(max);
            String keyword = query.trim();
            builder.query(q -> q.bool(b -> b
                    .must(m -> m.matchPhrasePrefix(mp -> mp.field("title").query(keyword)))
                    .filter(f -> f.term(t -> t.field("tenant_id").value(tenantId)))
                    .filter(f -> f.range(r -> r.field("secret_level").lte(JsonData.of(maxSecretLevel))))));
            try {
                SearchResponse<Map> response = client.search(builder.build(), Map.class);
                for (Hit<Map> hit : response.hits().hits()) {
                    Object title = hit.source() == null ? null : hit.source().get("title");
                    if (title != null) {
                        suggestions.add(String.valueOf(title));
                    }
                    if (suggestions.size() >= max) {
                        break;
                    }
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("检索建议查询失败：index={}", index, ex);
            }
        }
        return new SuggestResponse(suggestions.stream().limit(max).toList());
    }

    private int searchDomain(String index, String keyword, long tenantId, int maxSecretLevel,
                             int page, int size, List<SearchRecord> records) {
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(index)
                .from((page - 1) * size)
                .size(size);
        builder.query(q -> q.bool(b -> b
                .must(m -> m.multiMatch(mm -> mm.query(keyword).fields("title^2", "content")))
                .filter(f -> f.term(t -> t.field("tenant_id").value(tenantId)))
                .filter(f -> f.range(r -> r.field("secret_level").lte(JsonData.of(maxSecretLevel))))));
        builder.highlight(h -> h.fields("title", f -> f).fields("content", f -> f));
        try {
            SearchResponse<Map> response = client.search(builder.build(), Map.class);
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                String highlight = firstHighlight(hit, "title");
                if (highlight == null) {
                    highlight = firstHighlight(hit, "content");
                }
                records.add(new SearchRecord(
                        source == null ? null : String.valueOf(source.get("doc_id")),
                        source == null ? null : String.valueOf(source.get("title")),
                        highlight,
                        hit.score() == null ? 0d : hit.score()));
            }
            return response.hits().total() == null ? records.size() : (int) response.hits().total().value();
        } catch (IOException | RuntimeException ex) {
            log.warn("统一检索失败：index={}", index, ex);
            return 0;
        }
    }

    private String firstHighlight(Hit<Map> hit, String field) {
        if (hit.highlight() == null) {
            return null;
        }
        List<String> fragments = hit.highlight().get(field);
        return fragments == null || fragments.isEmpty() ? null : fragments.get(0);
    }

    private boolean indexExists(String index) {
        try {
            return client.indices().exists(e -> e.index(index)).value();
        } catch (IOException | RuntimeException ex) {
            log.warn("索引存在性检查失败：index={}", index, ex);
            return false;
        }
    }

    String physicalIndex(long tenantId, String indexName) {
        return indexPrefix + "_" + tenantId + "_" + indexName;
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of("hub", "intelligence", "service");
        }
        List<String> normalized = domains.stream().map(domain -> domain == null ? "" : domain.trim().toLowerCase())
                .distinct().toList();
        for (String domain : normalized) {
            if (!DOMAINS.contains(domain)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的检索域: " + domain);
            }
        }
        return normalized;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "BM25";
        }
        String normalized = mode.trim().toUpperCase();
        if (!MODES.contains(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的检索模式: " + mode);
        }
        return normalized;
    }
}
