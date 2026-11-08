package com.fenglin.ai.service;

import com.fenglin.ai.client.SearchServiceClient;
import com.fenglin.ai.client.SearchServiceClient.RetrievedDoc;
import com.fenglin.ai.web.dto.AiDtos.HybridRecord;
import com.fenglin.ai.web.dto.AiDtos.HybridSearchRequest;
import com.fenglin.ai.web.dto.AiDtos.HybridSearchResult;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUB-API-1304 向量/混合检索。
 * 契约缺口：平台无独立向量库，vector 通道复用检索服务 HYBRID 模式（ES 兜底）；
 * kbIds 在 ES 兜底路径下无对应物理概念，当前仅透传、不参与过滤（已在 P3 收口标注）。
 */
@Service
public class HybridSearchService {

    private static final int RRF_K = 60;

    private final SearchServiceClient searchClient;

    public HybridSearchService(SearchServiceClient searchClient) {
        this.searchClient = searchClient;
    }

    public HybridSearchResult search(long tenantId, Long userId, int secretLevel,
                                     HybridSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "query 不能为空");
        }
        String query = request.query().trim();
        int topK = request.topK() == null ? 20 : Math.min(Math.max(request.topK(), 1), 50);
        double fulltextWeight = clamp(request.fulltextWeight() == null ? 0.4 : request.fulltextWeight());
        double vectorWeight = clamp(request.vectorWeight() == null ? 0.6 : request.vectorWeight());
        boolean rerank = request.rerank() == null || request.rerank();

        long start = System.nanoTime();
        List<RetrievedDoc> fulltext = searchClient.search(tenantId, userId, query, "BM25", topK, secretLevel);
        List<RetrievedDoc> vector = searchClient.search(tenantId, userId, query, "HYBRID", topK, secretLevel);

        Map<String, RetrievedDoc> docs = new LinkedHashMap<>();
        Map<String, Double> fused = new LinkedHashMap<>();
        accumulate(fulltext, fulltextWeight, docs, fused);
        accumulate(vector, vectorWeight, docs, fused);
        double max = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);

        List<HybridRecord> records = new ArrayList<>();
        for (Map.Entry<String, Double> entry : fused.entrySet()) {
            RetrievedDoc doc = docs.get(entry.getKey());
            double score = max <= 0 ? 0d : entry.getValue() / max;
            records.add(new HybridRecord(entry.getKey(), Math.round(score * 10000d) / 10000d,
                    "hybrid", doc.title()));
        }
        if (rerank) {
            records.sort(Comparator.comparingDouble(HybridRecord::score).reversed());
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new HybridSearchResult(records, "RRF", latencyMs);
    }

    private void accumulate(List<RetrievedDoc> docs, double weight,
                            Map<String, RetrievedDoc> docMap, Map<String, Double> fused) {
        for (int i = 0; i < docs.size(); i++) {
            RetrievedDoc doc = docs.get(i);
            String key = doc.docId() == null ? doc.title() : doc.docId();
            if (key == null) {
                continue;
            }
            docMap.putIfAbsent(key, doc);
            fused.merge(key, weight / (RRF_K + i + 1), Double::sum);
        }
    }

    private double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 1);
    }
}
