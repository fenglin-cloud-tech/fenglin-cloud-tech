package com.fenglin.intelligence.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.core.RequestContext;
import com.fenglin.intelligence.IntelligenceErrorCode;
import com.fenglin.intelligence.client.WorkflowClient;
import com.fenglin.intelligence.mapper.CommonsMapper;
import com.fenglin.intelligence.mapper.KnowledgeMapper;
import com.fenglin.intelligence.support.IntelligenceAudit;
import com.fenglin.intelligence.support.IntelligenceContext;
import com.fenglin.intelligence.support.IntelligenceCrypto;
import com.fenglin.intelligence.support.IntelligenceIdempotency;
import com.fenglin.intelligence.support.IntelligenceMaps;
import com.fenglin.intelligence.support.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H1 知识主链：知识库、数据源、文档、审核、索引、混合检索、图谱、检索分析。
 * 说明：向量化（embedding）与 ES 写入属于平台 AI 中台/部署期同步任务；本域保存权威分块并做词法召回，
 * 未接入平台嵌入服务时检索结果标记 degraded=true，不重复实现模型调用。
 */
@Service
@Transactional
public class KnowledgeService {

    private final KnowledgeMapper mapper;
    private final CommonsMapper commonsMapper;
    private final IntelligenceCrypto crypto;
    private final IntelligenceAudit audit;
    private final IntelligenceIdempotency idempotency;
    private final WorkflowClient workflowClient;

    public KnowledgeService(KnowledgeMapper mapper, CommonsMapper commonsMapper, IntelligenceCrypto crypto,
                            IntelligenceAudit audit, IntelligenceIdempotency idempotency,
                            WorkflowClient workflowClient) {
        this.mapper = mapper;
        this.commonsMapper = commonsMapper;
        this.crypto = crypto;
        this.audit = audit;
        this.idempotency = idempotency;
        this.workflowClient = workflowClient;
    }

