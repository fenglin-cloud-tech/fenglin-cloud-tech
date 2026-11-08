package com.fenglin.collab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.collab.client.WorkflowClient;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云协主链端到端集成测试：真实 PostgreSQL（独立 schema fenglin_collab + RLS）+ 真实服务端口。
 *
 * <p>覆盖 IM → 文档 → 日历 → 会议 → OKR → 审批 → 工作台主链、租户隔离、消息幂等、
 * 会议室时段冲突、OKR 对齐防环、审批幂等与撤销。</p>
 *
 * <p>工作流中心在测试中以 @Primary 替身记录提单调用，审批引擎本身由工作流服务集成测试覆盖。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollabIntegrationTest {

    private static final long TENANT_A = 8701L;
    private static final long TENANT_B = 8702L;
    private static final long USER_OWNER = 5001L;
    private static final long USER_MEMBER = 5002L;
    private static final long USER_EDITOR = 5003L;

    private static final String ALL_PERMISSIONS = String.join(",",
            "biz:collab:im:chat", "biz:collab:im:group:manage",
            "biz:collab:doc:read", "biz:collab:doc:edit", "biz:collab:doc:comment", "biz:collab:doc:manage",
            "biz:collab:calendar:read", "biz:collab:calendar:manage",
            "biz:collab:meeting:join", "biz:collab:meeting:manage", "tenant:collab:meeting:room:manage",
            "biz:collab:okr:read", "biz:collab:okr:manage", "tenant:collab:okr:period:manage",
            "biz:collab:approval:read", "biz:collab:approval:submit", "biz:collab:approval:handle",
            "tenant:collab:approval:template:manage",
            "biz:collab:workbench:personal", "tenant:collab:workbench:template:manage");

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_collab")
                .defaultSchema("fenglin_collab")
                .locations("classpath:db/collab-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_collab");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_collab_app");
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
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        AUDIT_EVENTS.clear();
        WORKFLOW_CALLS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_collab.collab_conversation, "
                    + "fenglin_collab.collab_conversation_member, fenglin_collab.collab_message, "
                    + "fenglin_collab.collab_message_read, fenglin_collab.collab_message_reaction, "
                    + "fenglin_collab.collab_document, fenglin_collab.collab_document_version, "
                    + "fenglin_collab.collab_document_collaborator, fenglin_collab.collab_document_comment, "
                    + "fenglin_collab.collab_calendar, fenglin_collab.collab_event, "
                    + "fenglin_collab.collab_event_attendee, fenglin_collab.collab_meeting_room, "
                    + "fenglin_collab.collab_meeting, fenglin_collab.collab_meeting_minutes, "
                    + "fenglin_collab.collab_meeting_action, fenglin_collab.collab_meeting_checkin, "
                    + "fenglin_collab.collab_room_reservation, fenglin_collab.collab_okr_period, "
                    + "fenglin_collab.collab_okr_objective, fenglin_collab.collab_okr_key_result, "
                    + "fenglin_collab.collab_okr_alignment, fenglin_collab.collab_okr_progress, "
                    + "fenglin_collab.collab_approval_template, fenglin_collab.collab_approval_instance, "
                    + "fenglin_collab.collab_approval_task, fenglin_collab.collab_workbench_widget, "
                    + "fenglin_collab.collab_workbench_layout, fenglin_collab.collab_notification, "
                    + "fenglin_collab.collab_todo, fenglin_collab.collab_ona_graph, fenglin_collab.collab_ona_edge, "
                    + "fenglin_collab.collab_ona_metric, fenglin_collab.collab_ona_snapshot, "
                    + "fenglin_collab.collab_ona_config, fenglin_collab.collab_file, fenglin_collab.collab_setting");
        }
    }

    // ---------- IM 主链 ----------

    @Test
    void completesImMainChainWithIdempotencyCipherUnreadAndRevoke() throws Exception {
        JsonNode detail = read(post("/api/collab/v1/im/conversations", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("conv_type", "SINGLE", "member_ids", List.of(USER_MEMBER))).getBody()).get("data");
        String convId = detail.get("conversation").get("conversation_id").asText();
        assertThat(convId).isNotBlank();
        assertThat(detail.get("conversation").get("member_count").asInt()).isEqualTo(2);
        assertThat(detail.get("conversation").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(detail.get("members").size()).isEqualTo(2);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("client_msg_id", "msg-1");
        first.put("msg_type", "TEXT");
        first.put("content", "早会改到十点");
        JsonNode message = read(post("/api/collab/v1/im/conversations/" + convId + "/messages", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, first).getBody()).get("data");
        long messageId = message.get("message_id").asLong();
        assertThat(message.get("seq").asLong()).isEqualTo(1L);
        assertThat(message.get("summary").asText()).isEqualTo("早会改到十点");
        assertThat(message.get("revoked").asBoolean()).isFalse();

        JsonNode duplicated = read(post("/api/collab/v1/im/conversations/" + convId + "/messages", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, first).getBody()).get("data");
        assertThat(duplicated.get("message_id").asLong()).isEqualTo(messageId);
        assertThat(duplicated.get("seq").asLong()).isEqualTo(1L);

        assertThat(read(post("/api/collab/v1/im/conversations/" + convId + "/messages", TENANT_A,
                USER_MEMBER, ALL_PERMISSIONS, Map.of("client_msg_id", "msg-2", "content", "收到"))
                .getBody()).get("data").get("seq").asLong()).isEqualTo(2L);

        JsonNode third = read(post("/api/collab/v1/im/conversations/" + convId + "/messages", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("client_msg_id", "msg-3", "content", "项目周报已更新"))
                .getBody()).get("data");
        assertThat(third.get("seq").asLong()).isEqualTo(3L);

        JsonNode page = read(get("/api/collab/v1/im/conversations/" + convId + "/messages?size=50", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(page.get("records").size()).isEqualTo(3);
        assertThat(page.get("records").get(0).get("seq").asLong()).isEqualTo(1L);
        assertThat(page.get("next_cursor").asLong()).isEqualTo(1L);

        JsonNode unread = read(get("/api/collab/v1/im/conversations/" + convId, TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(unread.get("conversation").get("unread_count").asLong()).isEqualTo(1L);

        JsonNode readResult = read(post("/api/collab/v1/im/messages/" + third.get("message_id").asLong()
                + "/read?conversationId=" + convId, TENANT_A, USER_MEMBER, ALL_PERMISSIONS, Map.of())
                .getBody()).get("data");
        assertThat(readResult.get("unread_count").asLong()).isZero();

        assertThat(post("/api/collab/v1/im/messages/" + messageId + "/reactions", TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS, Map.of("emoji", "👍")).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> revokeByOther = post("/api/collab/v1/im/messages/" + messageId + "/revoke",
                TENANT_A, USER_MEMBER, ALL_PERMISSIONS, Map.of("reason", "无权撤回"));
        assertThat(revokeByOther.getStatusCode().value()).isEqualTo(403);
        assertThat(read(revokeByOther.getBody()).get("code").asText()).isEqualTo("2000002");

        assertThat(post("/api/collab/v1/im/messages/" + messageId + "/revoke", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("reason", "内容有误")).getStatusCode().value()).isEqualTo(200);

        JsonNode afterRevoke = read(get("/api/collab/v1/im/conversations/" + convId + "/messages?size=50",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(afterRevoke.get("records").get(0).get("revoked").asBoolean()).isTrue();

        JsonNode searched = read(get("/api/collab/v1/im/messages/search?conversationId=" + convId
                + "&keyword=周报", TENANT_A, USER_OWNER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(searched.get("total").asLong()).isEqualTo(1L);

        String cipher = queryString("SELECT content_cipher FROM fenglin_collab.collab_message WHERE id = "
                + messageId);
        assertThat(cipher).doesNotContain("早会改到十点");
        assertThat(cipher.length()).isGreaterThan(20);
        assertThat(queryString("SELECT content_hash FROM fenglin_collab.collab_message WHERE id = " + messageId))
                .hasSize(64);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_MESSAGE_SENT".equals(event.eventType())
                && event.tenantId() == TENANT_A);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_MESSAGE_REVOKED".equals(event.eventType()));
    }

    // ---------- 文档主链 ----------

    @Test
    void completesDocumentVersionRollbackCollaborationCommentsAndShareLink() throws Exception {
        JsonNode created = read(post("/api/collab/v1/docs", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("title", "云协协作规范", "doc_type", "RICH", "secret_level", "INTERNAL"))
                .getBody()).get("data");
        String docId = created.get("doc_id").asText();
        assertThat(created.get("current_version").asInt()).isEqualTo(1);
        assertThat(created.get("my_permission").asText()).isEqualTo("MANAGE");

        JsonNode updated = read(exchange(HttpMethod.PUT, "/api/collab/v1/docs/" + docId, TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("title", "云协协作规范 v2", "content", "第一版正文", "version", 1))
                .getBody()).get("data");
        assertThat(updated.get("current_version").asInt()).isEqualTo(2);
        assertThat(updated.get("content").asText()).isEqualTo("第一版正文");

        ResponseEntity<String> stale = exchange(HttpMethod.PUT, "/api/collab/v1/docs/" + docId, TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("content", "并发写入", "version", 1));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(read(stale.getBody()).get("code").asText()).isEqualTo("2000004");

        JsonNode versions = read(get("/api/collab/v1/docs/" + docId + "/versions", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(versions.get("total").asLong()).isEqualTo(1L);
        assertThat(versions.get("records").get(0).get("version_no").asInt()).isEqualTo(2);
        assertThat(versions.get("records").get(0).get("version_type").asText()).isEqualTo("AUTO");

        JsonNode manual = read(post("/api/collab/v1/docs/" + docId + "/versions", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("version_type", "MANUAL")).getBody()).get("data");
        assertThat(manual.get("version_no").asInt()).isEqualTo(2);

        JsonNode rolledBack = read(post("/api/collab/v1/docs/" + docId + "/rollback/2", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(rolledBack.get("current_version").asInt()).isEqualTo(3);
        assertThat(rolledBack.get("rolled_back_from").asInt()).isEqualTo(2);
        assertThat(read(get("/api/collab/v1/docs/" + docId, TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data").get("content").asText()).isEqualTo("第一版正文");

        JsonNode collaborator = read(post("/api/collab/v1/docs/" + docId + "/collaborators", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("subject_type", "USER", "subject_id", USER_EDITOR,
                        "permission", "EDIT")).getBody()).get("data");
        assertThat(collaborator.get("permission").asText()).isEqualTo("EDIT");

        JsonNode editorView = read(get("/api/collab/v1/docs/" + docId, TENANT_A, USER_EDITOR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(editorView.get("my_permission").asText()).isEqualTo("EDIT");

        JsonNode editedByCollaborator = read(exchange(HttpMethod.PUT, "/api/collab/v1/docs/" + docId, TENANT_A,
                USER_EDITOR, ALL_PERMISSIONS, Map.of("content", "协作者修订", "version", 3))
                .getBody()).get("data");
        assertThat(editedByCollaborator.get("current_version").asInt()).isEqualTo(4);

        JsonNode comment = read(post("/api/collab/v1/docs/" + docId + "/comments", TENANT_A, USER_EDITOR,
                ALL_PERMISSIONS, Map.of("content", "这里需要补充示例", "anchor", Map.of("block", 3)))
                .getBody()).get("data");
        assertThat(comment.get("content").asText()).isEqualTo("这里需要补充示例");
        JsonNode comments = read(get("/api/collab/v1/docs/" + docId + "/comments", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments.get(0).get("content").asText()).isEqualTo("这里需要补充示例");

        JsonNode shareLink = read(post("/api/collab/v1/docs/" + docId + "/share-link", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("expire_days", 7)).getBody()).get("data");
        String token = shareLink.get("link_token").asText();
        assertThat(token).isNotBlank();

        ResponseEntity<String> anonymous = get("/api/collab/v1/docs/shared/" + token, TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(200);
        assertThat(read(anonymous.getBody()).get("data").get("content").asText()).isEqualTo("协作者修订");

        JsonNode confidential = read(post("/api/collab/v1/docs", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("title", "机密方案", "secret_level", "CONFIDENTIAL")).getBody()).get("data");
        ResponseEntity<String> forbidden = post("/api/collab/v1/docs/" + confidential.get("doc_id").asText()
                + "/share-link", TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("expire_days", 7));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(read(forbidden.getBody()).get("code").asText()).isEqualTo("2002010");

        assertThat(queryString("SELECT content_cipher FROM fenglin_collab.collab_document WHERE id = '"
                + docId + "'")).doesNotContain("协作者修订");
        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_DOC_ROLLED_BACK".equals(event.eventType()));
    }

    // ---------- 日历 + 会议 + 待办 ----------

    @Test
    void completesCalendarAndMeetingChainIncludingRoomBookingAndTodoCallback() throws Exception {
        JsonNode calendar = read(post("/api/collab/v1/calendars", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("name", "产品团队日历", "cal_type", "TEAM", "visible_scope", "DEPT"))
                .getBody()).get("data");
        String calendarId = calendar.get("calendar_id").asText();
        assertThat(calendar.get("cal_type").asText()).isEqualTo("TEAM");

        Map<String, Object> eventBody = new LinkedHashMap<>();
        eventBody.put("calendar_id", calendarId);
        eventBody.put("title", "需求评审");
        eventBody.put("start_at", time(1, 10));
        eventBody.put("end_at", time(1, 11));
        eventBody.put("attendee_ids", List.of(USER_MEMBER));
        JsonNode event = read(post("/api/collab/v1/events", TENANT_A, USER_OWNER, ALL_PERMISSIONS, eventBody)
                .getBody()).get("data");
        String eventId = event.get("event_id").asText();
        assertThat(event.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(event.get("attendees").size()).isEqualTo(2);
        assertThat(event.get("attendees").get(0).get("organizer").asBoolean()).isTrue();

        ResponseEntity<String> conflict = post("/api/collab/v1/events", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("calendar_id", calendarId, "title", "冲突评审",
                        "start_at", time(1, 10), "end_at", time(1, 11)));
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(read(conflict.getBody()).get("code").asText()).isEqualTo("2003001");

        Map<String, Object> busyBody = new LinkedHashMap<>();
        busyBody.put("user_ids", List.of(USER_MEMBER));
        busyBody.put("start_time", time(1, 0));
        busyBody.put("end_time", time(1, 23));
        JsonNode busy = read(post("/api/collab/v1/events/free-busy", TENANT_A, USER_MEMBER, ALL_PERMISSIONS,
                busyBody).getBody()).get("data");
        assertThat(busy.get("busy").get(String.valueOf(USER_MEMBER)).size()).isEqualTo(1);

        JsonNode slots = read(post("/api/collab/v1/events/suggest-slots", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("user_ids", List.of(USER_OWNER), "duration_minutes", 30,
                        "search_days", 2)).getBody()).get("data");
        assertThat(slots.get("slots").size()).isEqualTo(5);
        assertThat(slots.get("slots").get(0).get("available_count").asInt()).isEqualTo(1);

        JsonNode room = read(post("/api/collab/v1/meeting-rooms", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("name", "A301", "location", "3F", "capacity", 12, "equipments", List.of("TV"),
                        "bookable", true)).getBody()).get("data");
        long roomId = room.get("room_id").asLong();
        assertThat(room.get("capacity").asInt()).isEqualTo(12);

        Map<String, Object> meetingBody = new LinkedHashMap<>();
        meetingBody.put("title", "方案评审会");
        meetingBody.put("start_at", time(2, 14));
        meetingBody.put("end_at", time(2, 15));
        meetingBody.put("meeting_mode", "OFFLINE");
        meetingBody.put("room_id", roomId);
        meetingBody.put("external_allowed", false);
        JsonNode meeting = read(post("/api/collab/v1/meetings", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                meetingBody).getBody()).get("data");
        String meetingId = meeting.get("meeting_id").asText();
        assertThat(meeting.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(meeting.get("join_code").asText()).matches("\\d{6}");

        JsonNode availableRooms = read(get("/api/collab/v1/meeting-rooms/available?startTime="
                + time(2, 14) + "&endTime=" + time(2, 15), TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(availableRooms.size()).isZero();

        ResponseEntity<String> occupied = post("/api/collab/v1/meeting-rooms/" + roomId + "/reserve", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("start_at", time(2, 14), "end_at", time(2, 16)));
        assertThat(occupied.getStatusCode().value()).isEqualTo(409);
        assertThat(read(occupied.getBody()).get("code").asText()).isEqualTo("2006005");

        JsonNode checkin = read(post("/api/collab/v1/meetings/" + meetingId + "/checkin", TENANT_A,
                USER_MEMBER, ALL_PERMISSIONS, Map.of("method", "SCAN")).getBody()).get("data");
        assertThat(checkin.get("checked_in").asBoolean()).isTrue();

        JsonNode minutes = read(post("/api/collab/v1/meetings/" + meetingId + "/minutes", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("topics", List.of("本期范围"),
                        "conclusions", List.of("按计划推进"), "publish", true)).getBody()).get("data");
        assertThat(minutes.get("status").asText()).isEqualTo("PUBLISHED");
        String minutesId = minutes.get("minutes_id").asText();

        Map<String, Object> actionItem = new LinkedHashMap<>();
        actionItem.put("content", "补充接口契约文档");
        actionItem.put("assignee_id", USER_MEMBER);
        actionItem.put("due_at", time(3, 18));
        JsonNode actions = read(post("/api/collab/v1/meetings/" + meetingId + "/actions", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("minutes_id", minutesId,
                        "actions", List.of(actionItem))).getBody()).get("data");
        assertThat(actions.get("todo_ids").size()).isEqualTo(1);
        long todoId = actions.get("todo_ids").get(0).asLong();

        JsonNode meetingDetail = read(get("/api/collab/v1/meetings/" + meetingId, TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(meetingDetail.get("checkins").get("checked_in").asInt()).isEqualTo(1);
        assertThat(meetingDetail.get("minutes").get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(meetingDetail.get("actions").size()).isEqualTo(1);
        assertThat(meetingDetail.get("actions").get(0).get("todo_id").asLong()).isEqualTo(todoId);
        assertThat(meetingDetail.get("actions").get(0).get("status").asText()).isEqualTo("OPEN");

        JsonNode todos = read(get("/api/collab/v1/workbench/todos?status=OPEN", TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(todos.get("total").asLong()).isEqualTo(1L);
        assertThat(todos.get("records").get(0).get("source_type").asText()).isEqualTo("MEETING_ACTION");

        assertThat(post("/api/collab/v1/workbench/todos/" + todoId + "/done", TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS, Map.of()).getStatusCode().value()).isEqualTo(200);

        JsonNode afterDone = read(get("/api/collab/v1/meetings/" + meetingId, TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(afterDone.get("actions").get(0).get("status").asText()).isEqualTo("DONE");

        assertThat(post("/api/collab/v1/meetings/" + meetingId + "/cancel", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of()).getStatusCode().value()).isEqualTo(200);
        assertThat(read(get("/api/collab/v1/meetings/" + meetingId, TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data").get("meeting").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(read(get("/api/collab/v1/meeting-rooms/available?startTime=" + time(2, 14) + "&endTime="
                + time(2, 15), TENANT_A, USER_OWNER, ALL_PERMISSIONS).getBody()).get("data").size()).isEqualTo(1);

        assertThat(post("/api/collab/v1/events/" + eventId + "/cancel", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of()).getStatusCode().value()).isEqualTo(200);
        assertThat(read(get("/api/collab/v1/events/" + eventId, TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data").get("status").asText()).isEqualTo("CANCELLED");

        assertThat(AUDIT_EVENTS).anyMatch(audit -> "COLLAB_MEETING_ACTIONS_CREATED".equals(audit.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(audit -> "COLLAB_TODO_COMPLETED".equals(audit.eventType()));
    }

    // ---------- OKR 主链 ----------

    @Test
    void completesOkrChainWithAlignmentCycleDetectionAndPeriodClose() throws Exception {
        JsonNode period = read(post("/api/collab/v1/okr/admin/periods", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("name", "2026 Q4", "start_date", "2026-10-01", "end_date", "2026-12-31",
                        "score_policy", "AVG_KR")).getBody()).get("data");
        String periodId = period.get("period_id").asText();
        assertThat(period.get("status").asText()).isEqualTo("OPEN");

        JsonNode company = read(post("/api/collab/v1/okr/objectives", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("period_id", periodId, "obj_type", "COMPANY", "title", "提升组织协同效率"))
                .getBody()).get("data");
        String companyObjectiveId = company.get("objective_id").asText();

        JsonNode personal = read(post("/api/collab/v1/okr/objectives", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("period_id", periodId, "obj_type", "PERSONAL", "title", "完成文档迁移"))
                .getBody()).get("data");
        String personalObjectiveId = personal.get("objective_id").asText();

        JsonNode kr = read(post("/api/collab/v1/okr/objectives/" + companyObjectiveId + "/key-results",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("title", "周活跃提升", "metric_type", "PERCENT",
                        "target_value", 100, "current_value", 20, "confidence", "MID"))
                .getBody()).get("data");
        String krId = kr.get("kr_id").asText();
        assertThat(new BigDecimal(kr.get("progress").asText())).isEqualByComparingTo("20.00");

        JsonNode aligned = read(post("/api/collab/v1/okr/objectives/" + personalObjectiveId + "/align",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("parent_objective_id", companyObjectiveId,
                        "align_type", "VERTICAL", "weight", 0.5)).getBody()).get("data");
        assertThat(aligned.get("alignment_id").asLong()).isPositive();

        ResponseEntity<String> cycle = post("/api/collab/v1/okr/objectives/" + companyObjectiveId + "/align",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("parent_objective_id", personalObjectiveId,
                        "align_type", "VERTICAL"));
        assertThat(cycle.getStatusCode().value()).isEqualTo(400);
        assertThat(read(cycle.getBody()).get("code").asText()).isEqualTo("2005001");

        ResponseEntity<String> selfCycle = post("/api/collab/v1/okr/objectives/" + companyObjectiveId + "/align",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("parent_objective_id", companyObjectiveId));
        assertThat(selfCycle.getStatusCode().value()).isEqualTo(400);

        JsonNode progress = read(post("/api/collab/v1/okr/key-results/" + krId + "/progress", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("progress", 60, "confidence", "HIGH",
                        "note", "周会推进中")).getBody()).get("data");
        assertThat(new BigDecimal(progress.get("progress").asText())).isEqualByComparingTo("60");

        ResponseEntity<String> outOfRange = post("/api/collab/v1/okr/key-results/" + krId + "/progress",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("progress", 120));
        assertThat(outOfRange.getStatusCode().value()).isEqualTo(400);
        assertThat(read(outOfRange.getBody()).get("code").asText()).isEqualTo("2005003");

        JsonNode graph = read(get("/api/collab/v1/okr/alignment-graph?periodId=" + periodId, TENANT_A,
                USER_OWNER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(graph.get("nodes").size()).isEqualTo(2);
        assertThat(graph.get("edges").size()).isEqualTo(1);
        assertThat(graph.get("edges").get(0).get("child_id").asText()).isEqualTo(personalObjectiveId);
        assertThat(graph.get("edges").get(0).get("parent_id").asText()).isEqualTo(companyObjectiveId);

        assertThat(post("/api/collab/v1/okr/periods/" + periodId + "/close", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of()).getStatusCode().value()).isEqualTo(200);
        assertThat(read(get("/api/collab/v1/okr/periods", TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data").get(0).get("status").asText()).isEqualTo("CLOSED");

        ResponseEntity<String> closedPeriod = post("/api/collab/v1/okr/objectives", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("period_id", periodId, "title", "关闭后目标"));
        assertThat(closedPeriod.getStatusCode().value()).isEqualTo(409);
        assertThat(read(closedPeriod.getBody()).get("code").asText()).isEqualTo("2005002");

        ResponseEntity<String> closedProgress = post("/api/collab/v1/okr/key-results/" + krId + "/progress",
                TENANT_A, USER_OWNER, ALL_PERMISSIONS, Map.of("progress", 70));
        assertThat(closedProgress.getStatusCode().value()).isEqualTo(409);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_OKR_ALIGNED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_OKR_PERIOD_CLOSED".equals(event.eventType()));
    }

    // ---------- 审批 + 通知 ----------

    @Test
    void completesApprovalChainWithIdempotencyNotificationAndCancel() throws Exception {
        JsonNode template = read(post("/api/collab/v1/approvals/admin/templates", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("code", "COLLAB_LEAVE", "name", "请假审批", "category", "HR",
                        "form_schema", Map.of("fields", List.of("days")), "process_key", "collab_approval"))
                .getBody()).get("data");
        String templateId = template.get("template_id").asText();
        assertThat(template.get("enabled").asBoolean()).isTrue();

        Map<String, Object> submitBody = new LinkedHashMap<>();
        submitBody.put("template_id", templateId);
        submitBody.put("form_data", Map.of("days", 2, "reason", "家中有事"));
        submitBody.put("approver_id", USER_MEMBER);
        submitBody.put("idempotency_key", "idem-1");
        JsonNode instance = read(post("/api/collab/v1/approvals/instances", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, submitBody).getBody()).get("data");
        String instanceId = instance.get("instance_id").asText();
        assertThat(instance.get("instance_no").asText()).matches("APV\\d{12}");
        assertThat(instance.get("status").asText()).isEqualTo("RUNNING");
        assertThat(instance.get("form_data").get("days").asInt()).isEqualTo(2);
        assertThat(instance.get("tasks").size()).isEqualTo(1);
        long taskId = instance.get("tasks").get(0).get("task_id").asLong();
        assertThat(instance.get("tasks").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(WORKFLOW_CALLS).hasSize(1);

        JsonNode duplicated = read(post("/api/collab/v1/approvals/instances", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, submitBody).getBody()).get("data");
        assertThat(duplicated.get("instance_id").asText()).isEqualTo(instanceId);
        assertThat(WORKFLOW_CALLS).hasSize(1);

        JsonNode myTasks = read(get("/api/collab/v1/approvals/tasks/my", TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(myTasks.size()).isEqualTo(1);
        assertThat(myTasks.get(0).get("taskId").asLong()).isEqualTo(taskId);
        assertThat(myTasks.get(0).get("instanceStatus").asText()).isEqualTo("RUNNING");

        JsonNode notifications = read(get("/api/collab/v1/notifications?unreadOnly=true", TENANT_A,
                USER_MEMBER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(notifications.get("total").asLong()).isEqualTo(1L);
        long notificationId = notifications.get("records").get(0).get("notification_id").asLong();
        assertThat(notifications.get("records").get(0).get("category").asText()).isEqualTo("APPROVAL");

        JsonNode readResult = read(post("/api/collab/v1/notifications/" + notificationId + "/read", TENANT_A,
                USER_MEMBER, ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(readResult.get("read").asBoolean()).isTrue();
        assertThat(read(get("/api/collab/v1/notifications?unreadOnly=true", TENANT_A, USER_MEMBER,
                ALL_PERMISSIONS).getBody()).get("data").get("total").asLong()).isZero();

        JsonNode approved = read(post("/api/collab/v1/approvals/tasks/" + taskId + "/approve", TENANT_A,
                USER_MEMBER, ALL_PERMISSIONS, Map.of("comment", "同意")).getBody()).get("data");
        assertThat(approved.get("task_status").asText()).isEqualTo("DONE");
        assertThat(approved.get("instance_status").asText()).isEqualTo("APPROVED");

        JsonNode detail = read(get("/api/collab/v1/approvals/instances/" + instanceId, TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(detail.get("status").asText()).isEqualTo("APPROVED");
        assertThat(detail.get("tasks").get(0).get("action").asText()).isEqualTo("APPROVE");

        ResponseEntity<String> handledAgain = post("/api/collab/v1/approvals/tasks/" + taskId + "/approve",
                TENANT_A, USER_MEMBER, ALL_PERMISSIONS, Map.of());
        assertThat(handledAgain.getStatusCode().value()).isEqualTo(409);
        assertThat(read(handledAgain.getBody()).get("code").asText()).isEqualTo("2007002");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("template_id", templateId);
        second.put("form_data", Map.of("days", 1));
        second.put("approver_id", USER_MEMBER);
        second.put("idempotency_key", "idem-2");
        String cancelInstanceId = read(post("/api/collab/v1/approvals/instances", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, second).getBody()).get("data").get("instance_id").asText();

        ResponseEntity<String> cancelByOther = post("/api/collab/v1/approvals/instances/" + cancelInstanceId
                + "/cancel", TENANT_A, USER_MEMBER, ALL_PERMISSIONS, Map.of());
        assertThat(cancelByOther.getStatusCode().value()).isEqualTo(403);

        assertThat(post("/api/collab/v1/approvals/instances/" + cancelInstanceId + "/cancel", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of()).getStatusCode().value()).isEqualTo(200);
        assertThat(read(get("/api/collab/v1/approvals/instances/" + cancelInstanceId, TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data").get("status").asText()).isEqualTo("CANCELLED");

        assertThat(read(get("/api/collab/v1/approvals/instances/my", TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data").size()).isEqualTo(2);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_APPROVAL_SUBMITTED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_APPROVAL_APPROVE".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_APPROVAL_CANCELLED".equals(event.eventType()));
    }

    // ---------- 工作台 ----------

    @Test
    void savesWorkbenchLayoutAndRegistersWidgets() throws Exception {
        JsonNode created = read(post("/api/collab/v1/admin/workbench/widgets", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("code", "TODO_CARD", "name", "待办卡片", "widget_type", "CARD",
                        "default_size", "MEDIUM")).getBody()).get("data");
        assertThat(created).isNull();

        JsonNode widgets = read(get("/api/collab/v1/workbench/widgets", TENANT_A, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(widgets.size()).isEqualTo(1);
        assertThat(widgets.get(0).get("code").asText()).isEqualTo("TODO_CARD");

        JsonNode emptyLayout = read(get("/api/collab/v1/workbench/layout", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(emptyLayout.get("widgets").size()).isZero();

        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("code", "TODO_CARD");
        placement.put("position", Map.of("x", 0, "y", 0, "w", 6, "h", 4));
        placement.put("config", Map.of("limit", 10));
        JsonNode saved = read(exchange(HttpMethod.PUT, "/api/collab/v1/workbench/layout", TENANT_A,
                USER_OWNER, ALL_PERMISSIONS, Map.of("widgets", List.of(placement))).getBody()).get("data");
        assertThat(saved.get("widgets").size()).isEqualTo(1);
        assertThat(saved.get("layout_type").asText()).isEqualTo("PERSONAL");

        JsonNode reloaded = read(get("/api/collab/v1/workbench/layout", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(reloaded.get("widgets").size()).isEqualTo(1);
        assertThat(reloaded.get("widgets").get(0).get("code").asText()).isEqualTo("TODO_CARD");

        assertThat(AUDIT_EVENTS).anyMatch(event -> "COLLAB_WORKBENCH_LAYOUT_SAVED".equals(event.eventType()));
    }

    // ---------- 隔离与权限 ----------

    @Test
    void isolatesTenantsAndEnforcesPermissions() throws Exception {
        JsonNode conversation = read(post("/api/collab/v1/im/conversations", TENANT_A, USER_OWNER,
                ALL_PERMISSIONS, Map.of("conv_type", "SINGLE", "member_ids", List.of(USER_MEMBER)))
                .getBody()).get("data");
        String convId = conversation.get("conversation").get("conversation_id").asText();

        JsonNode listB = read(get("/api/collab/v1/im/conversations", TENANT_B, USER_OWNER, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(listB.get("total").asLong()).isZero();
        assertThat(get("/api/collab/v1/im/conversations/" + convId, TENANT_B, USER_OWNER, ALL_PERMISSIONS)
                .getStatusCode().value()).isEqualTo(404);

        JsonNode doc = read(post("/api/collab/v1/docs", TENANT_A, USER_OWNER, ALL_PERMISSIONS,
                Map.of("title", "租户内文档")).getBody()).get("data");
        assertThat(get("/api/collab/v1/docs/" + doc.get("doc_id").asText(), TENANT_B, USER_OWNER,
                ALL_PERMISSIONS).getStatusCode().value()).isEqualTo(404);

        assertThat(post("/api/collab/v1/docs", TENANT_A, USER_OWNER, null, Map.of("title", "无权限文档"))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/collab/v1/workbench/todos", TENANT_A, USER_OWNER, null)
                .getStatusCode().value()).isEqualTo(403);

        assertThat(queryString("SELECT count(1) FROM fenglin_collab.collab_conversation WHERE tenant_id = "
                + TENANT_A)).isEqualTo("1");
    }

    // ---------- helpers ----------

    private String time(int plusDays, int hour) {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().plusDays(plusDays)
                .atTime(hour, 0).atOffset(ZoneOffset.UTC).toString();
    }

    private ResponseEntity<String> post(String path, long tenantId, long userId, String permissions, Object body) {
        return exchange(HttpMethod.POST, path, tenantId, userId, permissions, body);
    }

    private ResponseEntity<String> get(String path, long tenantId, long userId, String permissions) {
        return exchange(HttpMethod.GET, path, tenantId, userId, permissions, null);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, long tenantId, long userId,
                                            String permissions, Object body) {
        return restTemplate.exchange(url(path), method,
                new HttpEntity<>(body == null ? null : write(body), headers(tenantId, userId, permissions)),
                String.class);
    }

    private HttpHeaders headers(long tenantId, long userId, String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Client-Type", "ADMIN");
        headers.set("X-Request-Id", "test-" + System.nanoTime());
        if (permission != null) {
            headers.set("X-Permissions", permission);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, ex);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
