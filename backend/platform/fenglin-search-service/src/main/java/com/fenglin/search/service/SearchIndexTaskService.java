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

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final DataSource dataSource;
    private final String indexPrefix;

    public SearchIndexTaskService(ElasticsearchClient client,
                                  SearchIndexTaskMapper taskMapper,
                                  AuditSink auditSink,
                                  PlatformTransactionManager transactionManager,
                                  DataSource dataSource,
                                  @Value("${fenglin.search.index-prefix:hub}") String indexPrefix) {
        this.client = client;
        this.taskMapper = taskMapper;
        this.auditSink = auditSink;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
        this.indexPrefix = indexPrefix;
    }

    @Transactional
    public IndexTaskView create(IndexTaskRequest request, Long operatorId) {
        String indexName = normalizeIndexName(request.indexName());
        String taskType = normalizeTaskType(request.taskType());
        long tenantId = TenantContext.requireTenantId();

        if ("INCREMENTAL".equals(taskType) && !indexExists(physicalIndex(indexName))) {
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
        return toView(taskMapper.findById(tenantId, id));
    }

    @Transactional(readOnly = true)
    public IndexTaskView get(long taskId) {
        Map<String, Object> row = taskMapper.findById(TenantContext.requireTenantId(), taskId);
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
                Map<String, Object> task = transactionTemplate.execute(status ->
                        taskMapper.findById(tenantId, taskId));
                if (task == null) {
                    return;
                }
                String indexName = (String) task.get("index_name");
                String physical = physicalIndex(indexName);
                if ("FULL".equals(task.get("task_type"))) {
                    ensureIndex(physical);
                    ingest(physical, indexName, tenantId);
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
        // FULL 视为重建：旧 mapping 无法原地修改，先删后建，保证中文分词生效
        if (client.indices().exists(e -> e.index(index)).value()) {
            client.indices().delete(d -> d.index(index));
        }
        client.indices().create(c -> c.index(index).mappings(m -> m
                .properties("doc_id", p -> p.keyword(k -> k))
                .properties("tenant_id", p -> p.long_(l -> l))
                .properties("title", p -> p.text(t -> t.analyzer("cjk")))
                .properties("content", p -> p.text(t -> t.analyzer("cjk")))
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

    private String physicalIndex(String indexName) {
        return indexPrefix + "_" + indexName;
    }

    /**
     * FULL 任务灌数：先清掉该租户旧文档，再按域写入 {@code hub_<domain>}。
     * 当前仅 hub 域在同库（fenglin_hub）有数据源；service / intelligence 的数据在独立库，无法跨库直读。
     */
    private void ingest(String index, String indexName, long tenantId) throws IOException {
        if (!"hub".equals(indexName)) {
            log.info("索引 {} 暂无同库数据源，跳过灌数", index);
            return;
        }
        List<Map<String, Object>> docs = loadHubUsers(tenantId);
        client.deleteByQuery(d -> d.index(index)
                .query(q -> q.term(t -> t.field("tenant_id").value(tenantId))));
        for (Map<String, Object> doc : docs) {
            client.index(i -> i.index(index).id(String.valueOf(doc.get("doc_id"))).document(doc));
        }
        log.info("索引 {} 灌入 {} 条文档", index, docs.size());
    }

    private List<Map<String, Object>> loadHubUsers(long tenantId) {
        String sql = "SELECT id, username, real_name, nickname, employee_no FROM fenglin_hub.hub_user "
                + "WHERE tenant_id = ? AND deleted = 0";
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            // RLS 依赖事务级 GUC，必须在同一事务内设置后再查询
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement set = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true), set_config('app.cross_tenant', 'false', true)")) {
                    set.setString(1, String.valueOf(tenantId));
                    set.execute();
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, tenantId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            String username = rs.getString("username");
                            String display = rs.getString("real_name");
                            if (display == null || display.isBlank()) {
                                display = rs.getString("nickname");
                            }
                            if (display == null || display.isBlank()) {
                                display = username;
                            }
                            String employeeNo = rs.getString("employee_no");
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("doc_id", String.valueOf(rs.getLong("id")));
                            doc.put("tenant_id", tenantId);
                            doc.put("title", display);
                            doc.put("content", username + " " + (employeeNo == null ? "" : employeeNo));
                            doc.put("secret_level", 0);
                            doc.put("updated_at", Instant.now().toString());
                            docs.add(doc);
                        }
                    }
                }
                connection.rollback();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取 hub 域索引数据失败", ex);
        }
        return docs;
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