    @Transactional
    public Map<String, Object> createKnowledgeBase(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        String name = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "name")));
        if (mapper.countKnowledgeBaseCode(tenantId, code) > 0) {
            throw new BizException(IntelligenceErrorCode.CODE_EXISTS);
        }
        int secLevel = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(body, "default_sec_level", "defaultSecLevel"), 2),
                1, 4);
        long id = IdWorker.getId();
        mapper.insertKnowledgeBase(id, tenantId, code, name,
                IntelligenceContext.string(IntelligenceContext.first(body, "description")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "scope_type", "scopeType")),
                        "TENANT"),
                secLevel,
                defaultValue(IntelligenceContext.string(
                        IntelligenceContext.first(body, "embedding_model", "embeddingModel")), "bge-large-zh"),
                "ai_kb_" + id, "ai_kb_" + id, userId);
        audit.low("kb.create", "knowledge_base", id);
        return IntelligenceMaps.parse(mapper.findKnowledgeBase(tenantId, id));
    }

    public PageResult<Map<String, Object>> listKnowledgeBases(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        List<Map<String, Object>> rows = mapper.listKnowledgeBases(tenantId,
                IntelligenceContext.size(size), IntelligenceContext.offset(page, size));
        return PageResult.of(rows, mapper.countKnowledgeBases(tenantId),
                IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> createDataSource(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "code")));
        String name = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "name")));
        if (mapper.countDataSourceCode(tenantId, code) > 0) {
            throw new BizException(IntelligenceErrorCode.CODE_EXISTS);
        }
        long id = IdWorker.getId();
        mapper.insertDataSource(id, tenantId, code, name,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "category")), "FILE"),
                defaultValue(IntelligenceContext.string(
                        IntelligenceContext.first(body, "connector_type", "connectorType")), "LOCAL_FILE"),
                IntelligenceContext.string(IntelligenceContext.first(body, "endpoint")),
                IntelligenceMaps.json(IntelligenceContext.first(body, "config", "config_json")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "sync_mode", "syncMode")),
                        "FULL"),
                IntelligenceContext.string(IntelligenceContext.first(body, "sync_cron", "syncCron")), userId);
        String secret = IntelligenceContext.string(
                IntelligenceContext.first(body, "credential_secret", "credentialSecret"));
        if (secret != null) {
            mapper.insertDataSourceCredential(IdWorker.getId(), tenantId, id,
                    crypto.encrypt(secret, "ai_ds:" + id), crypto.hash(secret), userId);
        }
        audit.high("datasource.create", "data_source", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("code", code);
        result.put("has_credential", secret != null);
        return result;
    }

    public PageResult<Map<String, Object>> listDataSources(Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(mapper.listDataSources(tenantId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                mapper.countDataSources(tenantId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    @Transactional
    public Map<String, Object> createDocument(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long kbId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "knowledge_base_id", "kbId"));
        Map<String, Object> kb = mapper.findKnowledgeBase(tenantId, kbId);
        if (kb == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        String title = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "title")));
        int secLevel = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(body, "sec_level", "secLevel"),
                        (int) Rows.lngVal(kb, "default_sec_level")), 1, 4);
        String content = IntelligenceContext.string(IntelligenceContext.first(body, "content"));
        long id = IdWorker.getId();
        mapper.insertDocument(id, tenantId, kbId,
                IntelligenceContext.longValue(IntelligenceContext.first(body, "data_source_id", "dataSourceId")),
                title,
                IntelligenceContext.string(IntelligenceContext.first(body, "source_uri", "sourceUri")),
                IntelligenceContext.string(IntelligenceContext.first(body, "file_id", "fileId")),
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "doc_type", "docType")), "TXT"),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "size_bytes", "sizeBytes")),
                secLevel,
                IntelligenceContext.longValue(IntelligenceContext.first(body, "owner_dept_id", "ownerDeptId")),
                IntelligenceContext.string(IntelligenceContext.first(body, "tags")),
                content == null ? null : crypto.hash(content), userId);
        int chunks = storeChunks(tenantId, userId, id, kbId, secLevel, content);
        audit.low("document.create", "document", id);
        Map<String, Object> result = IntelligenceMaps.parse(mapper.findDocument(tenantId, id));
        result.put("parsed_chunks", chunks);
        return result;
    }

    private int storeChunks(long tenantId, long userId, long docId, long kbId, int secLevel, String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        List<String> parts = split(content);
        int seq = 0;
        int offset = 0;
        for (String part : parts) {
            seq++;
            mapper.insertChunk(IdWorker.getId(), tenantId, docId, kbId, seq, part,
                    Math.max(1, part.length() / 2), 1, null, offset, secLevel, userId);
            offset += part.length();
        }
        return seq;
    }

    private List<String> split(String content) {
        List<String> parts = new ArrayList<>();
        String normalized = content.replace("\r\n", "\n");
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String text = paragraph.strip();
            if (text.isEmpty()) {
                continue;
            }
            while (text.length() > 800) {
                parts.add(text.substring(0, 800));
                text = text.substring(800);
            }
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
        if (parts.isEmpty() && !normalized.isBlank()) {
            parts.add(normalized.strip());
        }
        return parts;
    }

    public PageResult<Map<String, Object>> listDocuments(long kbId, Integer page, Integer size) {
        long tenantId = IntelligenceContext.tenantId();
        return PageResult.of(mapper.listDocuments(tenantId, kbId, IntelligenceContext.size(size),
                        IntelligenceContext.offset(page, size)),
                mapper.countDocuments(tenantId, kbId), IntelligenceContext.page(page), IntelligenceContext.size(size));
    }

    public Map<String, Object> documentDetail(long docId) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(doc);
        result.put("chunks", mapper.listChunks(tenantId, docId));
        result.put("latest_index_task", mapper.findLatestIndexTask(tenantId, docId));
        return result;
    }

    @Transactional
    public Map<String, Object> submitReview(long docId, String idemKey, String permissions) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        String status = Rows.str(doc, "review_status");
        if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException(IntelligenceErrorCode.REVIEW_STATE_CONFLICT);
        }
        String instanceNo = workflowClient.start(permissions, RequestContext.requestId(), "aiKnowledgeReview",
                String.valueOf(docId), "AI_DOCUMENT", Rows.str(doc, "title"),
                Map.of("knowledge_base_id", Rows.lngVal(doc, "knowledge_base_id"),
                        "doc_type", String.valueOf(Rows.str(doc, "doc_type")),
                        "sec_level", Rows.intVal(doc, "sec_level")));
        mapper.updateDocumentReview(tenantId, docId, "PENDING", userId);
        idempotency.record("/api/intelligence/v1/admin/documents/review:submit", idemKey, docId);
        audit.low("document.submitReview", "document", docId);
        return Map.of("document_id", docId, "instance_no", instanceNo, "review_status", "PENDING");
    }

    @Transactional
    public Map<String, Object> review(long docId, String action) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        if (!"PENDING".equals(Rows.str(doc, "review_status"))) {
            throw new BizException(IntelligenceErrorCode.REVIEW_STATE_CONFLICT);
        }
        String normalized = "APPROVE".equalsIgnoreCase(action) ? "APPROVED"
                : "REJECT".equalsIgnoreCase(action) ? "REJECTED" : null;
        if (normalized == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID);
        }
        mapper.updateDocumentReview(tenantId, docId, normalized, userId);
        Long taskId = null;
        if ("APPROVED".equals(normalized)) {
            taskId = IdWorker.getId();
            mapper.insertIndexTask(taskId, tenantId, docId, Rows.lngVal(doc, "knowledge_base_id"), "FULL", userId);
        }
        audit.high("document.review." + normalized.toLowerCase(), "document", docId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document_id", docId);
        result.put("review_status", normalized);
        result.put("index_task_id", taskId);
        return result;
    }

    @Transactional
    public Map<String, Object> index(long docId) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        if (!"APPROVED".equals(Rows.str(doc, "review_status"))) {
            throw new BizException(IntelligenceErrorCode.REVIEW_STATE_CONFLICT);
        }
        if (mapper.listChunks(tenantId, docId).isEmpty()) {
            throw new BizException(IntelligenceErrorCode.DOC_PARSE_FAILED);
        }
        long taskId = IdWorker.getId();
        mapper.insertIndexTask(taskId, tenantId, docId, Rows.lngVal(doc, "knowledge_base_id"), "FULL", userId);
        mapper.updateIndexTask(tenantId, taskId, "RUNNING", 10, null, userId);
        mapper.updateDocumentIndex(tenantId, docId, "INDEXING", 0, userId);
        int chunks = mapper.listChunks(tenantId, docId).size();
        mapper.updateIndexTask(tenantId, taskId, "SUCCESS", 100, null, userId);
        mapper.updateDocumentIndex(tenantId, docId, "INDEXED", chunks, userId);
        audit.low("document.index", "document", docId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("document_id", docId);
        result.put("status", "SUCCESS");
        result.put("chunk_count", chunks);
        result.put("derived_index", "ES/向量库由部署期同步任务写入，PG 为权威源");
        return result;
    }

    @Transactional
    public Map<String, Object> search(Map<String, Object> body, boolean secure) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String query = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "query", "q")));
        if (query.length() > 200) {
            throw new BizException(IntelligenceErrorCode.SEARCH_PARAM_INVALID);
        }
        int topK = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(body, "top_k", "topK"), 10), 1, 50);
        int secLevel = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(body, "sec_level", "secLevel"), secure ? 4 : 2),
                1, 4);
        String queryType = defaultValue(IntelligenceContext.string(
                IntelligenceContext.first(body, "query_type", "queryType")), "HYBRID");
        String kbCsv = kbCsv(IntelligenceContext.first(body, "kb_ids", "kbIds"));
        long start = System.nanoTime();
        List<Map<String, Object>> hits = mapper.searchChunks(tenantId, kbCsv, query, secLevel, topK);
        int latency = (int) ((System.nanoTime() - start) / 1_000_000);
        BigDecimal topScore = hits.isEmpty() ? null
                : Rows.dec(hits.get(0), "score") == null ? null
                : Rows.dec(hits.get(0), "score").setScale(4, RoundingMode.HALF_UP);
        mapper.insertSearchLog(IdWorker.getId(), tenantId, userId, query, queryType, kbCsv, hits.size(), topScore,
                latency, IntelligenceContext.clientType());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("query_type", queryType);
        result.put("secure", secure);
        result.put("sec_level", secLevel);
        result.put("degraded", !"KEYWORD".equals(queryType));
        result.put("degrade_reason", "向量召回需平台 AI 中台嵌入服务，未配置时降级为词法召回");
        result.put("latency_ms", latency);
        result.put("total", hits.size());
        result.put("hits", hits);
        return result;
    }

    public Map<String, Object> preview(long docId, int secLevel) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null || Rows.intVal(doc, "sec_level") > IntelligenceContext.clamp(secLevel, 1, 4)) {
            throw new BizException(IntelligenceErrorCode.SEARCH_FORBIDDEN);
        }
        Map<String, Object> result = new LinkedHashMap<>(doc);
        result.put("chunks", mapper.listChunks(tenantId, docId));
        return result;
    }

    @Transactional
    public Map<String, Object> createKgEntity(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long kbId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "knowledge_base_id", "kbId"));
        String name = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "name")));
        long id = IdWorker.getId();
        mapper.insertKgEntity(id, tenantId, kbId,
                defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "entity_type", "entityType")),
                        "CONCEPT"),
                name, name.toLowerCase(),
                IntelligenceContext.string(IntelligenceContext.first(body, "aliases")),
                IntelligenceContext.longValue(IntelligenceContext.first(body, "source_document_id", "sourceDocumentId")),
                BigDecimal.valueOf(0.9), userId);
        audit.low("kg.entity.create", "kg_entity", id);
        return Map.of("id", id, "name", name);
    }

    @Transactional
    public Map<String, Object> createKgRelation(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        long kbId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "knowledge_base_id", "kbId"));
        long sourceId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "source_entity_id", "sourceEntityId"));
        long targetId = IntelligenceContext.requireLong(
                IntelligenceContext.first(body, "target_entity_id", "targetEntityId"));
        String type = IntelligenceContext.requireText(
                IntelligenceContext.string(IntelligenceContext.first(body, "relation_type", "relationType")));
        long id = IdWorker.getId();
        mapper.insertKgRelation(id, tenantId, kbId, sourceId, targetId, type, BigDecimal.ONE,
                IntelligenceContext.longValue(IntelligenceContext.first(body, "source_document_id", "sourceDocumentId")),
                BigDecimal.valueOf(0.9), userId);
        audit.low("kg.relation.create", "kg_relation", id);
        return Map.of("id", id, "source_entity_id", sourceId, "target_entity_id", targetId);
    }

    public List<Map<String, Object>> listKgEntities(Long kbId, String keyword, Integer limit) {
        return mapper.listKgEntities(IntelligenceContext.tenantId(), kbId, IntelligenceContext.text(keyword),
                IntelligenceContext.clamp(limit == null ? 20 : limit, 1, 200));
    }

    public Map<String, Object> kgPath(long fromId, long toId, Integer maxDepth) {
        Map<String, Object> path = mapper.findKgPath(IntelligenceContext.tenantId(), fromId, toId,
                IntelligenceContext.clamp(maxDepth == null ? 3 : maxDepth, 1, 5));
        if (path == null) {
            return Map.of("found", false, "from", fromId, "to", toId);
        }
        Map<String, Object> result = new LinkedHashMap<>(path);
        result.put("found", true);
        result.put("from", fromId);
        result.put("to", toId);
        return result;
    }

    public Map<String, Object> searchAnalytics(Integer limit) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> summary = mapper.searchSummary(tenantId);
        result.put("total", summary == null ? 0 : Rows.lngVal(summary, "total"));
        result.put("avg_latency_ms", summary == null ? 0 : Rows.intVal(summary, "avg_latency"));
        result.put("zero_result", summary == null ? 0 : Rows.intVal(summary, "zero_result"));
        result.put("top_queries", mapper.topQueries(tenantId, IntelligenceContext.clamp(limit == null ? 10 : limit, 1, 50)));
        return result;
    }

    private String kbCsv(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return IntelligenceContext.csv(iterable);
        }
        if (value instanceof Object[] array) {
            return IntelligenceContext.csv(List.of(array));
        }
        return IntelligenceContext.text(value == null ? null : String.valueOf(value));
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
