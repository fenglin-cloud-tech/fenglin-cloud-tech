package com.fenglin.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.workflow.service.WorkflowCallbackService;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流中心集成测试：真实 PostgreSQL + 全量迁移 + 真实 Flowable 引擎。
 * 覆盖定义部署/查询、发起→办理→结束、驳回、转办、终止、回调幂等与重试、审计与监控统计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowFlowIntegrationTest {

    private static final long TENANT_DEF = 1401L;
    private static final long TENANT_FLOW = 1411L;
    private static final long TENANT_REJECT = 1412L;
    private static final long TENANT_TRANSFER = 1413L;
    private static final long TENANT_TERMINATE = 1414L;
    private static final long TENANT_CALLBACK = 1415L;
    private static final long TENANT_RETRY = 1416L;
    private static final long TENANT_OTHER = 1499L;

    private static final long USER_INITIATOR = 5401L;
    private static final long USER_APPROVER = 5402L;
    private static final long USER_OTHER = 5403L;

    private static final String DEF_PERMISSION = "tenant:workflow:def:manage";
    private static final String MONITOR_PERMISSION = "tenant:workflow:monitor:view";
    private static final String START_PERMISSION = "biz:workflow:instance:start";
    private static final String VIEW_PERMISSION = "biz:workflow:instance:view";
    private static final String HANDLE_PERMISSION = "biz:workflow:task:handle";

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
    }

    @TestConfiguration
    static class RecordingAuditConfig {

        @Bean
        @Primary
        AuditSink recordingAuditSink() {
            return AUDIT_EVENTS::add;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowCallbackService callbackService;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        AUDIT_EVENTS.clear();
    }

    // ---------- 里程碑 1：流程定义可用 ----------

    @Test
    void deploysDefinitionVersionsAndListsWithinTenant() {
        HttpResult forbidden = exchange(HttpMethod.POST, "/api/hub/v1/admin/workflow/definitions",
                deployBody("leave_apply", "leave_apply"), TENANT_DEF, USER_INITIATOR, null);
        assertThat(forbidden.status()).isEqualTo(403);

        HttpResult first = deploy(TENANT_DEF, "leave_apply", "leave_apply");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("data").get("process_key").asText()).isEqualTo("leave_apply");
        assertThat(first.body().get("data").get("def_version").asInt()).isEqualTo(1);
        assertThat(first.body().get("data").get("status").asText()).isEqualTo("PUBLISHED");

        HttpResult listA = listDefinitions(TENANT_DEF, null);
        assertThat(listA.status()).isEqualTo(200);
        assertThat(listA.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(listA.body().get("data").get("records").get(0).get("process_key").asText())
                .isEqualTo("leave_apply");

        HttpResult listOther = listDefinitions(TENANT_OTHER, null);
        assertThat(listOther.body().get("data").get("total").asLong()).isZero();

        HttpResult second = deploy(TENANT_DEF, "leave_apply", "leave_apply");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().get("data").get("def_version").asInt()).isEqualTo(2);

        HttpResult published = listDefinitions(TENANT_DEF, "PUBLISHED");
        assertThat(published.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(published.body().get("data").get("records").get(0).get("def_version").asInt()).isEqualTo(2);

        assertThat(listDefinitions(TENANT_DEF, null).body().get("data").get("total").asLong()).isEqualTo(2);

        HttpResult mismatch = deploy(TENANT_DEF, "leave_apply", "another_key");
        assertThat(mismatch.status()).isEqualTo(400);
        assertThat(mismatch.body().get("message").asText()).contains("不一致");

        assertThat(auditCount(TENANT_DEF, "WF_DEFINITION_DEPLOYED")).isEqualTo(2);
    }

    // ---------- 里程碑 2：实例与任务链路 ----------

    @Test
    void startsApprovesAndCompletesInstance() {
        assertThat(deploy(TENANT_FLOW, "leave_apply", "leave_apply").status()).isEqualTo(200);

        HttpResult started = start(TENANT_FLOW, USER_INITIATOR, "leave_apply", "张三的请假申请");
        assertThat(started.status()).isEqualTo(200);
        long instanceId = started.body().get("data").get("instance_id").asLong();
        String taskId = started.body().get("data").get("first_task_id").asText();
        assertThat(started.body().get("data").get("status").asText()).isEqualTo("RUNNING");
        assertThat(taskId).isNotBlank();

        HttpResult pendingApprover = pending(TENANT_FLOW, USER_APPROVER, VIEW_PERMISSION);
        assertThat(pendingApprover.status()).isEqualTo(200);
        assertThat(pendingApprover.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(pendingApprover.body().get("data").get("records").get(0).get("task_id").asLong())
                .isEqualTo(Long.parseLong(taskId));
        assertThat(pendingApprover.body().get("data").get("records").get(0).get("title").asText())
                .isEqualTo("张三的请假申请");

        assertThat(pending(TENANT_FLOW, USER_INITIATOR, VIEW_PERMISSION)
                .body().get("data").get("total").asLong()).isZero();
        assertThat(pending(TENANT_FLOW, USER_APPROVER, null).status()).isEqualTo(403);

        HttpResult handled = handle(TENANT_FLOW, USER_APPROVER, taskId, "APPROVE", "同意", null);
        assertThat(handled.status()).isEqualTo(200);
        assertThat(handled.body().get("data").get("status").asText()).isEqualTo("DONE");
        assertThat(handled.body().get("data").get("instance_status").asText()).isEqualTo("COMPLETED");

        HttpResult detail = detail(TENANT_FLOW, USER_INITIATOR, instanceId);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().get("data").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.body().get("data").get("result").asText()).isEqualTo("APPROVED");
        assertThat(detail.body().get("data").get("ended_at").asText()).isNotBlank();
        JsonNode nodes = detail.body().get("data").get("nodes");
        assertThat(nodes).isNotEmpty();
        JsonNode approveNode = null;
        for (JsonNode node : nodes) {
            if ("leaderApprove".equals(node.get("node_key").asText())) {
                approveNode = node;
            }
        }
        assertThat(approveNode).isNotNull();
        assertThat(approveNode.get("status").asText()).isEqualTo("DONE");
        assertThat(approveNode.get("handler_id").asLong()).isEqualTo(USER_APPROVER);
        assertThat(approveNode.get("comment").asText()).isEqualTo("同意");

        HttpResult duplicate = handle(TENANT_FLOW, USER_APPROVER, taskId, "APPROVE", "同意", null);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("message").asText()).contains("0004002");

        assertThat(detail(TENANT_OTHER, USER_INITIATOR, instanceId).status()).isEqualTo(404);

        assertThat(auditCount(TENANT_FLOW, "WF_INSTANCE_STARTED")).isEqualTo(1);
        assertThat(auditCount(TENANT_FLOW, "WF_TASK_APPROVED")).isEqualTo(1);
        assertThat(auditCount(TENANT_FLOW, "WF_INSTANCE_COMPLETED")).isEqualTo(1);
    }

    @Test
    void rejectsTaskAndEndsInstanceWithoutPendingTask() {
        assertThat(deploy(TENANT_REJECT, "leave_apply", "leave_apply").status()).isEqualTo(200);
        HttpResult started = start(TENANT_REJECT, USER_INITIATOR, "leave_apply", "驳回测试");
        long instanceId = started.body().get("data").get("instance_id").asLong();
        String taskId = started.body().get("data").get("first_task_id").asText();

        HttpResult rejected = handle(TENANT_REJECT, USER_APPROVER, taskId, "REJECT", "资料不全", null);
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().get("data").get("instance_status").asText()).isEqualTo("COMPLETED");

        HttpResult detail = detail(TENANT_REJECT, USER_INITIATOR, instanceId);
        assertThat(detail.body().get("data").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.body().get("data").get("result").asText()).isEqualTo("REJECTED");
        assertThat(pending(TENANT_REJECT, USER_APPROVER, VIEW_PERMISSION)
                .body().get("data").get("total").asLong()).isZero();
        assertThat(auditCount(TENANT_REJECT, "WF_TASK_REJECTED")).isEqualTo(1);
    }

    @Test
    void transfersTaskToAnotherAssignee() {
        assertThat(deploy(TENANT_TRANSFER, "leave_apply", "leave_apply").status()).isEqualTo(200);
        HttpResult started = start(TENANT_TRANSFER, USER_INITIATOR, "leave_apply", "转办测试");
        String taskId = started.body().get("data").get("first_task_id").asText();

        HttpResult notAssignee = handle(TENANT_TRANSFER, USER_OTHER, taskId, "APPROVE", "越权", null);
        assertThat(notAssignee.status()).isEqualTo(403);

        HttpResult transferred = handle(TENANT_TRANSFER, USER_APPROVER, taskId, "TRANSFER", "转给李四", USER_OTHER);
        assertThat(transferred.status()).isEqualTo(200);
        assertThat(transferred.body().get("data").get("status").asText()).isEqualTo("PENDING");

        assertThat(pending(TENANT_TRANSFER, USER_APPROVER, VIEW_PERMISSION)
                .body().get("data").get("total").asLong()).isZero();
        HttpResult pendingOther = pending(TENANT_TRANSFER, USER_OTHER, VIEW_PERMISSION);
        assertThat(pendingOther.body().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(pendingOther.body().get("data").get("records").get(0).get("task_id").asLong())
                .isEqualTo(Long.parseLong(taskId));

        HttpResult approved = handle(TENANT_TRANSFER, USER_OTHER, taskId, "APPROVE", "已接手同意", null);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().get("data").get("instance_status").asText()).isEqualTo("COMPLETED");
        assertThat(auditCount(TENANT_TRANSFER, "WF_TASK_TRANSFERRED")).isEqualTo(1);
    }

    @Test
    void terminatesInstanceOnlyByInitiator() {
        assertThat(deploy(TENANT_TERMINATE, "leave_apply", "leave_apply").status()).isEqualTo(200);
        HttpResult started = start(TENANT_TERMINATE, USER_INITIATOR, "leave_apply", "终止测试");
        long instanceId = started.body().get("data").get("instance_id").asLong();
        String taskId = started.body().get("data").get("first_task_id").asText();

        HttpResult forbidden = exchange(HttpMethod.POST,
                "/api/hub/v1/workflow/instances/" + instanceId + ":terminate",
                Map.of("reason", "越权终止"), TENANT_TERMINATE, USER_OTHER, START_PERMISSION);
        assertThat(forbidden.status()).isEqualTo(403);

        HttpResult terminated = exchange(HttpMethod.POST,
                "/api/hub/v1/workflow/instances/" + instanceId + ":terminate",
                Map.of("reason", "用户撤回"), TENANT_TERMINATE, USER_INITIATOR, START_PERMISSION);
        assertThat(terminated.status()).isEqualTo(200);
        assertThat(terminated.body().get("data").get("status").asText()).isEqualTo("TERMINATED");
        assertThat(terminated.body().get("data").get("result").asText()).isEqualTo("CANCELLED");

        HttpResult detail = detail(TENANT_TERMINATE, USER_INITIATOR, instanceId);
        assertThat(detail.body().get("data").get("status").asText()).isEqualTo("TERMINATED");
        assertThat(pending(TENANT_TERMINATE, USER_APPROVER, VIEW_PERMISSION)
                .body().get("data").get("total").asLong()).isZero();

        HttpResult again = exchange(HttpMethod.POST,
                "/api/hub/v1/workflow/instances/" + instanceId + ":terminate",
                Map.of("reason", "重复终止"), TENANT_TERMINATE, USER_INITIATOR, START_PERMISSION);
        assertThat(again.status()).isEqualTo(409);

        HttpResult staleHandle = handle(TENANT_TERMINATE, USER_APPROVER, taskId, "APPROVE", "已终止", null);
        assertThat(staleHandle.status()).isEqualTo(409);
        assertThat(auditCount(TENANT_TERMINATE, "WF_INSTANCE_TERMINATED")).isEqualTo(1);
    }

    // ---------- 里程碑 3：回调与审计 ----------

    @Test
    void deliversCallbackIdempotentlyAndServesMonitorStats() throws Exception {
        HttpServer server = startCallbackServer();
        try {
            String callbackUrl = "http://localhost:" + server.getAddress().getPort() + "/ok";
            assertThat(deploy(TENANT_CALLBACK, "leave_apply", "leave_apply").status()).isEqualTo(200);
            insertCallback(TENANT_CALLBACK, "leave_apply", callbackUrl, "PROCESS_COMPLETED", 1);

            HttpResult started = start(TENANT_CALLBACK, USER_INITIATOR, "leave_apply", "回调测试");
            long instanceId = started.body().get("data").get("instance_id").asLong();
            String instanceNo = started.body().get("data").get("instance_no").asText();
            String taskId = started.body().get("data").get("first_task_id").asText();

            HttpResult handled = handle(TENANT_CALLBACK, USER_APPROVER, taskId, "APPROVE", "同意", null);
            assertThat(handled.status()).isEqualTo(200);

            assertThat(CALLBACK_OK.get()).isEqualTo(1);
            assertThat(CALLBACK_EVENT.get()).isEqualTo("PROCESS_COMPLETED");
            assertThat(CALLBACK_BODY.get()).contains(instanceNo);

            Map<String, Object> log = callbackLog(TENANT_CALLBACK, instanceId, "PROCESS_COMPLETED");
            assertThat(log.get("status")).isEqualTo("SENT");
            assertThat(((Number) log.get("attempts")).intValue()).isEqualTo(1);
            assertThat(((Number) log.get("http_status")).intValue()).isEqualTo(200);

            TenantContext.set(TENANT_CALLBACK, USER_INITIATOR, "PORTAL", "test-idempotent", false);
            try {
                callbackService.dispatch(TENANT_CALLBACK, instanceId, "PROCESS_COMPLETED");
            } finally {
                TenantContext.clear();
            }
            assertThat(CALLBACK_OK.get()).isEqualTo(1);

            HttpResult stats = exchange(HttpMethod.GET,
                    "/api/hub/v1/admin/workflow/monitor/stats", null, TENANT_CALLBACK,
                    USER_INITIATOR, MONITOR_PERMISSION);
            assertThat(stats.status()).isEqualTo(200);
            assertThat(stats.body().get("data").get("completed").asLong()).isGreaterThanOrEqualTo(1);
            assertThat(stats.body().get("data").get("by_node")).isNotEmpty();
            assertThat(exchange(HttpMethod.GET, "/api/hub/v1/admin/workflow/monitor/stats",
                    null, TENANT_CALLBACK, USER_INITIATOR, null).status()).isEqualTo(403);

            assertThat(auditCount(TENANT_CALLBACK, "WF_CALLBACK_DELIVERED")).isEqualTo(1);
            assertThat(auditCount(TENANT_CALLBACK, "WF_INSTANCE_COMPLETED")).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesCallbackAndMarksFailureInAudit() throws Exception {
        HttpServer server = startCallbackServer();
        try {
            String callbackUrl = "http://localhost:" + server.getAddress().getPort() + "/fail";
            assertThat(deploy(TENANT_RETRY, "leave_apply", "leave_apply").status()).isEqualTo(200);
            insertCallback(TENANT_RETRY, "leave_apply", callbackUrl, "PROCESS_COMPLETED", 1);

            HttpResult started = start(TENANT_RETRY, USER_INITIATOR, "leave_apply", "重试测试");
            long instanceId = started.body().get("data").get("instance_id").asLong();
            String taskId = started.body().get("data").get("first_task_id").asText();

            HttpResult handled = handle(TENANT_RETRY, USER_APPROVER, taskId, "APPROVE", "同意", null);
            assertThat(handled.status()).isEqualTo(200);

            assertThat(CALLBACK_FAIL.get()).isEqualTo(2);
            Map<String, Object> log = callbackLog(TENANT_RETRY, instanceId, "PROCESS_COMPLETED");
            assertThat(log.get("status")).isEqualTo("FAILED");
            assertThat(((Number) log.get("attempts")).intValue()).isEqualTo(2);
            assertThat(auditCount(TENANT_RETRY, "WF_CALLBACK_FAILED")).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    // ---------- 测试辅助 ----------

    private static final AtomicInteger CALLBACK_OK = new AtomicInteger();
    private static final AtomicInteger CALLBACK_FAIL = new AtomicInteger();
    private static final AtomicReference<String> CALLBACK_EVENT = new AtomicReference<>();
    private static final AtomicReference<String> CALLBACK_BODY = new AtomicReference<>();

    private HttpServer startCallbackServer() throws Exception {
        CALLBACK_OK.set(0);
        CALLBACK_FAIL.set(0);
        CALLBACK_EVENT.set(null);
        CALLBACK_BODY.set(null);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            CALLBACK_OK.incrementAndGet();
            CALLBACK_EVENT.set(exchange.getRequestHeaders().getFirst("X-Fenglin-Event"));
            CALLBACK_BODY.set(body);
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.getRequestBody().readAllBytes();
            CALLBACK_FAIL.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private void insertCallback(long tenantId, String processKey, String url, String events, int maxRetry)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO fenglin_hub.hub_wf_callback "
                             + "(id, tenant_id, process_key, callback_url, events, timeout_ms, max_retry, enabled) "
                             + "VALUES (?, ?, ?, ?, ?, 1000, ?, 1)")) {
            statement.setLong(1, Math.abs(System.nanoTime()));
            statement.setLong(2, tenantId);
            statement.setString(3, processKey);
            statement.setString(4, url);
            statement.setString(5, events);
            statement.setInt(6, maxRetry);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> callbackLog(long tenantId, long instanceId, String action) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, attempts, http_status FROM fenglin_hub.hub_wf_callback_log "
                             + "WHERE tenant_id = ? AND instance_id = ? AND action = ?")) {
            statement.setLong(1, tenantId);
            statement.setLong(2, instanceId);
            statement.setString(3, action);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", rs.getString("status"));
                row.put("attempts", rs.getInt("attempts"));
                row.put("http_status", rs.getInt("http_status"));
                return row;
            }
        }
    }

    private long auditCount(long tenantId, String eventType) {
        return AUDIT_EVENTS.stream()
                .filter(event -> Long.valueOf(tenantId).equals(event.tenantId()))
                .filter(event -> eventType.equals(event.eventType()))
                .count();
    }

    private String bpmn(String processKey) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://fenglin.com/workflow">
                  <process id="%s" name="请假申请" isExecutable="true">
                    <startEvent id="start" name="发起"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="leaderApprove"/>
                    <userTask id="leaderApprove" name="直属领导审批" flowable:assignee="${approverId}"/>
                    <sequenceFlow id="flow2" sourceRef="leaderApprove" targetRef="end"/>
                    <endEvent id="end" name="结束"/>
                  </process>
                </definitions>
                """.formatted(processKey);
    }

    private Map<String, Object> deployBody(String processKey, String bpmnProcessId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("process_key", processKey);
        body.put("name", "请假申请");
        body.put("category", "HR");
        body.put("bpmn_xml", bpmn(bpmnProcessId));
        body.put("form_key", "leave_form");
        return body;
    }

    private HttpResult deploy(long tenantId, String processKey, String bpmnProcessId) {
        return exchange(HttpMethod.POST, "/api/hub/v1/admin/workflow/definitions",
                deployBody(processKey, bpmnProcessId), tenantId, USER_INITIATOR, DEF_PERMISSION);
    }

    private HttpResult listDefinitions(long tenantId, String status) {
        String path = "/api/hub/v1/admin/workflow/definitions?page=1&size=20"
                + (status == null ? "" : "&status=" + status);
        return exchange(HttpMethod.GET, path, null, tenantId, USER_INITIATOR, DEF_PERMISSION);
    }

    private HttpResult start(long tenantId, long userId, String processKey, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("process_key", processKey);
        body.put("business_key", "BIZ-" + System.nanoTime());
        body.put("business_type", "leave.apply");
        body.put("title", title);
        body.put("form_data", Map.of("approverId", String.valueOf(USER_APPROVER), "days", 2));
        return exchange(HttpMethod.POST, "/api/hub/v1/workflow/instances",
                body, tenantId, userId, START_PERMISSION);
    }

    private HttpResult pending(long tenantId, long userId, String permission) {
        return exchange(HttpMethod.GET, "/api/hub/v1/workflow/pending?page=1&size=20",
                null, tenantId, userId, permission);
    }

    private HttpResult detail(long tenantId, long userId, long instanceId) {
        return exchange(HttpMethod.GET, "/api/hub/v1/workflow/instances/" + instanceId,
                null, tenantId, userId, VIEW_PERMISSION);
    }

    private HttpResult handle(long tenantId, long userId, String taskId, String action,
                              String comment, Long assigneeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        if (comment != null) {
            body.put("comment", comment);
        }
        if (assigneeId != null) {
            body.put("assignee_id", assigneeId);
        }
        return exchange(HttpMethod.POST, "/api/hub/v1/workflow/tasks/" + taskId + ":handle",
                body, tenantId, userId, HANDLE_PERMISSION);
    }

    private HttpResult exchange(HttpMethod method, String path, Object body,
                                long tenantId, Long userId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
        }
        if (permissions != null) {
            headers.set("X-Permissions", permissions);
        }
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path,
                method, new HttpEntity<>(body, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
