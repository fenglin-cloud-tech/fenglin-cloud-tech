package com.fenglin.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.intelligence.client.AiGatewayClient;
import com.fenglin.intelligence.client.WorkflowClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云智端到端主链测试：真实内嵌 PostgreSQL + Flyway + 真实 HTTP。
 *
 * <p>覆盖 H1 知识库/审核/索引/检索/图谱、H2 Copilot/RAG/引用/反馈/Agent、H3 指标/数据集/看板/洞察/归因/订阅，
 * 以及平台 AI 中台代理（授权/计量/双模式）。模型网关与工作流以测试替身注入，验证云智只编排不重复实现。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntelligenceIntegrationTest {

    private static final long TENANT = 9941L;
    private static final long OTHER_TENANT = 9942L;
    private static final long USER = 8501L;
    private static final String ALL = "*";

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();
    private static final List<String> CHAT_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_intelligence")
                .defaultSchema("fenglin_intelligence")
                .locations("classpath:db/intelligence-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_intelligence");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_intelligence_app");
    }

    @TestConfiguration
    static class TestDoublesConfig {

        @Bean
        @Primary
        AuditSink recordingAuditSink() {
            return AUDIT_EVENTS::add;
        }

        @Bean
        @Primary
        WorkflowClient recordingWorkflowClient() {
            return (permissions, requestId, processKey, businessKey, businessType, title, formData) -> {
                WORKFLOW_CALLS.add(processKey + ":" + businessKey);
                return "WF-" + businessKey;
            };
        }

        @Bean
        @Primary
        AiGatewayClient stubGatewayClient() {
            return new AiGatewayClient() {
                @Override
                public ChatResult chat(String content, Double temperature, Integer secretLevel) {
                    CHAT_CALLS.add(content);
                    return new ChatResult("依据知识库资料，报销需在出差结束后 7 个工作日内提交。",
                            120, 60, 8, true, List.of());
                }

                @Override
                public Map<String, Object> grants(String hostProduct) {
                    return Map.of("source", "hub_ai_capability_grant",
                            "host_product", hostProduct == null ? "ALL" : hostProduct,
                            "items", List.of(Map.of("capability", "RAG", "enabled", true)));
                }

                @Override
                public Map<String, Object> metering(String mode, String hostProduct, String from, String to) {
                    return Map.of("source", "hub_ai_metering", "total_tokens", 180,
                            "mode", mode == null ? "SAAS" : mode);
                }

                @Override
                public Map<String, Object> validateMode() {
                    return Map.of("consistent", true, "deploy_mode", "SAAS");
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetData() throws Exception {
        AUDIT_EVENTS.clear();
        WORKFLOW_CALLS.clear();
        CHAT_CALLS.clear();
        execute("DO $$ DECLARE r record; BEGIN "
                + "FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_intelligence' "
                + "AND tablename <> 'flyway_schema_history' LOOP "
                + "EXECUTE 'TRUNCATE TABLE fenglin_intelligence.' || quote_ident(r.tablename) || ' CASCADE'; "
                + "END LOOP; END $$");
    }

    @Test
    void knowledgeBaseDocumentReviewIndexSearchAndKgChain() throws Exception {
        JsonNode kb = ok(post("/api/intelligence/v1/admin/knowledge-bases", TENANT, USER, ALL, null, Map.of(
                "code", "kb_finance", "name", "财务制度库", "default_sec_level", 2)));
        long kbId = kb.path("data").path("id").asLong();
        assertThat(kb.path("data").path("code").asText()).isEqualTo("kb_finance");
        assertThat(post("/api/intelligence/v1/admin/knowledge-bases", TENANT, USER, ALL, null, Map.of(
                "code", "kb_finance", "name", "重复编码")).getStatusCode().value()).isEqualTo(409);

        JsonNode source = ok(post("/api/intelligence/v1/admin/data-sources", TENANT, USER, ALL, null, Map.of(
                "code", "ds_finance", "name", "财务共享盘", "category", "FILE",
                "connector_type", "LOCAL_FILE", "credential_secret", "top-secret-value")));
        long dataSourceId = source.path("data").path("id").asLong();
        assertThat(source.path("data").path("has_credential").asBoolean()).isTrue();
        assertThat(ok(get("/api/intelligence/v1/admin/data-sources", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode doc = ok(post("/api/intelligence/v1/admin/documents", TENANT, USER, ALL, null, Map.of(
                "knowledge_base_id", kbId, "data_source_id", dataSourceId, "title", "差旅报销制度",
                "doc_type", "TXT", "sec_level", 2,
                "content", "差旅报销标准如下：一线城市住宿费每晚不超过 600 元。\n\n"
                        + "报销需在出差结束后 7 个工作日内提交，超期需部门负责人审批。")));
        long docId = doc.path("data").path("id").asLong();
        assertThat(doc.path("data").path("review_status").asText()).isEqualTo("DRAFT");
        assertThat(doc.path("data").path("parsed_chunks").asInt()).isEqualTo(2);

        JsonNode submitted = ok(post("/api/intelligence/v1/admin/documents/" + docId + "/review:submit",
                TENANT, USER, ALL, "doc-submit-1", null));
        assertThat(submitted.path("data").path("review_status").asText()).isEqualTo("PENDING");
        assertThat(submitted.path("data").path("instance_no").asText()).isEqualTo("WF-" + docId);
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("aiKnowledgeReview:"));

        assertThat(post("/api/intelligence/v1/admin/documents/" + docId + "/index", TENANT, USER, ALL, null, null)
                .getStatusCode().value()).isEqualTo(409);

        JsonNode reviewed = ok(post("/api/intelligence/v1/admin/documents/" + docId + "/review",
                TENANT, USER, ALL, null, Map.of("action", "APPROVE")));
        assertThat(reviewed.path("data").path("review_status").asText()).isEqualTo("APPROVED");
        assertThat(reviewed.path("data").path("index_task_id").asLong()).isPositive();

        JsonNode indexed = ok(post("/api/intelligence/v1/admin/documents/" + docId + "/index",
                TENANT, USER, ALL, null, null));
        assertThat(indexed.path("data").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(indexed.path("data").path("chunk_count").asInt()).isEqualTo(2);

        JsonNode hybrid = ok(post("/api/intelligence/v1/search/hybrid", TENANT, USER, ALL, null, Map.of(
                "query", "住宿费", "kb_ids", List.of(kbId), "top_k", 5)));
        assertThat(hybrid.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(hybrid.path("data").path("degraded").asBoolean()).isTrue();
        assertThat(hybrid.path("data").path("hits").get(0).path("document_id").asLong()).isEqualTo(docId);

        JsonNode secureMiss = ok(post("/api/intelligence/v1/search/secure", TENANT, USER, ALL, null, Map.of(
                "query", "报销", "sec_level", 1)));
        assertThat(secureMiss.path("data").path("total").asInt()).isZero();
        assertThat(ok(post("/api/intelligence/v1/search/secure", TENANT, USER, ALL, null, Map.of(
                "query", "报销", "sec_level", 2))).path("data").path("total").asInt()).isEqualTo(2);

        JsonNode preview = ok(get("/api/intelligence/v1/documents/" + docId + "/preview?sec_level=3",
                TENANT, USER, ALL));
        assertThat(preview.path("data").path("chunks")).hasSize(2);
        assertThat(get("/api/intelligence/v1/documents/" + docId + "/preview?sec_level=1",
                TENANT, USER, ALL).getStatusCode().value()).isEqualTo(403);

        long entityA = ok(post("/api/intelligence/v1/admin/kg/entities", TENANT, USER, ALL, null, Map.of(
                "knowledge_base_id", kbId, "name", "差旅", "entity_type", "CONCEPT")))
                .path("data").path("id").asLong();
        long entityB = ok(post("/api/intelligence/v1/admin/kg/entities", TENANT, USER, ALL, null, Map.of(
                "knowledge_base_id", kbId, "name", "住宿费", "entity_type", "CONCEPT")))
                .path("data").path("id").asLong();
        ok(post("/api/intelligence/v1/admin/kg/relations", TENANT, USER, ALL, null, Map.of(
                "knowledge_base_id", kbId, "source_entity_id", entityA, "target_entity_id", entityB,
                "relation_type", "INCLUDES")));
        assertThat(ok(get("/api/intelligence/v1/kg/entities?kb_id=" + kbId, TENANT, USER, ALL))
                .path("data")).hasSize(2);
        assertThat(ok(get("/api/intelligence/v1/kg/paths?from_id=" + entityA + "&to_id=" + entityB,
                TENANT, USER, ALL)).path("data").path("found").asBoolean()).isTrue();

        JsonNode analytics = ok(get("/api/intelligence/v1/admin/search/analytics", TENANT, USER, ALL));
        assertThat(analytics.path("data").path("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "document.submitReview".equals(event.action()));

        JsonNode otherKb = ok(post("/api/intelligence/v1/admin/knowledge-bases", OTHER_TENANT, 8601L, ALL, null,
                Map.of("code", "kb_other", "name", "另一租户库")));
        JsonNode otherDoc = ok(post("/api/intelligence/v1/admin/documents", OTHER_TENANT, 8601L, ALL, null, Map.of(
                "knowledge_base_id", otherKb.path("data").path("id").asLong(),
                "title", "他租户文档", "content", "隔离验证")));
        assertThat(get("/api/intelligence/v1/documents/" + otherDoc.path("data").path("id").asLong() + "/preview",
                TENANT, USER, ALL).getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    void copilotRagCitationFeedbackAgentAndGuardrailChain() throws Exception {
        long[] seeded = seedIndexedDocument();
        long kbId = seeded[0];

        JsonNode conversation = ok(post("/api/intelligence/v1/copilot/conversations", TENANT, USER, ALL, null, Map.of(
                "title", "报销咨询", "type", "RAG", "kb_ids", List.of(kbId))));
        long conversationId = conversation.path("data").path("id").asLong();

        JsonNode answer = ok(post("/api/intelligence/v1/copilot/conversations/" + conversationId + "/messages",
                TENANT, USER, ALL, null, Map.of("content", "报销", "sec_level", 2)));
        long assistantMessageId = answer.path("data").path("assistant_message_id").asLong();
        assertThat(answer.path("data").path("answer").asText()).contains("7 个工作日");
        assertThat(answer.path("data").path("citations")).hasSize(1);
        assertThat(answer.path("data").path("model_degraded").asBoolean()).isTrue();
        assertThat(CHAT_CALLS).anyMatch(prompt -> prompt.contains("【资料】") && prompt.contains("差旅报销制度"));

        JsonNode messages = ok(get("/api/intelligence/v1/copilot/conversations/" + conversationId + "/messages",
                TENANT, USER, ALL));
        assertThat(messages.path("data").path("total").asInt()).isEqualTo(2);
        JsonNode assistant = messages.path("data").path("records").get(1);
        assertThat(assistant.path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(assistant.path("citations")).hasSize(1);

        JsonNode feedback = ok(post("/api/intelligence/v1/copilot/messages/" + assistantMessageId + "/feedback",
                TENANT, USER, ALL, null, Map.of("feedback_type", "LIKE", "rating", 5, "comment", "引用准确")));
        assertThat(feedback.path("data").path("feedback_type").asText()).isEqualTo("LIKE");

        JsonNode summarized = ok(post("/api/intelligence/v1/copilot/text:summarize", TENANT, USER, ALL, null,
                Map.of("text", "差旅报销需在 7 个工作日内提交，住宿费一线城市不超过 600 元。")));
        assertThat(summarized.path("data").path("mode").asText()).isEqualTo("SUMMARIZE");
        assertThat(summarized.path("data").path("content").asText()).isNotBlank();

        ok(post("/api/intelligence/v1/admin/agents", TENANT, USER, ALL, null, Map.of(
                "code", "agent_reimburse", "name", "报销助手", "status", "PUBLISHED", "max_steps", 5,
                "nodes", List.of(Map.of("type", "LLM")), "tools", List.of("knowledge_search"))));
        JsonNode run = ok(post("/api/intelligence/v1/agents/agent_reimburse/run", TENANT, USER, ALL, null, Map.of(
                "input", Map.of("question", "住宿费上限"))));
        assertThat(run.path("data").path("answer").asText()).isNotBlank();
        assertThat(run.path("data").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(ok(get("/api/intelligence/v1/agents", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode guardrail = ok(put("/api/intelligence/v1/admin/guardrails", TENANT, USER, ALL,
                Map.of("config", Map.of("output_block", true))));
        assertThat(guardrail.path("data").path("managed_by").asText()).isEqualTo("platform-ai");
        assertThat(guardrail.path("data").path("owner_unit").asText()).contains("fenglin-ai-service");

        assertThat(get("/api/intelligence/v1/copilot/conversations/" + conversationId + "/messages",
                OTHER_TENANT, 8602L, ALL).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void biMetricDatasetDashboardInsightAttributionAndPlatformProxyChain() throws Exception {
        JsonNode source = ok(post("/api/intelligence/v1/admin/data-sources", TENANT, USER, ALL, null, Map.of(
                "code", "ds_bi", "name", "BI 数据源", "category", "DB", "connector_type", "POSTGRES")));
        long dataSourceId = source.path("data").path("id").asLong();

        JsonNode metric = ok(post("/api/intelligence/v1/admin/metrics", TENANT, USER, ALL, null, Map.of(
                "code", "m_revenue", "name", "营业收入", "category", "SALES", "metric_type", "ATOMIC",
                "formula", "SUM(amount)", "unit", "CNY", "grain", "DAY", "dimensions", List.of("channel"))));
        long metricId = metric.path("data").path("id").asLong();
        assertThat(ok(get("/api/intelligence/v1/metrics?keyword=营业收入", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode dataset = ok(post("/api/intelligence/v1/admin/datasets", TENANT, USER, ALL, null, Map.of(
                "code", "ds_sales", "name", "销售明细", "data_source_id", dataSourceId,
                "mode", "DIRECT", "sql_text", "SELECT channel, amount FROM sales")));
        long datasetId = dataset.path("data").path("id").asLong();
        assertThat(ok(post("/api/intelligence/v1/admin/datasets/" + datasetId + "/fields", TENANT, USER, ALL, null,
                Map.of("field_name", "channel", "field_type", "DIMENSION", "data_type", "STRING")))
                .path("data").path("dataset_id").asLong()).isEqualTo(datasetId);
        ok(post("/api/intelligence/v1/admin/datasets/" + datasetId + "/fields", TENANT, USER, ALL, null,
                Map.of("field_name", "amount", "field_type", "MEASURE", "data_type", "NUMBER")));
        assertThat(ok(get("/api/intelligence/v1/datasets/" + datasetId + "/fields", TENANT, USER, ALL))
                .path("data")).hasSize(2);

        JsonNode dashboard = ok(post("/api/intelligence/v1/admin/dashboards", TENANT, USER, ALL, null, Map.of(
                "code", "db_sales", "name", "销售看板", "theme", "LIGHT", "status", "PUBLISHED",
                "layout", Map.of("cols", 12))));
        long dashboardId = dashboard.path("data").path("id").asLong();
        ok(post("/api/intelligence/v1/admin/dashboards/" + dashboardId + "/charts", TENANT, USER, ALL, null, Map.of(
                "name", "渠道收入", "chart_type", "BAR", "dataset_id", datasetId,
                "query", Map.of("dimension", "channel", "measure", "amount"))));
        JsonNode detail = ok(get("/api/intelligence/v1/dashboards/" + dashboardId, TENANT, USER, ALL));
        assertThat(detail.path("data").path("charts")).hasSize(1);
        assertThat(detail.path("data").path("layout").path("cols").asInt()).isEqualTo(12);

        JsonNode task = ok(post("/api/intelligence/v1/admin/insight-tasks", TENANT, USER, ALL, null, Map.of(
                "name", "收入波动洞察", "target_type", "METRIC", "target_id", metricId, "trigger_type", "MANUAL")));
        long taskId = task.path("data").path("id").asLong();
        JsonNode run = ok(post("/api/intelligence/v1/admin/insight-tasks/" + taskId + "/run", TENANT, USER, ALL, null,
                null));
        assertThat(run.path("data").path("anomaly_type").asText()).isEqualTo("SPIKE");
        assertThat(run.path("data").path("attribution_id").asLong()).isPositive();
        assertThat(ok(get("/api/intelligence/v1/insight-tasks/" + taskId + "/results", TENANT, USER, ALL))
                .path("data")).hasSize(1);

        ok(post("/api/intelligence/v1/analytics/attribution", TENANT, USER, ALL, null, Map.of(
                "metric_id", metricId, "dimension_field", "region", "algorithm", "CONTRIBUTION",
                "contributions", List.of(Map.of("dimension", "华东", "contribution", 0.55)),
                "main_factor", "华东")));
        assertThat(ok(get("/api/intelligence/v1/analytics/attributions?metric_id=" + metricId,
                TENANT, USER, ALL)).path("data")).hasSize(2);

        JsonNode nl2sql = ok(post("/api/intelligence/v1/analytics/nl2sql", TENANT, USER, ALL, null, Map.of(
                "question", "各渠道收入是多少", "dataset_id", datasetId)));
        assertThat(nl2sql.path("data").path("sql").asText()).contains("GROUP BY channel").contains("SUM(amount)");
        assertThat(nl2sql.path("data").path("degraded").asBoolean()).isTrue();

        JsonNode exported = ok(post("/api/intelligence/v1/analytics/export", TENANT, USER, ALL, null, Map.of()));
        assertThat(exported.path("data").path("status").asText()).isEqualTo("QUEUED");
        assertThat(exported.path("data").path("delegate").asText()).isEqualTo("fenglin-report-service");

        JsonNode subscription = ok(post("/api/intelligence/v1/subscriptions", TENANT, USER, ALL, null, Map.of(
                "task_id", taskId, "channels", "EMAIL", "frequency", "DAILY")));
        assertThat(subscription.path("data").path("status").asText()).isEqualTo("ENABLED");
        assertThat(ok(get("/api/intelligence/v1/subscriptions", TENANT, USER, ALL)).path("data")).hasSize(1);

        assertThat(ok(put("/api/intelligence/v1/admin/data-permissions", TENANT, USER, ALL, Map.of(
                "subject_type", "ROLE", "subject_id", 90L, "resource_type", "DASHBOARD",
                "resource_id", dashboardId, "data_scope", "DEPT", "column_hidden", "amount")))
                .path("data").path("id").asLong()).isPositive();

        JsonNode overview = ok(get("/api/intelligence/v1/report/overview", TENANT, USER, ALL));
        assertThat(overview.path("data").path("datasets").asInt()).isEqualTo(1);
        assertThat(overview.path("data").path("dashboards").asInt()).isEqualTo(1);
        assertThat(overview.path("data").path("insight_tasks").asInt()).isEqualTo(1);

        JsonNode mode = ok(get("/api/intelligence/v1/admin/ai/mode", TENANT, USER, "tenant:ai:usage:view"));
        assertThat(mode.path("data").path("capability_source").asText()).isEqualTo("hub_ai_capability_grant");
        assertThat(mode.path("data").path("metering_source").asText()).isEqualTo("hub_ai_metering");
        assertThat(mode.path("data").path("model_gateway_owner").asText()).isEqualTo("fenglin-ai-service");
        assertThat(mode.path("data").path("business_owner").asText()).isEqualTo("fenglin-intelligence-service");

        JsonNode grants = ok(get("/api/intelligence/v1/admin/ai/capability-grants?host_product=intelligence",
                TENANT, USER, "tenant:ai:grant:manage"));
        assertThat(grants.path("data").path("source").asText()).isEqualTo("hub_ai_capability_grant");
        JsonNode metering = ok(get("/api/intelligence/v1/admin/ai/metering?mode=SAAS", TENANT, USER,
                "tenant:ai:usage:view"));
        assertThat(metering.path("data").path("source").asText()).isEqualTo("hub_ai_metering");
        assertThat(ok(post("/api/intelligence/v1/admin/ai/mode:validate", TENANT, USER, "tenant:ai:usage:view",
                null, null)).path("data").path("consistent").asBoolean()).isTrue();

        assertThat(post("/api/intelligence/v1/admin/metrics", TENANT, USER, "ai:bi:view", null, Map.of(
                "code", "m_denied", "name", "越权指标", "formula", "SUM(1)"))
                .getStatusCode().value()).isEqualTo(403);
    }

    /** 建知识库 → 文档 → 提交审核 → 通过 → 索引，返回 [kbId, docId]。 */
    private long[] seedIndexedDocument() throws Exception {
        long kbId = ok(post("/api/intelligence/v1/admin/knowledge-bases", TENANT, USER, ALL, null, Map.of(
                "code", "kb_copilot", "name", "Copilot 知识库", "default_sec_level", 2)))
                .path("data").path("id").asLong();
        long docId = ok(post("/api/intelligence/v1/admin/documents", TENANT, USER, ALL, null, Map.of(
                "knowledge_base_id", kbId, "title", "差旅报销制度", "doc_type", "TXT", "sec_level", 2,
                "content", "差旅报销需在出差结束后 7 个工作日内提交，住宿费一线城市不超过 600 元。")))
                .path("data").path("id").asLong();
        ok(post("/api/intelligence/v1/admin/documents/" + docId + "/review:submit", TENANT, USER, ALL,
                "copilot-submit-1", null));
        ok(post("/api/intelligence/v1/admin/documents/" + docId + "/review", TENANT, USER, ALL, null,
                Map.of("action", "APPROVE")));
        ok(post("/api/intelligence/v1/admin/documents/" + docId + "/index", TENANT, USER, ALL, null, null));
        return new long[]{kbId, docId};
    }

    private HttpHeaders headers(long tenantId, long userId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Permissions", permissions);
        return headers;
    }

    private ResponseEntity<String> get(String path, long tenantId, long userId, String permissions) {
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(headers(tenantId, userId, permissions)), String.class);
    }

    private ResponseEntity<String> post(String path, long tenantId, long userId, String permissions,
                                        String idemKey, Map<String, Object> body) {
        HttpHeaders headers = headers(tenantId, userId, permissions);
        if (idemKey != null) {
            headers.set("Idempotency-Key", idemKey);
        }
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> put(String path, long tenantId, long userId, String permissions,
                                       Map<String, Object> body) {
        return restTemplate.exchange(url(path), HttpMethod.PUT,
                new HttpEntity<>(body, headers(tenantId, userId, permissions)), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode ok(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .withFailMessage("期望 2xx，实际 %s：%s", response.getStatusCode(), response.getBody())
                .isTrue();
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.path("code").asText()).isEqualTo("0");
        return root;
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
