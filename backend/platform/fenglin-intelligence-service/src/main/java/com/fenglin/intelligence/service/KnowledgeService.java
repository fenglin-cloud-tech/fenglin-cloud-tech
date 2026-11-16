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
import java.util.Set;

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
        long userId = IntelligenceContext.userId();
        List<Map<String, Object>> rows = mapper.listKnowledgeBases(tenantId, userId,
                IntelligenceContext.size(size), IntelligenceContext.offset(page, size));
        return PageResult.of(rows, mapper.countKnowledgeBases(tenantId, userId),
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
        List<Map<String, Object>> hits = mapper.searchChunks(tenantId, userId, kbCsv, query, secLevel, topK);
        for (Map<String, Object> hit : hits) {
            hit.put("highlight", highlight(Rows.str(hit, "content"), query));
        }
        int latency = (int) ((System.nanoTime() - start) / 1_000_000);
        BigDecimal topScore = hits.isEmpty() ? null
                : Rows.dec(hits.get(0), "score") == null ? null
                : Rows.dec(hits.get(0), "score").setScale(4, RoundingMode.HALF_UP);
        mapper.insertSearchLog(IdWorker.getId(), tenantId, userId, query, queryType, kbCsv, hits.size(), topScore,
                latency, IntelligenceContext.clientType());
        Map<String, Object> facets = new LinkedHashMap<>();
        for (Map<String, Object> facet : mapper.searchFacets(tenantId, userId, kbCsv, query, secLevel)) {
            String key = Rows.str(facet, "facet");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bucket = (List<Map<String, Object>>) facets.computeIfAbsent(key,
                    k -> new ArrayList<Map<String, Object>>());
            bucket.add(Map.of("value", Rows.str(facet, "value") == null ? "" : Rows.str(facet, "value"),
                    "count", Rows.lngVal(facet, "total")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("query_type", queryType);
        result.put("secure", secure);
        result.put("sec_level", secLevel);
        result.put("degraded", !"KEYWORD".equals(queryType));
        result.put("degrade_reason", "向量召回需平台 AI 中台嵌入服务，未配置时降级为词法召回");
        result.put("latency_ms", latency);
        result.put("total", hits.size());
        result.put("facets", facets);
        result.put("hits", hits);
        return result;
    }

    private String highlight(String content, String query) {
        if (content == null) {
            return null;
        }
        String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (query == null || query.isBlank()) {
            return escaped;
        }
        String lower = escaped.toLowerCase();
        String needle = query.toLowerCase();
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        int index;
        while ((index = lower.indexOf(needle, cursor)) >= 0) {
            builder.append(escaped, cursor, index).append("<em>")
                    .append(escaped, index, index + needle.length()).append("</em>");
            cursor = index + needle.length();
        }
        builder.append(escaped.substring(cursor));
        return builder.toString();
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

    public Map<String, Object> searchAnalytics(Integer limit, String from, String to) {
        long tenantId = IntelligenceContext.tenantId();
        String fromText = IntelligenceContext.text(from);
        String toText = IntelligenceContext.text(to);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> summary = mapper.searchSummary(tenantId, fromText, toText);
        result.put("total", summary == null ? 0 : Rows.lngVal(summary, "total"));
        result.put("avg_latency_ms", summary == null ? 0 : Rows.intVal(summary, "avg_latency"));
        result.put("zero_result", summary == null ? 0 : Rows.intVal(summary, "zero_result"));
        result.put("from", fromText);
        result.put("to", toText);
        result.put("top_queries",
                mapper.topQueries(tenantId, fromText, toText,
                        IntelligenceContext.clamp(limit == null ? 10 : limit, 1, 50)));
        return result;
    }

    // ---------- 知识库 ACL ----------

    public List<Map<String, Object>> listAcl(long kbId) {
        long tenantId = IntelligenceContext.tenantId();
        requireKnowledgeBase(tenantId, kbId);
        return mapper.listAcl(tenantId, kbId);
    }

    @Transactional
    public Map<String, Object> saveAcl(long kbId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        requireKnowledgeBase(tenantId, kbId);
        Object rawItems = IntelligenceContext.first(body, "items");
        if (!(rawItems instanceof List<?> items)) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "items 必须为数组");
        }
        mapper.deleteAclByKb(tenantId, kbId, userId);
        int saved = 0;
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> item)) {
                throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "items 元素必须为对象");
            }
            Object rawSubjectType = item.get("subjectType") == null ? item.get("subject_type") : item.get("subjectType");
            if (rawSubjectType == null) {
                throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "subjectType 不能为空");
            }
            String subjectType = IntelligenceContext.requireText(String.valueOf(rawSubjectType));
            Object subjectId = item.get("subjectId") == null ? item.get("subject_id") : item.get("subjectId");
            Object rawPermission = item.get("permission");
            String permission = rawPermission == null || String.valueOf(rawPermission).isBlank()
                    ? "READ" : String.valueOf(rawPermission).strip().toUpperCase();
            Object secLevelMax = item.get("secLevelMax") == null ? item.get("sec_level_max") : item.get("secLevelMax");
            mapper.insertAcl(IdWorker.getId(), tenantId, kbId, subjectType,
                    subjectId == null ? null : Long.parseLong(String.valueOf(subjectId)), permission,
                    IntelligenceContext.clamp(IntelligenceContext.intValue(secLevelMax, 2), 1, 4), userId);
            saved++;
        }
        audit.high("kb.acl.save", "knowledge_base", kbId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledge_base_id", kbId);
        result.put("saved", saved);
        result.put("items", mapper.listAcl(tenantId, kbId));
        return result;
    }

    // ---------- 个人知识库 ----------

    @Transactional
    public Map<String, Object> createPersonalKnowledgeBase(Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        String code = "kb-personal-" + userId;
        Map<String, Object> existing = mapper.findKnowledgeBaseByCode(tenantId, code);
        if (existing != null) {
            return existing;
        }
        String name = defaultValue(IntelligenceContext.string(IntelligenceContext.first(body, "name")),
                "个人知识库");
        long id = IdWorker.getId();
        mapper.insertKnowledgeBase(id, tenantId, code, name, "个人知识（仅本人可见）", "PRIVATE", 2,
                "bge-large-zh", "ai_kb_" + id, "ai_kb_" + id, userId);
        audit.low("kb.personal.create", "knowledge_base", id);
        return IntelligenceMaps.parse(mapper.findKnowledgeBase(tenantId, id));
    }

    public List<Map<String, Object>> listPersonalKnowledgeBases() {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        List<Map<String, Object>> rows = mapper.listKnowledgeBases(tenantId, userId, 100, 0);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if ("PRIVATE".equals(Rows.str(row, "scope_type"))) {
                result.add(row);
            }
        }
        return result;
    }

    // ---------- 文档解析 / 上传 ----------

    @Transactional
    public Map<String, Object> parseDocument(long docId, Map<String, Object> body) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> doc = mapper.findDocument(tenantId, docId);
        if (doc == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        String content = IntelligenceContext.string(IntelligenceContext.first(body, "content"));
        if (content == null || content.isBlank()) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID,
                    "缺少可解析正文：本地上传仅保存分块，重新解析需在 content 中提交正文");
        }
        mapper.deleteChunks(tenantId, docId, userId);
        int chunks = storeChunks(tenantId, userId, docId, Rows.lngVal(doc, "knowledge_base_id"),
                Rows.intVal(doc, "sec_level"), content);
        mapper.updateDocumentChunkCount(tenantId, docId, chunks, userId);
        mapper.updateDocumentIndex(tenantId, docId, "PENDING", chunks, userId);
        audit.low("document.parse", "document", docId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document_id", docId);
        result.put("parsed_chunks", chunks);
        result.put("index_status", "PENDING");
        return result;
    }

    @Transactional
    public Map<String, Object> uploadDocument(String filename, byte[] bytes, Map<String, Object> form) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (bytes == null || bytes.length == 0) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "上传文件为空");
        }
        if (bytes.length > 10 * 1024 * 1024) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "单个文件不得超过 10MB");
        }
        String safeName = filename == null || filename.isBlank() ? "上传文档.txt" : filename;
        String docType = docTypeOf(safeName);
        if (!Set.of("TXT", "MD", "EXCEL").contains(docType)) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID,
                    "当前部署未接入 " + docType + " 解析器，仅支持 txt/md/csv 文本文件上传");
        }
        long kbId = IntelligenceContext.requireLong(IntelligenceContext.first(form, "knowledge_base_id", "kbId"));
        Map<String, Object> kb = mapper.findKnowledgeBase(tenantId, kbId);
        if (kb == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String title = defaultValue(IntelligenceContext.string(IntelligenceContext.first(form, "title")),
                safeName.contains(".") ? safeName.substring(0, safeName.lastIndexOf('.')) : safeName);
        int secLevel = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(form, "sec_level", "secLevel"),
                        Rows.intVal(kb, "default_sec_level")), 1, 4);
        String checksum = sha256(bytes);
        long id = IdWorker.getId();
        mapper.insertDocument(id, tenantId, kbId,
                IntelligenceContext.longValue(IntelligenceContext.first(form, "data_source_id", "dataSourceId")),
                title, null, checksum, docType, (long) bytes.length, secLevel,
                IntelligenceContext.longValue(IntelligenceContext.first(form, "owner_dept_id", "ownerDeptId")),
                IntelligenceContext.string(IntelligenceContext.first(form, "tags")), checksum, userId);
        int chunks = storeChunks(tenantId, userId, id, kbId, secLevel, content);
        mapper.updateDocumentChunkCount(tenantId, id, chunks, userId);
        audit.high("document.upload", "document", id);
        Map<String, Object> result = IntelligenceMaps.parse(mapper.findDocument(tenantId, id));
        result.put("parsed_chunks", chunks);
        result.put("file_name", safeName);
        return result;
    }

    private String docTypeOf(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "MD";
        }
        if (lower.endsWith(".csv") || lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "EXCEL";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".json")) {
            return "TXT";
        }
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "WORD";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "PPT";
        }
        return "TXT";
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "文件摘要计算失败");
        }
    }

    // ---------- 数据源连通性测试 / 手动同步 ----------

    public Map<String, Object> testDataSource(long id) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> source = mapper.findDataSourceDetail(tenantId, id);
        if (source == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据源不存在");
        }
        String connector = Rows.str(source, "connector_type");
        connector = connector == null || connector.isBlank() ? "UNKNOWN" : connector;
        String endpoint = Rows.str(source, "endpoint");
        long start = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data_source_id", id);
        result.put("connector_type", connector);
        result.put("endpoint", endpoint);
        try {
            if ("LOCAL_FILE".equals(connector) || "FILE".equals(connector)) {
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException("未配置本地目录 endpoint");
                }
                java.nio.file.Path path = java.nio.file.Path.of(endpoint);
                boolean exists = java.nio.file.Files.exists(path);
                boolean readable = exists && java.nio.file.Files.isReadable(path);
                result.put("reachable", readable);
                result.put("detail", exists ? "路径存在，" + (readable ? "可读" : "不可读") : "路径不存在");
            } else if ("HTTP".equals(connector) || "API".equals(connector) || "HTTP_API".equals(connector)) {
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException("未配置 HTTP endpoint");
                }
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(3)).build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest
                        .newBuilder(java.net.URI.create(endpoint))
                        .timeout(java.time.Duration.ofSeconds(3))
                        .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build();
                int status = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
                result.put("reachable", status > 0 && status < 500);
                result.put("detail", "HTTP " + status);
            } else if ("DB".equals(connector) || "JDBC".equals(connector)) {
                String jdbcUrl = endpoint;
                if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
                    throw new IllegalArgumentException("未配置 jdbc: 连接串");
                }
                java.sql.DriverManager.setLoginTimeout(3);
                try (java.sql.Connection ignored = java.sql.DriverManager.getConnection(jdbcUrl)) {
                    result.put("reachable", true);
                    result.put("detail", "JDBC 连接成功");
                }
            } else {
                result.put("reachable", false);
                result.put("detail", "连接器 " + connector + " 需部署期专用探针，当前不支持在线测试");
            }
        } catch (Exception ex) {
            result.put("reachable", false);
            result.put("detail", ex.getMessage());
        }
        result.put("latency_ms", (int) ((System.nanoTime() - start) / 1_000_000));
        result.put("tested_at", java.time.OffsetDateTime.now().toString());
        return result;
    }

    @Transactional
    public Map<String, Object> syncDataSource(long id) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        Map<String, Object> source = mapper.findDataSourceDetail(tenantId, id);
        if (source == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据源不存在");
        }
        List<Map<String, Object>> documents = mapper.listApprovedDocumentsBySource(tenantId, id);
        List<Map<String, Object>> tasks = new ArrayList<>();
        int indexed = 0;
        for (Map<String, Object> doc : documents) {
            long docId = Rows.lngVal(doc, "id");
            int chunks = mapper.listChunks(tenantId, docId).size();
            if (chunks == 0) {
                tasks.add(Map.of("document_id", docId, "status", "SKIPPED", "reason", "无分块"));
                continue;
            }
            long taskId = IdWorker.getId();
            mapper.insertIndexTask(taskId, tenantId, docId,
                    Rows.lngVal(mapper.findDocument(tenantId, docId), "knowledge_base_id"), "FULL", userId);
            mapper.updateIndexTask(tenantId, taskId, "SUCCESS", 100, null, userId);
            mapper.updateDocumentIndex(tenantId, docId, "INDEXED", chunks, userId);
            tasks.add(Map.of("document_id", docId, "task_id", taskId, "status", "SUCCESS",
                    "chunk_count", chunks));
            indexed++;
        }
        audit.high("datasource.sync", "data_source", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data_source_id", id);
        result.put("sync_mode", Rows.str(source, "sync_mode"));
        result.put("document_count", documents.size());
        result.put("indexed", indexed);
        result.put("tasks", tasks);
        return result;
    }

    // ---------- 图谱图数据 / 详情 / 删除 ----------

    public Map<String, Object> kgGraph(Long kbId, Long entityId, Integer depth) {
        long tenantId = IntelligenceContext.tenantId();
        List<Map<String, Object>> nodes = entityId == null
                ? mapper.listKgEntities(tenantId, kbId, null, 300)
                : mapper.listKgNeighborEntities(tenantId, entityId,
                        IntelligenceContext.clamp(depth == null ? 2 : depth, 1, 4));
        java.util.Set<Long> nodeIds = new java.util.HashSet<>();
        for (Map<String, Object> node : nodes) {
            nodeIds.add(Rows.lngVal(node, "id"));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> relation : mapper.listKgRelations(tenantId, kbId, 500)) {
            long source = Rows.lngVal(relation, "source_entity_id");
            long target = Rows.lngVal(relation, "target_entity_id");
            if (nodeIds.contains(source) && nodeIds.contains(target)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", Rows.lngVal(relation, "id"));
                edge.put("source", source);
                edge.put("target", target);
                edge.put("relation_type", Rows.str(relation, "relation_type"));
                edge.put("weight", Rows.dec(relation, "weight"));
                edges.add(edge);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("node_count", nodes.size());
        result.put("edge_count", edges.size());
        return result;
    }

    public Map<String, Object> kgEntityDetail(long id) {
        long tenantId = IntelligenceContext.tenantId();
        Map<String, Object> entity = mapper.findKgEntity(tenantId, id);
        if (entity == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图谱实体不存在");
        }
        List<Map<String, Object>> relations = new ArrayList<>();
        for (Map<String, Object> relation : mapper.listKgEntityRelations(tenantId, id)) {
            Map<String, Object> item = new LinkedHashMap<>(relation);
            Map<String, Object> source = mapper.findKgEntity(tenantId, Rows.lngVal(relation, "source_entity_id"));
            Map<String, Object> target = mapper.findKgEntity(tenantId, Rows.lngVal(relation, "target_entity_id"));
            item.put("source_name", source == null ? null : Rows.str(source, "name"));
            item.put("target_name", target == null ? null : Rows.str(target, "name"));
            relations.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>(entity);
        result.put("relations", relations);
        return result;
    }

    @Transactional
    public Map<String, Object> deleteKgEntity(long id) {
        long tenantId = IntelligenceContext.tenantId();
        long userId = IntelligenceContext.userId();
        if (mapper.findKgEntity(tenantId, id) == null) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "图谱实体不存在");
        }
        mapper.deleteKgEntity(tenantId, id, userId);
        int relations = mapper.deleteKgEntityRelations(tenantId, id, userId);
        audit.high("kg.entity.delete", "kg_entity", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        result.put("cascade_relations", relations);
        return result;
    }

    // ---------- 检索联想 ----------

    public List<String> suggest(String keyword, Integer limit) {
        long tenantId = IntelligenceContext.tenantId();
        String text = IntelligenceContext.requireText(
                IntelligenceContext.string(keyword));
        int size = IntelligenceContext.clamp(limit == null ? 5 : limit, 1, 20);
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        merged.addAll(mapper.suggestQueries(tenantId, text, size));
        merged.addAll(mapper.suggestDocuments(tenantId, text, size));
        merged.addAll(mapper.suggestEntities(tenantId, text, size));
        List<String> result = new ArrayList<>(merged);
        return result.size() > size ? result.subList(0, size) : result;
    }

    private void requireKnowledgeBase(long tenantId, long kbId) {
        if (mapper.findKnowledgeBase(tenantId, kbId) == null) {
            throw new BizException(IntelligenceErrorCode.KB_NOT_FOUND);
        }
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
