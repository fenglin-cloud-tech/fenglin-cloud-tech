package com.fenglin.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.search.mapper.SearchIndexTaskMapper;
import com.fenglin.search.web.dto.IndexTaskRequest;
import com.fenglin.search.web.dto.IndexTaskView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HUB-API-903 索引任务：创建 PENDING 任务、异步建/刷新索引并统计文档数。
 */
@Service
public class SearchIndexTaskService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexTaskService.class);
    private static final Pattern INDEX_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final ElasticsearchClient client;
    private final SearchIndexTaskMapper taskMapper;
    private final AuditSink auditSink;
    private final TransactionTemplate transactionTemplate;
    private final String indexPrefix;

    public SearchIndexTaskService(ElasticsearchClient client,
                                  SearchIndexTaskMapper taskMapper,
                                  AuditSink auditSink,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${fenglin.search.index-prefix:hub}") String indexPrefix) {
        this.client = client;
        this.taskMapper = taskMapper;
        this.auditSink = auditSink;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.indexPrefix = indexPrefix;
    }

    @Transactional
    public IndexTaskView create(IndexTaskRequest request, Long operatorId) {
        String indexName = normalizeIndexName(request.indexName());
        String taskType = normalizeTaskType(request.taskType());
        long tenantId = TenantContext.requireTenantId();

        if ("INCREMENTAL".equals(taskType) && !indexExists(physicalIndex(tenantId, indexName))) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "0008001 索引不存在: " + indexName);
        }
        Map<String, Object> active = taskMapper.findActiveTask(tenantId, indexName, taskType);
        if (active != null) {
            return toView(active);
        }
        long id = IdWorker.getId();
        taskMapper.insert(id, tenantId, indexName, taskType, operatorId);
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "SEARCH_INDEX_TASK_CREATED",
                "search", "CREATE", "LOW", tenantId, operatorId, "ADMIN", null,
                "SEARCH_INDEX_TASK", String.valueOf(id), Instant.now()));
        return toView(taskMapper.findById(id));
    }

    @Transactional(readOnly = true)
    public IndexTaskView get(long taskId) {
        Map<String, Object> row = taskMapper.findById(taskId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "索引任务不存在: " + taskId);
        }
        return toView(row);
    }

    /**
     * 由轮询任务执行：FULL 建索引（不存在时）/INCREMENTAL 仅刷新，最后统计文档数。
     */
    public void process(long taskId, long tenantId) {
        TenantContext.set(tenantId, null, "SYSTEM", null, false);
        try {
            Integer started = transactionTemplate.execute(status -> taskMapper.markRunning(taskId));
            if (started == null || started == 0) {
                return;
            }
            try {
                Map<String, Object> task = transactionTemplate.execute(status -> taskMapper.findById(taskId));
                if (task == null) {
                    return;
                }
                String physical = physicalIndex(tenantId, (String) task.get("index_name"));
                if ("FULL".equals(task.get("task_type"))) {
                    ensureIndex(physical);
                }
                client.indices().refresh(r -> r.index(physical));
                long count = client.count(c -> c.index(physical)).count();
                transactionTemplate.executeWithoutResult(status -> taskMapper.markSucceeded(taskId, count));
            } catch (IOException | RuntimeException ex) {
                log.error("索引任务执行失败：taskId={} tenant={}", taskId, tenantId, ex);
                safeMarkFailed(taskId, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void ensureIndex(String index) throws IOException {
        if (client.indices().exists(e -> e.index(index)).value()) {
            return;
        }
        client.indices().create(c -> c.index(index).mappings(m -> m
                .properties("doc_id", p -> p.keyword(k -> k))
                .properties("tenant_id", p -> p.long_(l -> l))
                .properties("title", p -> p.text(t -> t))
                .properties("content", p -> p.text(t -> t))
                .properties("secret_level", p -> p.integer(i -> i))
                .properties("updated_at", p -> p.date(d -> d))));
    }

    private void safeMarkFailed(long taskId, String message) {
        String trimmed = message.length() > 500 ? message.substring(0, 500) : message;
        try {
            transactionTemplate.executeWithoutResult(status -> taskMapper.markFailed(taskId, trimmed));
        } catch (RuntimeException ex) {
            log.error("索引任务失败状态回写失败：taskId={}", taskId, ex);
        }
    }

    private boolean indexExists(String index) {
        try {
            return client.indices().exists(e -> e.index(index)).value();
        } catch (IOException ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "检索引擎不可用: " + ex.getMessage());
        }
    }

    private String physicalIndex(long tenantId, String indexName) {
        return indexPrefix + "_" + tenantId + "_" + indexName;
    }

    private String normalizeIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "index_name 不能为空");
        }
        String normalized = indexName.trim().toLowerCase();
        if (!INDEX_NAME.matcher(normalized).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "index_name 仅支持小写字母、数字与下划线");
        }
        return normalized;
    }

    private String normalizeTaskType(String taskType) {
        String normalized = taskType == null || taskType.isBlank() ? "FULL" : taskType.trim().toUpperCase();
        if (!"FULL".equals(normalized) && !"INCREMENTAL".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "task_type 仅支持 FULL/INCREMENTAL");
        }
        return normalized;
    }

    private IndexTaskView toView(Map<String, Object> row) {
        return new IndexTaskView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("index_name"),
                (String) row.get("task_type"),
                (String) row.get("status"),
                row.get("doc_count") == null ? null : ((Number) row.get("doc_count")).longValue(),
                (String) row.get("error_msg"));
    }
}
