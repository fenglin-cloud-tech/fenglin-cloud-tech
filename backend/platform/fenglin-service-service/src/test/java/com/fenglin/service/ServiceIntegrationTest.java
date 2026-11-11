package com.fenglin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.service.client.WorkflowClient;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云服端到端主链测试：真实内嵌 PostgreSQL + Flyway + 真实 HTTP。
 *
 * <p>覆盖工单/SLA、服务目录、知识库、CSAT、资产、问题/变更、低代码、流程挖掘、RPA 与报表。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceIntegrationTest {

    private static final long TENANT = 9821L;
    private static final long OTHER_TENANT = 9822L;
    private static final long USER = 7301L;
    private static final long AGENT = 7302L;
    private static final String ALL = "*";

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_service")
                .defaultSchema("fenglin_service")
                .locations("classpath:db/service-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_service");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_service_app");
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
        execute("DO $$ DECLARE r record; BEGIN "
                + "FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_service' "
                + "AND tablename NOT IN ('flyway_schema_history', 'svc_catalog', 'svc_catalog_item', "
                + "'svc_sla_policy') LOOP "
                + "EXECUTE 'TRUNCATE TABLE fenglin_service.' || quote_ident(r.tablename) || ' CASCADE'; "
                + "END LOOP; END $$");
    }

    @Test
    void ticketChainSupportsIdempotencyPiiTransitionCsatAndTenantIsolation() throws Exception {
        Map<String, Object> body = Map.of("title", "VPN 无法连接", "ticket_type", "REQUEST", "priority", "P3",
                "description", "远程办公登录失败", "contact", "13800001111");

        JsonNode first = ok(post("/api/service/v1/tickets", TENANT, USER, ALL, "ticket-idem-1", body));
        long ticketId = first.path("data").path("id").asLong();
        String ticketNo = first.path("data").path("ticket_no").asText();
        assertThat(ticketNo).startsWith("SVC");
        assertThat(first.path("data").path("status").asText()).isEqualTo("NEW");
        assertThat(first.path("data").path("sla_policy_id").asLong()).isEqualTo(900002L);

        JsonNode duplicate = ok(post("/api/service/v1/tickets", TENANT, USER, ALL, "ticket-idem-1", body));
        assertThat(duplicate.path("data").path("id").asLong()).isEqualTo(ticketId);

        JsonNode withPii = ok(get("/api/service/v1/tickets/" + ticketId, TENANT, USER, ALL));
        assertThat(withPii.path("data").path("contact").asText()).isEqualTo("13800001111");
        assertThat(withPii.path("data").path("contact_masked").asText()).contains("****");
        JsonNode withoutPii = ok(get("/api/service/v1/tickets/" + ticketId, TENANT, AGENT, "service:ticket:view"));
        assertThat(withoutPii.path("data").has("contact")).isFalse();

        JsonNode assigned = ok(post("/api/service/v1/tickets/" + ticketId + "/assign", TENANT, USER, ALL, null,
                Map.of("assignee_id", AGENT)));
        assertThat(assigned.path("data").path("status").asText()).isEqualTo("ASSIGNED");
        ok(post("/api/service/v1/tickets/" + ticketId + "/transition", TENANT, AGENT, ALL, null,
                Map.of("to_status", "IN_PROGRESS")));
        ok(post("/api/service/v1/tickets/" + ticketId + "/comments", TENANT, AGENT, ALL, null,
                Map.of("content", "已远程协助", "internal", false)));
        assertThat(ok(get("/api/service/v1/tickets/" + ticketId + "/comments", TENANT, AGENT, ALL))
                .path("data")).hasSize(1);
        ResponseEntity<String> illegal = post("/api/service/v1/tickets/" + ticketId + "/transition", TENANT, AGENT, ALL,
                null, Map.of("to_status", "CLOSED"));
        assertThat(illegal.getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode resolved = ok(post("/api/service/v1/tickets/" + ticketId + "/transition", TENANT, AGENT, ALL, null,
                Map.of("to_status", "RESOLVED")));
        assertThat(resolved.path("data").path("status").asText()).isEqualTo("RESOLVED");
        JsonNode closed = ok(post("/api/service/v1/tickets/" + ticketId + "/transition", TENANT, AGENT, ALL, null,
                Map.of("to_status", "CLOSED")));
        assertThat(closed.path("data").path("status").asText()).isEqualTo("CLOSED");

        JsonNode sla = ok(get("/api/service/v1/tickets/" + ticketId + "/sla", TENANT, AGENT, ALL));
        assertThat(sla.path("data").has("response_due_at")).isTrue();

        ok(post("/api/service/v1/csat", TENANT, USER, ALL, null, Map.of("ticket_id", ticketId, "score", 5,
                "comment", "处理及时")));
        assertThat(post("/api/service/v1/csat", TENANT, USER, ALL, null,
                Map.of("ticket_id", ticketId, "score", 4)).getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode report = ok(get("/api/service/v1/reports/tickets", TENANT, USER, ALL));
        assertThat(report.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode workload = ok(get("/api/service/v1/reports/workload", TENANT, USER, ALL));
        assertThat(workload.path("data")).isNotNull();
        JsonNode csat = ok(get("/api/service/v1/reports/csat", TENANT, USER, ALL));
        assertThat(csat.path("data").path("summary")).isNotNull();

        JsonNode email = ok(post("/api/service/v1/internal/tickets/email-inbound", TENANT, USER, ALL, null,
                Map.of("from", "user@example.com", "subject", "邮箱转单", "body", "打印机卡纸",
                        "ticket_type", "REQUEST", "priority", "P3", "requester_id", USER)));
        assertThat(email.path("data").path("ticket_no").asText()).startsWith("SVC");

        JsonNode imported = ok(post("/api/service/v1/tickets/import", TENANT, USER, ALL, "import-1",
                Map.of("items", List.of(Map.of("title", "批量-1", "ticket_type", "REQUEST", "priority", "P3"),
                        Map.of("title", "批量-2", "ticket_type", "REQUEST", "priority", "P3")))));
        assertThat(imported.path("data").path("created").asInt()).isEqualTo(2);

        JsonNode other = ok(post("/api/service/v1/tickets", OTHER_TENANT, 7401L, ALL, null,
                Map.of("title", "另一租户工单", "ticket_type", "REQUEST", "priority", "P3")));
        long otherId = other.path("data").path("id").asLong();
        assertThat(get("/api/service/v1/tickets/" + otherId, TENANT, USER, ALL).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void catalogKbAssetProblemAndChangeChain() throws Exception {
        JsonNode tree = ok(get("/api/service/v1/catalog/tree", TENANT, USER, ALL));
        assertThat(tree.path("data")).isNotEmpty();
        JsonNode items = ok(get("/api/service/v1/catalog/items?catalogId=900101", TENANT, USER, ALL));
        assertThat(items.path("data")).hasSizeGreaterThanOrEqualTo(3);

        JsonNode applied = ok(post("/api/service/v1/catalog/items/900201/apply", TENANT, USER, ALL, null,
                Map.of("title", "申请管理员账号", "contact", "13900002222", "form_data", Map.of("system", "OA"))));
        long ticketId = applied.path("data").path("ticket").path("id").asLong();
        assertThat(applied.path("data").path("approval_instance_no").asText()).startsWith("WF-");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("svcCatalogItemApproval:"));

        JsonNode category = ok(post("/api/service/v1/kb/categories", TENANT, USER, ALL, null,
                Map.of("name", "账号与权限")));
        long categoryId = category.path("data").path("id").asLong();
        JsonNode article = ok(post("/api/service/v1/kb/articles", TENANT, USER, ALL, null, Map.of(
                "category_id", categoryId, "title", "VPN 排障手册", "summary", "常见故障",
                "content", "1. 检查网络 2. 重装客户端", "keywords", List.of("VPN", "排障"),
                "status", "PUBLISHED")));
        long articleId = article.path("data").path("id").asLong();
        JsonNode detail = ok(get("/api/service/v1/kb/articles/" + articleId, TENANT, USER, ALL));
        assertThat(detail.path("data").path("title").asText()).isEqualTo("VPN 排障手册");
        JsonNode detailAgain = ok(get("/api/service/v1/kb/articles/" + articleId, TENANT, USER, ALL));
        assertThat(detailAgain.path("data").path("view_count").asInt()).isGreaterThanOrEqualTo(1);
        ok(post("/api/service/v1/kb/articles/" + articleId + "/feedback", TENANT, USER, ALL, null,
                Map.of("helpful", true, "ticket_id", ticketId)));
        assertThat(ok(get("/api/service/v1/kb/suggest?keyword=VPN", TENANT, USER, ALL)).path("data"))
                .isNotEmpty();
        JsonNode asset = ok(post("/api/service/v1/assets", TENANT, USER, ALL, null,
                Map.of("name", "MacBook Pro", "asset_type", "LAPTOP", "status", "IDLE")));
        long assetId = asset.path("data").path("id").asLong();
        JsonNode borrowed = ok(post("/api/service/v1/assets/" + assetId + "/borrow", TENANT, USER, ALL, null,
                Map.of("owner_id", AGENT, "days", 7)));
        assertThat(borrowed.path("data").path("status").asText()).isEqualTo("BORROWED");
        JsonNode returned = ok(post("/api/service/v1/assets/" + assetId + "/return", TENANT, USER, ALL, null,
                Map.of("remark", "按时归还")));
        assertThat(returned.path("data").path("status").asText()).isEqualTo("IDLE");
        assertThat(ok(get("/api/service/v1/assets/reminders", TENANT, USER, ALL)).path("data")).isNotNull();

        JsonNode scopedAsset = ok(post("/api/service/v1/assets", TENANT, USER, ALL, null,
                Map.of("name", "ThinkPad X1", "asset_type", "LAPTOP", "status", "IDLE")));
        long scopedAssetId = scopedAsset.path("data").path("id").asLong();
        ok(post("/api/service/v1/assets/" + scopedAssetId + "/borrow", TENANT, USER, ALL, null,
                Map.of("owner_id", USER, "days", 7)));
        assertThat(ok(get("/api/service/v1/assets/reminders", TENANT, USER, "service:asset:reminder:view"))
                .path("data").path("total").asInt()).isEqualTo(1);
        assertThat(ok(get("/api/service/v1/assets/reminders", TENANT, AGENT, "service:asset:reminder:view"))
                .path("data").path("total").asInt()).isZero();
        assertThat(ok(get("/api/service/v1/assets/reminders", TENANT, AGENT, ALL))
                .path("data").path("total").asInt()).isEqualTo(2);

        JsonNode problem = ok(post("/api/service/v1/problems", TENANT, USER, ALL, null, Map.of(
                "title", "VPN 网关不稳定", "description", "多起连接失败", "ticket_ids", List.of(ticketId))));
        long problemId = problem.path("data").path("id").asLong();
        assertThat(problem.path("data").path("problem_no").asText()).startsWith("PRB");
        JsonNode updated = ok(post("/api/service/v1/problems/" + problemId + "/aggregate", TENANT, USER, ALL, null,
                Map.of("root_cause", "网关连接数耗尽", "known_error", true)));
        assertThat(updated.path("data").path("known_error").asBoolean()).isTrue();

        JsonNode change = ok(post("/api/service/v1/changes", TENANT, USER, ALL, null, Map.of(
                "title", "扩容 VPN 网关", "change_type", "NORMAL", "risk_level", "HIGH",
                "plan", "增加两台网关并灰度", "rollback_plan", "回滚旧配置")));
        long changeId = change.path("data").path("id").asLong();
        assertThat(change.path("data").path("change_no").asText()).startsWith("CHG");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("svcChangeApproval:"));
        JsonNode approved = ok(post("/api/service/v1/changes/" + changeId + "/approve", TENANT, USER, ALL, null,
                Map.of("approved", true)));
        assertThat(approved.path("data").path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void lowcodeDesignReleaseAndRuntimeChain() throws Exception {
        JsonNode app = ok(post("/api/service/v1/lowcode/apps", TENANT, USER, ALL, null,
                Map.of("name", "IT 自助门户", "app_code", "IT_PORTAL")));
        long appId = app.path("data").path("id").asLong();

        JsonNode form = ok(post("/api/service/v1/lowcode/apps/" + appId + "/forms", TENANT, USER, ALL, null,
                Map.of("form_key", "portal_form", "name", "申请表单", "fields", List.of(
                        Map.of("field_key", "title", "label", "标题", "field_type", "TEXT", "required", true)))));
        long formId = form.path("data").path("id").asLong();
        assertThat(form.path("data").path("fields")).hasSize(1);
        assertThat(ok(post("/api/service/v1/lowcode/forms/" + formId + "/validate", TENANT, USER, ALL, null,
                Map.of())).path("data").path("valid").asBoolean()).isTrue();
        assertThat(post("/api/service/v1/lowcode/apps/" + appId + "/forms", TENANT, USER, ALL, null,
                Map.of("form_key", "bad_form", "name", "坏表单", "fields", List.of(
                        Map.of("field_key", "title", "label", "标题", "field_type", "UNKNOWN"))))
                .getStatusCode().is2xxSuccessful()).isFalse();

        Map<String, Object> graph = Map.of(
                "nodes", List.of(Map.of("key", "start", "type", "start"), Map.of("key", "end", "type", "end")),
                "edges", List.of(Map.of("from", "start", "to", "end")));
        JsonNode flow = ok(post("/api/service/v1/lowcode/apps/" + appId + "/flows", TENANT, USER, ALL, null,
                Map.of("flow_key", "portal_flow", "name", "门户审批", "bpmn_json", graph,
                        "form_id", formId, "status", "PUBLISHED")));
        long flowId = flow.path("data").path("id").asLong();
        assertThat(ok(post("/api/service/v1/lowcode/flows/" + flowId + "/simulate", TENANT, USER, ALL, null,
                Map.of())).path("data").path("simulated_path")).hasSize(2);

        JsonNode model = ok(post("/api/service/v1/lowcode/apps/" + appId + "/models", TENANT, USER, ALL, null,
                Map.of("model_key", "portal_model", "name", "门户模型", "fields", List.of(
                        Map.of("field_key", "title", "name", "标题", "data_type", "TEXT")))));
        long modelId = model.path("data").path("id").asLong();
        assertThat(put("/api/service/v1/lowcode/models/" + modelId, TENANT, USER, ALL,
                Map.of("model_key", "portal_model", "name", "门户模型", "version_no", 1,
                        "fields", List.of())).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(ok(post("/api/service/v1/lowcode/models/" + modelId + "/compat-check", TENANT, USER, ALL, null,
                Map.of("fields", List.of(Map.of("field_key", "title", "name", "标题", "data_type", "TEXT")))))
                .path("data").path("compatible").asBoolean()).isTrue();

        JsonNode page = ok(post("/api/service/v1/lowcode/apps/" + appId + "/pages", TENANT, USER, ALL, null,
                Map.of("page_key", "list", "name", "列表页", "page_type", "LIST",
                        "data_binding", Map.of("model_key", "portal_model"))));
        long pageId = page.path("data").path("id").asLong();
        assertThat(ok(put("/api/service/v1/lowcode/pages/" + pageId + "/datasource", TENANT, USER, ALL,
                Map.of("data_binding", Map.of("model_key", "portal_model", "page_size", 20))))
                .path("data").path("data_binding").path("page_size").asInt()).isEqualTo(20);

        JsonNode release = ok(post("/api/service/v1/lowcode/apps/" + appId + "/releases", TENANT, USER, ALL, null,
                Map.of("to_env", "PROD", "release_note", "首发")));
        long releaseId = release.path("data").path("id").asLong();
        assertThat(release.path("data").path("release_no").asText()).startsWith("REL");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("svcLowcodeAppReleaseApproval:"));
        JsonNode approved = ok(post("/api/service/v1/lowcode/releases/" + releaseId + "/approve", TENANT, USER, ALL,
                null, Map.of("approved", true)));
        assertThat(approved.path("data").path("status").asText()).isEqualTo("RELEASED");
        assertThat(ok(get("/api/service/v1/lowcode/apps/" + appId, TENANT, USER, ALL))
                .path("data").path("status").asText()).isEqualTo("PUBLISHED");

        JsonNode runtime = ok(post("/api/service/v1/runtime/apps/IT_PORTAL/flows/portal_flow/start", TENANT, USER,
                ALL, null, Map.of("business_key", "BIZ-1", "title", "门户申请", "approver_id", AGENT)));
        long instanceId = runtime.path("data").path("instance_id").asLong();
        long taskId = runtime.path("data").path("task_id").asLong();
        JsonNode completed = ok(post("/api/service/v1/runtime/instances/" + instanceId + "/tasks/" + taskId
                + "/complete", TENANT, AGENT, ALL, null, Map.of("approved", true, "comment", "同意")));
        assertThat(completed.path("data").path("status").asText()).isEqualTo("COMPLETED");
        JsonNode pageSubmit = ok(post("/api/service/v1/runtime/apps/IT_PORTAL/pages/list/submit", TENANT, USER, ALL,
                null, Map.of("title", "运行期提交")));
        assertThat(pageSubmit.path("data").path("submitted").asBoolean()).isTrue();

        JsonNode script = ok(post("/api/service/v1/lowcode/apps/" + appId + "/scripts", TENANT, USER, ALL, null,
                Map.of("script_key", "calc", "name", "计算", "content", "return 1 + 1")));
        long scriptId = script.path("data").path("id").asLong();
        assertThat(ok(post("/api/service/v1/lowcode/scripts/" + scriptId + "/scan", TENANT, USER, ALL, null, Map.of()))
                .path("data").path("risk_level").asText()).isEqualTo("LOW");
        JsonNode risky = ok(post("/api/service/v1/lowcode/apps/" + appId + "/scripts", TENANT, USER, ALL, null,
                Map.of("script_key", "risky", "name", "危险脚本", "content", "require('child_process').exec('rm -rf /')")));
        long riskyId = risky.path("data").path("id").asLong();
        assertThat(post("/api/service/v1/lowcode/scripts/" + riskyId + "/scan", TENANT, USER, ALL, null, Map.of())
                .getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(ok(get("/api/service/v1/lowcode/templates", TENANT, USER, ALL)).path("data")).isNotEmpty();
    }

    @Test
    void miningAndRpaChain() throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        for (String caseId : List.of("CASE-1", "CASE-2")) {
            events.add(event(caseId, "接单", "RECEIVE", "2026-11-01T09:00:00Z", "agent-a"));
            events.add(event(caseId, "派单", "ASSIGN", "2026-11-01T09:02:00Z", "agent-a"));
            events.add(event(caseId, "处理", "HANDLE", "2026-11-01T09:12:00Z", "agent-b"));
            events.add(event(caseId, "解决", "RESOLVE", "2026-11-01T09:22:00Z", "agent-b"));
            events.add(event(caseId, "关闭", "CLOSE", "2026-11-01T09:25:00Z", "agent-a"));
        }
        JsonNode ingested = ok(post("/api/service/v1/mining/events", TENANT, USER, ALL, null,
                Map.of("events", events)));
        assertThat(ingested.path("data").path("inserted").asInt()).isEqualTo(10);

        JsonNode discovered = ok(post("/api/service/v1/mining/models/discover", TENANT, USER, ALL, null,
                Map.of("period_start", "2026-11-01T00:00:00Z", "period_end", "2026-11-02T00:00:00Z",
                        "name", "工单处理模型")));
        long modelId = discovered.path("data").path("id").asLong();
        assertThat(discovered.path("data").path("metrics_json").path("case_count").asInt()).isEqualTo(2);
        assertThat(discovered.path("data").path("metrics_json").path("event_count").asInt()).isEqualTo(10);

        JsonNode conformance = ok(post("/api/service/v1/mining/models/" + modelId + "/conformance", TENANT, USER, ALL,
                null, Map.of()));
        assertThat(conformance.path("data").path("fitness").asDouble()).isEqualTo(1.0);
        assertThat(ok(get("/api/service/v1/mining/models/" + modelId + "/bottlenecks", TENANT, USER, ALL))
                .path("data").path("bottlenecks")).isNotEmpty();
        assertThat(ok(get("/api/service/v1/mining/models/" + modelId + "/resource-load", TENANT, USER, ALL))
                .path("data").path("resources")).isNotEmpty();

        JsonNode candidates = ok(get("/api/service/v1/mining/models/" + modelId + "/candidates", TENANT, USER, ALL));
        assertThat(candidates.path("data")).isNotEmpty();
        long candidateId = candidates.path("data").get(0).path("id").asLong();
        JsonNode automation = ok(post("/api/service/v1/mining/candidates/" + candidateId + "/to-automation", TENANT,
                USER, ALL, null, Map.of()));
        long automationId = automation.path("data").path("id").asLong();
        assertThat(automation.path("data").path("status").asText()).isEqualTo("DRAFT");
        assertThat(ok(post("/api/service/v1/rpa/automations/" + automationId + "/publish", TENANT, USER, ALL, null,
                Map.of())).path("data").path("status").asText()).isEqualTo("PUBLISHED");

        JsonNode robot = ok(post("/api/service/v1/rpa/robots", TENANT, USER, ALL, null,
                Map.of("robot_code", "R-01", "name", "财务机器人", "robot_type", "DESKTOP", "status", "ONLINE",
                        "capabilities", List.of("EXCEL"))));
        long robotId = robot.path("data").path("id").asLong();

        JsonNode credential = ok(post("/api/service/v1/rpa/credentials", TENANT, USER, ALL, null,
                Map.of("name", "SAP 账号", "cred_type", "PASSWORD", "target_system", "SAP", "secret", "S3cret!")));
        assertThat(credential.path("data").path("secret_masked").asText()).isEqualTo("******");
        long credentialId = credential.path("data").path("id").asLong();
        JsonNode token = ok(post("/api/service/v1/rpa/credentials/" + credentialId + "/token", TENANT, USER, ALL,
                null, Map.of()));
        assertThat(token.path("data").path("token").asText()).isNotBlank();
        assertThat(ok(post("/api/service/v1/internal/rpa/credentials/redeem", TENANT, USER, ALL, null,
                Map.of("token", token.path("data").path("token").asText())))
                .path("data").path("valid").asBoolean()).isTrue();

        Map<String, Object> taskBody = Map.of("automation_flow_id", automationId, "robot_id", robotId,
                "priority", 3, "input", Map.of("order_no", "SO-1"), "max_retry", 2);
        JsonNode task = ok(post("/api/service/v1/rpa/tasks", TENANT, USER, ALL, "rpa-idem-1", taskBody));
        long taskId = task.path("data").path("id").asLong();
        assertThat(task.path("data").path("task_no").asText()).startsWith("RPA");
        assertThat(ok(post("/api/service/v1/rpa/tasks", TENANT, USER, ALL, "rpa-idem-1", taskBody))
                .path("data").path("id").asLong()).isEqualTo(taskId);

        JsonNode running = ok(post("/api/service/v1/internal/rpa/tasks/" + taskId + "/callback", TENANT, USER, ALL,
                null, Map.of("status", "RUNNING")));
        assertThat(running.path("data").path("status").asText()).isEqualTo("RUNNING");
        JsonNode failed = ok(post("/api/service/v1/internal/rpa/tasks/" + taskId + "/callback", TENANT, USER, ALL,
                null, Map.of("status", "FAILED", "error_code", "E01", "error_msg", "SAP 超时",
                        "logs", List.of(Map.of("step", "login", "message", "登录失败")))));
        assertThat(failed.path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(ok(get("/api/service/v1/rpa/tasks/" + taskId + "/logs", TENANT, USER, ALL)).path("data"))
                .hasSize(1);
        assertThat(ok(post("/api/service/v1/rpa/tasks/" + taskId + "/retry", TENANT, USER, ALL, null, Map.of()))
                .path("data").path("status").asText()).isEqualTo("QUEUED");
        assertThat(ok(get("/api/service/v1/reports/rpa", TENANT, USER, ALL)).path("data")).isNotNull();
        assertThat(ok(get("/api/service/v1/reports/robot-utilization", TENANT, USER, ALL)).path("data")).isNotNull();
    }

    @Test
    void adminConfigAndCatalogManagement() throws Exception {
        JsonNode saved = ok(put("/api/service/v1/admin/config/ticket-types", TENANT, USER, ALL,
                Map.of("types", List.of(Map.of("code", "INCIDENT", "name", "事件"),
                        Map.of("code", "REQUEST", "name", "请求")))));
        assertThat(saved.path("data").path("saved").asBoolean()).isTrue();
        assertThat(ok(get("/api/service/v1/admin/config/TICKET_TYPE", TENANT, USER, ALL)).path("data")).hasSize(1);

        JsonNode catalog = ok(post("/api/service/v1/admin/catalog", TENANT, USER, ALL, null, Map.of(
                "catalogs", List.of(Map.of("name", "办公服务", "icon", "office")),
                "items", List.of(Map.of("catalog_id", 900101, "code", "IT-OFFICE", "name", "办公软件",
                        "ticket_type", "REQUEST", "sla_policy_id", 900002)))));
        assertThat(catalog.path("data").path("catalogs").asInt()).isEqualTo(1);
        assertThat(catalog.path("data").path("items").asInt()).isEqualTo(1);

        JsonNode policy = ok(post("/api/service/v1/admin/sla-policies", TENANT, USER, ALL, null, Map.of(
                "name", "租户 P2", "priority", "P2", "ticket_type", "INCIDENT",
                "response_minutes", 30, "resolve_minutes", 240)));
        assertThat(policy.path("data").path("id").asLong()).isPositive();
        assertThat(ok(get("/api/service/v1/admin/sla-policies", TENANT, USER, ALL)).path("data")).isNotEmpty();
        assertThat(AUDIT_EVENTS).anyMatch(event -> "config.save".equals(event.action()));
    }

    private Map<String, Object> event(String caseId, String activity, String code, String time, String resource) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("case_id", caseId);
        event.put("activity", activity);
        event.put("activity_code", code);
        event.put("event_time", time);
        event.put("resource", resource);
        event.put("source_system", "ITSM");
        return event;
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

    private JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private JsonNode ok(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .withFailMessage("期望 2xx，实际 %s：%s", response.getStatusCode(), response.getBody())
                .isTrue();
        JsonNode root = read(response.getBody());
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
