package com.fenglin.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息中心集成测试：真实 PostgreSQL + 全量迁移，覆盖模板渲染、去重、偏好过滤、已读与租户隔离。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessageFlowIntegrationTest {

    private static final long TENANT_A = 1301L;
    private static final long TENANT_B = 1302L;
    private static final long USER_A = 5301L;
    private static final long USER_B = 5302L;
    private static final String JDBC_URL;

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
        registry.add("fenglin.message.retry-job-enabled", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_message, fenglin_hub.hub_message_template, "
                    + "fenglin_hub.hub_message_receipt, fenglin_hub.hub_message_preference");
        }
    }

    @Test
    void sendsStationAndWsThenReadsWithUnreadCount() {
        createTemplate(TENANT_A, "approval_todo", "STATION");

        HttpResult sent = send(Map.of(
                "template_code", "approval_todo",
                "channels", List.of("STATION", "WS"),
                "receivers", List.of(USER_A),
                "params", Map.of("title", "报价审批"),
                "biz_type", "workflow.task",
                "biz_id", "WF202610000001"), TENANT_A);
        assertThat(sent.status()).isEqualTo(200);
        assertThat(sent.body().get("data").get("status").asText()).isEqualTo("PARTIAL");
        assertThat(sent.body().get("data").get("message_ids")).hasSize(2);
        assertThat(sent.body().get("data").get("failed").asInt()).isEqualTo(1);

        HttpResult list = get("/api/hub/v1/messages", TENANT_A, USER_A);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get("data").get("records")).hasSize(2);
        assertThat(list.body().get("data").get("unread_count").asInt()).isEqualTo(2);
        assertThat(list.body().get("data").get("records").get(0).get("title").asText())
                .isEqualTo("【待办】报价审批");

        long stationId = findMessageId(list, "STATION");
        HttpResult read = post("/api/hub/v1/messages/" + stationId + ":read", null, TENANT_A, USER_A);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body().get("data").get("status").asText()).isEqualTo("READ");

        HttpResult afterRead = get("/api/hub/v1/messages", TENANT_A, USER_A);
        assertThat(afterRead.body().get("data").get("unread_count").asInt()).isEqualTo(1);

        HttpResult otherUser = get("/api/hub/v1/messages", TENANT_A, USER_B);
        assertThat(otherUser.body().get("data").get("records")).isEmpty();

        HttpResult otherTenant = get("/api/hub/v1/messages", TENANT_B, USER_A);
        assertThat(otherTenant.body().get("data").get("records")).isEmpty();
    }

    @Test
    void deduplicatesByTemplateBizIdAndReceiverWithinWindow() {
        createTemplate(TENANT_A, "approval_todo", "STATION");
        Map<String, Object> payload = Map.of(
                "template_code", "approval_todo",
                "channels", List.of("STATION"),
                "receivers", List.of(USER_A),
                "params", Map.of("title", "报价审批"),
                "biz_type", "workflow.task",
                "biz_id", "WF-DUP-1");
        HttpResult first = send(payload, TENANT_A);
        HttpResult second = send(payload, TENANT_A);
        assertThat(first.body().get("data").get("message_ids")).hasSize(1);
        assertThat(second.body().get("data").get("message_ids")).hasSize(1);
        assertThat(second.body().get("data").get("message_ids").get(0).asLong())
                .isEqualTo(first.body().get("data").get("message_ids").get(0).asLong());
        HttpResult list = get("/api/hub/v1/messages", TENANT_A, USER_A);
        assertThat(list.body().get("data").get("records")).hasSize(1);
    }

    @Test
    void missingTemplateVariableIsRejected() {
        createTemplate(TENANT_A, "approval_todo", "STATION");
        HttpResult result = send(Map.of(
                "template_code", "approval_todo",
                "channels", List.of("STATION"),
                "receivers", List.of(USER_A),
                "params", Map.of()), TENANT_A);
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().get("message").asText()).contains("0005001");
    }

    @Test
    void unsupportedChannelIsRejected() {
        createTemplate(TENANT_A, "approval_todo", "STATION");
        HttpResult result = send(Map.of(
                "template_code", "approval_todo",
                "channels", List.of("EMAIL"),
                "receivers", List.of(USER_A),
                "params", Map.of("title", "x")), TENANT_A);
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().get("message").asText()).contains("通道未实现");
    }

    @Test
    void preferenceSuppressesUnselectedChannelAndRequiresPermission() {
        createTemplate(TENANT_A, "approval_todo", "STATION");
        HttpResult forbidden = exchange(HttpMethod.PUT, "/api/hub/v1/messages/preferences",
                Map.of("items", List.of(Map.of("event_type", "workflow.task",
                        "channels", List.of("WS")))), TENANT_A, USER_A, null);
        assertThat(forbidden.status()).isEqualTo(403);

        HttpResult updated = exchange(HttpMethod.PUT, "/api/hub/v1/messages/preferences",
                Map.of("items", List.of(Map.of("event_type", "workflow.task",
                        "channels", List.of("WS")))), TENANT_A, USER_A, "biz:message:preference:manage");
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("data").get(0).get("channels").get(0).asText()).isEqualTo("WS");

        HttpResult sent = send(Map.of(
                "template_code", "approval_todo",
                "channels", List.of("STATION"),
                "receivers", List.of(USER_A),
                "params", Map.of("title", "报价审批"),
                "biz_type", "workflow.task"), TENANT_A);
        assertThat(sent.body().get("data").get("message_ids")).isEmpty();
        assertThat(sent.body().get("data").get("suppressed").asInt()).isEqualTo(1);
    }

    @Test
    void templateManagementRequiresPermissionAndIsTenantIsolated() {
        HttpResult forbidden = exchange(HttpMethod.POST, "/api/hub/v1/admin/message/templates",
                templateBody("approval_todo"), TENANT_A, USER_A, null);
        assertThat(forbidden.status()).isEqualTo(403);

        createTemplate(TENANT_B, "approval_todo", "STATION");
        HttpResult listA = get("/api/hub/v1/admin/message/templates", TENANT_A, USER_A);
        assertThat(listA.body().get("data")).isEmpty();
        HttpResult listB = get("/api/hub/v1/admin/message/templates", TENANT_B, USER_A);
        assertThat(listB.body().get("data")).hasSize(1);
    }

    @Test
    void monthlyPartitionIsCreatedByMaintainer() throws Exception {
        String suffix = java.time.YearMonth.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery(
                     "SELECT to_regclass('fenglin_hub.hub_message_" + suffix + "') IS NOT NULL")) {
            rs.next();
            assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    private void createTemplate(long tenantId, String code, String channel) {
        HttpResult result = exchange(HttpMethod.POST, "/api/hub/v1/admin/message/templates",
                templateBody(code), tenantId, USER_A, "tenant:message:template:manage");
        assertThat(result.status()).isEqualTo(200);
    }

    private Map<String, Object> templateBody(String code) {
        return Map.of(
                "code", code,
                "name", "审批待办",
                "channel", "STATION",
                "title_tpl", "【待办】${title}",
                "content_tpl", "您有新的审批任务：${title}",
                "variables", List.of("title"));
    }

    private long findMessageId(HttpResult list, String channel) {
        for (JsonNode node : list.body().get("data").get("records")) {
            if (channel.equals(node.get("channel").asText())) {
                return node.get("message_id").asLong();
            }
        }
        throw new AssertionError("未找到通道消息: " + channel);
    }

    private HttpResult send(Map<String, Object> body, long tenantId) {
        return exchange(HttpMethod.POST, "/internal/v1/message/send", body, tenantId, null, null);
    }

    private HttpResult get(String path, long tenantId, long userId) {
        return exchange(HttpMethod.GET, path, null, tenantId, userId, null);
    }

    private HttpResult post(String path, Object body, long tenantId, long userId) {
        return exchange(HttpMethod.POST, path, body, tenantId, userId, null);
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
