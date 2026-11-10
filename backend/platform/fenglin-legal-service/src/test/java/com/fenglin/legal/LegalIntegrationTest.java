package com.fenglin.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.legal.client.WorkflowClient;
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
 * 云法端到端主链测试：真实内嵌 PostgreSQL + Flyway + 真实 HTTP。
 *
 * <p>覆盖合同模板/合同/审批/用印/签署/存证/履约/归档、DLP 规则/策略/决策/事件/放行/水印/审计链与管理端报表。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegalIntegrationTest {

    private static final long TENANT = 9931L;
    private static final long OTHER_TENANT = 9932L;
    private static final long USER = 8401L;
    private static final long APPROVER = 8402L;
    private static final String ALL = "*";

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_legal")
                .defaultSchema("fenglin_legal")
                .locations("classpath:db/legal-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_legal");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_legal_app");
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
                + "FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_legal' "
                + "AND tablename <> 'flyway_schema_history' LOOP "
                + "EXECUTE 'TRUNCATE TABLE fenglin_legal.' || quote_ident(r.tablename) || ' CASCADE'; "
                + "END LOOP; END $$");
    }

    @Test
    void contractSealSignEvidenceArchiveChain() throws Exception {
        JsonNode template = ok(post("/api/legal/v1/contract-templates", TENANT, USER, ALL, null,
                Map.of("name", "采购合同模板", "category", "PURCHASE")));
        long templateId = template.path("data").path("id").asLong();
        assertThat(template.path("data").path("template_no").asText()).startsWith("TPL");

        JsonNode version = ok(post("/api/legal/v1/contract-templates/" + templateId + "/versions",
                TENANT, USER, ALL, null, Map.of("content_ref", "minio://legal/tpl/1.docx",
                        "change_summary", "首版")));
        assertThat(version.path("data").path("version_no").asInt()).isEqualTo(1);
        JsonNode variables = ok(put("/api/legal/v1/admin/contract-templates/" + templateId + "/variables",
                TENANT, USER, ALL, Map.of("variables", List.of(
                        Map.of("var_key", "term", "var_label", "合同期限", "required", true)))));
        assertThat(variables.path("data")).hasSize(1);

        Map<String, Object> contractBody = Map.of(
                "name", "服务器采购合同", "category", "PURCHASE", "template_id", templateId,
                "amount", 128000, "currency", "CNY", "start_date", "2026-11-01", "end_date", "2027-10-31",
                "owner_id", USER, "risk_level", "MID",
                "variable_values", Map.of("term", "12个月"));
        JsonNode created = ok(post("/api/legal/v1/contracts", TENANT, USER, ALL, "ct-idem-1", contractBody));
        long contractId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("contract_no").asText()).startsWith("CT");
        assertThat(created.path("data").path("status").asText()).isEqualTo("DRAFT");
        assertThat(ok(post("/api/legal/v1/contracts", TENANT, USER, ALL, "ct-idem-1", contractBody))
                .path("data").path("id").asLong()).isEqualTo(contractId);

        JsonNode updated = ok(put("/api/legal/v1/contracts/" + contractId, TENANT, USER, ALL,
                Map.of("risk_level", "HIGH")));
        assertThat(updated.path("data").path("risk_level").asText()).isEqualTo("HIGH");

        ok(post("/api/legal/v1/contracts/" + contractId + "/versions", TENANT, USER, ALL, null,
                Map.of("content_ref", "minio://legal/ct/1.docx", "change_summary", "条款定稿")));
        JsonNode clauses = ok(put("/api/legal/v1/contracts/" + contractId + "/clauses", TENANT, USER, ALL,
                Map.of("clauses", List.of(Map.of("clause_no", "1", "title", "标的", "content", "服务器 8 台")))));
        assertThat(clauses.path("data")).hasSize(1);

        JsonNode submitted = ok(post("/api/legal/v1/contracts/" + contractId + "/submit", TENANT, USER, ALL,
                "submit-1", Map.of()));
        assertThat(submitted.path("data").path("status").asText()).isEqualTo("APPROVING");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("legalContractApproval:"));
        JsonNode approved = ok(post("/api/legal/v1/contracts/" + contractId + "/approve", TENANT, APPROVER, ALL,
                "approve-1", Map.of("action", "APPROVE", "comment", "同意")));
        assertThat(approved.path("data").path("status").asText()).isEqualTo("APPROVED");

        JsonNode party = ok(post("/api/legal/v1/contract-parties", TENANT, USER, ALL, null, Map.of(
                "name", "北京示例科技有限公司", "uscc", "91110000MA0000000X",
                "contact_name", "张三", "contact_mobile", "13800001111")));
        assertThat(party.path("data").path("contact_mobile_tail").asText()).isEqualTo("1111");
        JsonNode duplicated = ok(post("/api/legal/v1/contract-parties", TENANT, USER, ALL, null,
                Map.of("name", "北京示例科技有限公司", "uscc", "91110000MA0000000X")));
        assertThat(duplicated.path("data").path("duplicated").asBoolean()).isTrue();
        assertThat(ok(get("/api/legal/v1/contract-parties?keyword=示例", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode seal = ok(post("/api/legal/v1/admin/seals", TENANT, USER, ALL, null, Map.of(
                "name", "合同专用章", "seal_type", "CONTRACT", "subject_name", "丰麟云创")));
        long sealId = seal.path("data").path("id").asLong();
        JsonNode application = ok(post("/api/legal/v1/seal-applications", TENANT, USER, ALL, "seal-apply-1",
                Map.of("contract_id", contractId, "seal_id", sealId, "seal_type", "CONTRACT", "copies", 2,
                        "purpose", "合同签署用印")));
        long applicationId = application.path("data").path("id").asLong();
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("legalSealApproval:"));
        JsonNode sealApproved = ok(post("/api/legal/v1/seal-applications/" + applicationId + "/approve",
                TENANT, APPROVER, ALL, "seal-approve-1", Map.of("action", "APPROVE")));
        assertThat(sealApproved.path("data").path("status").asText()).isEqualTo("APPROVE");
        JsonNode sealed = ok(post("/api/legal/v1/seal-applications/" + applicationId + "/execute",
                TENANT, USER, ALL, "seal-exec-1", Map.of("image_ref", "minio://legal/seal/1.png")));
        assertThat(sealed.path("data").path("block_hash").asText()).isNotBlank();
        assertThat(sealed.path("data").path("seq_no").asLong()).isEqualTo(1L);
        assertThat(ok(get("/api/legal/v1/seal-applications", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode signatory = ok(post("/api/legal/v1/contract-signatories", TENANT, USER, ALL, null, Map.of(
                "contract_id", contractId, "signer_name", "李四", "signer_mobile", "13900002222",
                "signer_id_no", "110101199001011234", "seal_mode", "HANDWRITE")));
        assertThat(signatory.path("data").path("signer_mobile_tail").asText()).isEqualTo("2222");
        JsonNode sign = ok(post("/api/legal/v1/contracts/" + contractId + "/sign/initiate", TENANT, USER, ALL,
                "sign-init-1", Map.of()));
        long signRecordId = sign.path("data").path("sign_record_id").asLong();
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("legalContractSign:"));
        assertThat(post("/api/legal/v1/sign-tasks/" + signRecordId + "/sign", TENANT, USER, ALL, "sign-1",
                Map.of("step_up", true)).getStatusCode().is2xxSuccessful()).isFalse();
        JsonNode signed = ok(post("/api/legal/v1/sign-tasks/" + signRecordId + "/sign", TENANT, USER, ALL, "sign-2",
                Map.of("step_up", true, "real_name_verified", true, "will_confirmed", true,
                        "auth_mode", "CA", "tsa_token", "TSA-0001", "sign_data", "SIG-BASE64")));
        assertThat(signed.path("data").path("contract_all_signed").asBoolean()).isTrue();
        assertThat(ok(get("/api/legal/v1/contracts/" + contractId, TENANT, USER, ALL))
                .path("data").path("status").asText()).isEqualTo("SIGNED");
        assertThat(ok(get("/api/legal/v1/evidences?contract_id=" + contractId, TENANT, USER, ALL))
                .path("data").path("total").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(ok(get("/api/legal/v1/evidences/chain-verify?contract_id=" + contractId, TENANT, USER, ALL))
                .path("data").path("valid").asBoolean()).isTrue();

        JsonNode node = ok(post("/api/legal/v1/performance-nodes", TENANT, USER, ALL, null, Map.of(
                "contract_id", contractId, "node_type", "DELIVERY", "name", "首批交付",
                "plan_date", "2026-12-01", "amount", 64000)));
        long nodeId = node.path("data").path("id").asLong();
        assertThat(ok(post("/api/legal/v1/performance-nodes/" + nodeId + "/status", TENANT, USER, ALL, null,
                Map.of("status", "DONE"))).path("data").path("status").asText()).isEqualTo("DONE");
        assertThat(ok(post("/api/legal/v1/expiry-reminders", TENANT, USER, ALL, null,
                Map.of("contract_id", contractId))).path("data").path("inserted").asBoolean()).isTrue();
        assertThat(ok(get("/api/legal/v1/expiry-reminders", TENANT, USER, ALL)).path("data").path("total").asInt())
                .isEqualTo(1);

        JsonNode archived = ok(post("/api/legal/v1/contracts/" + contractId + "/archive", TENANT, USER, ALL,
                "archive-1", null));
        assertThat(archived.path("data").path("status").asText()).isEqualTo("ARCHIVED");
        JsonNode integrity = ok(post("/api/legal/v1/contracts/" + contractId + "/verify-integrity",
                TENANT, USER, ALL, null, Map.of()));
        assertThat(integrity.path("data").path("valid").asBoolean()).isTrue();

        JsonNode report = ok(get("/api/legal/v1/admin/reports/contracts?dim=STATUS", TENANT, USER, ALL));
        assertThat(report.path("data").path("summary").path("total").asInt()).isEqualTo(1);
        JsonNode signReport = ok(get("/api/legal/v1/admin/reports/sign-compliance", TENANT, USER, ALL));
        assertThat(signReport.path("data").path("evidence_complete_rate").decimalValue())
                .isGreaterThanOrEqualTo(java.math.BigDecimal.ONE);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "contract.approve".equals(event.action()));

        JsonNode other = ok(post("/api/legal/v1/contracts", OTHER_TENANT, 8501L, ALL, null,
                Map.of("name", "另一租户合同", "category", "SALES")));
        long otherId = other.path("data").path("id").asLong();
        assertThat(get("/api/legal/v1/contracts/" + otherId, TENANT, USER, ALL).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void dlpRulePolicyDecisionEventReleaseWatermarkAndAuditChain() throws Exception {
        JsonNode rule = ok(post("/api/legal/v1/admin/dlp/rules", TENANT, USER, ALL, null, Map.of(
                "name", "手机号识别", "rule_type", "REGEX", "expression", "1[3-9]\\d{9}",
                "data_level", "CONFIDENTIAL", "priority", 10)));
        long ruleId = rule.path("data").path("id").asLong();
        assertThat(ok(post("/api/legal/v1/admin/dlp/rules/" + ruleId + "/test", TENANT, USER, ALL, null,
                Map.of("sample_text", "联系人 13800001111"))).path("data").path("matched").asBoolean()).isTrue();
        assertThat(ok(post("/api/legal/v1/admin/dlp/rules/" + ruleId + "/test", TENANT, USER, ALL, null,
                Map.of("sample_text", "无敏感内容"))).path("data").path("matched").asBoolean()).isFalse();
        assertThat(post("/api/legal/v1/admin/dlp/rules", TENANT, USER, ALL, null, Map.of(
                "name", "坏正则", "rule_type", "REGEX", "expression", "([", "data_level", "SECRET"))
                .getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode policy = ok(post("/api/legal/v1/admin/dlp/policies", TENANT, USER, ALL, null, Map.of(
                "name", "邮件外发阻断", "data_level", "CONFIDENTIAL", "action", "BLOCK",
                "rule_ids", List.of(ruleId), "channel_codes", List.of("EMAIL"), "priority", 10)));
        long policyId = policy.path("data").path("id").asLong();
        assertThat(policy.path("data").path("policy_no").asText()).startsWith("DP");
        assertThat(post("/api/legal/v1/admin/dlp/policies", TENANT, USER, ALL, null, Map.of(
                "name", "冲突策略", "data_level", "CONFIDENTIAL", "action", "WARN",
                "rule_ids", List.of(ruleId), "channel_codes", List.of("IM"), "priority", 10))
                .getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(ok(put("/api/legal/v1/admin/dlp/channels", TENANT, USER, ALL, Map.of(
                "channels", List.of(Map.of("channel_code", "EMAIL", "policy_id", policyId, "enabled", true,
                        "default_action", "BLOCK"))))).path("data").path("channel_count").asInt()).isEqualTo(1);

        JsonNode decision = ok(post("/api/legal/v1/internal/legal/dlp/decide", TENANT, USER, ALL, null,
                Map.of("channel_code", "EMAIL", "content_sample", "客户手机 13800001111")));
        assertThat(decision.path("data").path("decision").asText()).isEqualTo("BLOCK");
        assertThat(decision.path("data").path("data_level").asText()).isEqualTo("CONFIDENTIAL");
        assertThat(decision.path("data").path("policy_no").asText()).startsWith("DP");

        JsonNode detection = ok(post("/api/legal/v1/internal/legal/dlp/detect", TENANT, USER, ALL, null,
                Map.of("content", "身份证 110101199001011234")));
        assertThat(detection.path("data").path("matched_rule_ids")).isNotEmpty();

        JsonNode event = ok(post("/api/legal/v1/internal/legal/dlp/report", TENANT, USER, ALL, null, Map.of(
                "event_no", "DLPEVT-1", "channel_code", "EMAIL", "data_level", "CONFIDENTIAL",
                "action_taken", "BLOCK", "file_name", "customer.xlsx", "file_fingerprint", "fp-1",
                "matched_rule_ids", List.of(ruleId))));
        long eventId = event.path("data").path("event_id").asLong();
        assertThat(ok(post("/api/legal/v1/internal/legal/dlp/report", TENANT, USER, ALL, null, Map.of(
                "event_no", "DLPEVT-1", "channel_code", "EMAIL"))).path("data").path("merged").asBoolean()).isTrue();
        JsonNode handled = ok(post("/api/legal/v1/dlp/events/" + eventId + "/handle", TENANT, USER, ALL,
                "handle-1", Map.of("action", "TRIAGE", "conclusion", "待核查")));
        assertThat(handled.path("data").path("status").asText()).isEqualTo("TRIAGING");
        assertThat(ok(post("/api/legal/v1/dlp/events/" + eventId + "/handle", TENANT, USER, ALL,
                "handle-2", Map.of("action", "FALSE_POSITIVE"))).path("data").path("finished").asBoolean())
                .isTrue();
        assertThat(ok(get("/api/legal/v1/dlp/events?event_type=OUTBOUND", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode release = ok(post("/api/legal/v1/dlp/releases", TENANT, USER, ALL, null, Map.of(
                "channel_code", "EMAIL", "file_name", "quote.pdf", "content_fingerprint", "fp-2",
                "target", "partner@example.com", "purpose", "投标报价", "data_level", "CONFIDENTIAL")));
        long releaseId = release.path("data").path("id").asLong();
        assertThat(release.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("legalDlpReleaseApproval:"));
        JsonNode releaseApproved = ok(post("/api/legal/v1/dlp/releases/" + releaseId + "/approve",
                TENANT, APPROVER, ALL, "release-1", Map.of("action", "APPROVE", "valid_hours", 24)));
        assertThat(releaseApproved.path("data").path("status").asText()).isEqualTo("APPROVED");

        JsonNode watermark = ok(post("/api/legal/v1/admin/dlp/watermarks", TENANT, USER, ALL, null, Map.of(
                "biz_type", "FILE", "biz_id", 1001, "file_hash", "fh-1", "file_ref", "minio://legal/q.pdf",
                "user_id", USER)));
        String embedCode = watermark.path("data").path("embed_code").asText();
        assertThat(embedCode).startsWith("ENC-");
        JsonNode traced = ok(post("/api/legal/v1/admin/dlp/watermarks/trace", TENANT, USER, ALL, null,
                Map.of("embed_code", embedCode, "event_id", eventId)));
        assertThat(traced.path("data").path("trace_status").asText()).isEqualTo("TRACED");
        assertThat(ok(get("/api/legal/v1/admin/dlp/watermarks", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);
        assertThat(post("/api/legal/v1/admin/dlp/watermarks/trace", TENANT, USER, ALL, null,
                Map.of("embed_code", "ENC-NOT-EXIST")).getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode terminal = ok(post("/api/legal/v1/internal/legal/terminal/register", TENANT, USER, ALL, null,
                Map.of("mac", "AA-BB-CC-DD-EE-FF", "hostname", "PC-001", "os_type", "WINDOWS")));
        String terminalNo = terminal.path("data").path("terminal_no").asText();
        assertThat(terminalNo).startsWith("TM");
        assertThat(ok(post("/api/legal/v1/internal/legal/terminal/register", TENANT, USER, ALL, null,
                Map.of("mac", "AA-BB-CC-DD-EE-FF"))).path("data").path("terminal_no").asText())
                .isEqualTo(terminalNo);
        assertThat(ok(get("/api/legal/v1/admin/dlp/terminals", TENANT, USER, ALL))
                .path("data").path("total").asInt()).isEqualTo(1);

        JsonNode auditLogs = ok(get("/api/legal/v1/admin/dlp/audit-logs", TENANT, USER, ALL));
        assertThat(auditLogs.path("data").path("total").asInt()).isGreaterThanOrEqualTo(5);
        JsonNode chain = ok(post("/api/legal/v1/admin/dlp/audit-logs/verify", TENANT, USER, ALL, null, Map.of()));
        assertThat(chain.path("data").path("valid").asBoolean()).isTrue();
        assertThat(chain.path("data").path("checked").asLong()).isGreaterThanOrEqualTo(5);

        JsonNode dlpReport = ok(get("/api/legal/v1/admin/reports/dlp?dim=CHANNEL", TENANT, USER, ALL));
        assertThat(dlpReport.path("data").path("items")).isNotEmpty();
        assertThat(dlpReport.path("data").path("metrics").has("block_rate")).isTrue();
    }

    @Test
    void adminConfigEchoAndPermissionEnforced() throws Exception {
        JsonNode saved = ok(put("/api/legal/v1/admin/config/reminder", TENANT, USER, ALL,
                Map.of("offset_days", 45, "channels", List.of("INBOX", "EMAIL"))));
        assertThat(saved.path("data").path("saved").asBoolean()).isTrue();
        JsonNode reminder = ok(get("/api/legal/v1/admin/config/reminder", TENANT, USER, ALL));
        assertThat(reminder.path("data").path("configured").asBoolean()).isTrue();
        assertThat(reminder.path("data").path("offset_days").asInt()).isEqualTo(45);
        assertThat(reminder.path("data").path("channels")).hasSize(2);
        assertThat(ok(put("/api/legal/v1/admin/config/contract-fields", TENANT, USER, ALL,
                Map.of("fields", List.of(Map.of("field", "amount", "visible", true)))))
                .path("data").path("field_count").asInt()).isEqualTo(1);
        JsonNode fields = ok(get("/api/legal/v1/admin/config/contract-fields", TENANT, USER, ALL));
        assertThat(fields.path("data").path("configured").asBoolean()).isTrue();
        assertThat(fields.path("data").path("fields").get(0).path("field").asText()).isEqualTo("amount");
        assertThat(ok(put("/api/legal/v1/admin/config/dictionary", TENANT, USER, ALL,
                Map.of("type", "legal_contract_category", "items", List.of(Map.of("code", "PURCHASE")))))
                .path("data").path("item_count").asInt()).isEqualTo(1);
        JsonNode dictionary = ok(get("/api/legal/v1/admin/config/dictionary?type=legal_contract_category",
                TENANT, USER, ALL));
        assertThat(dictionary.path("data").path("configured").asBoolean()).isTrue();
        assertThat(dictionary.path("data").path("items").get(0).path("code").asText()).isEqualTo("PURCHASE");
        assertThat(ok(post("/api/legal/v1/admin/config/ca-providers", TENANT, USER, ALL, null,
                Map.of("provider_code", "CA-TEST", "secret_ref", "kms://legal/ca-test")))
                .path("data").path("connectivity").asText()).isEqualTo("OK");
        JsonNode caProviders = ok(get("/api/legal/v1/admin/config/ca-providers", TENANT, USER, ALL));
        assertThat(caProviders.path("data")).hasSize(1);
        assertThat(caProviders.path("data").get(0).path("provider_code").asText()).isEqualTo("CA-TEST");
        assertThat(ok(get("/api/legal/v1/admin/config/ca-providers", OTHER_TENANT, USER, ALL))
                .path("data")).isEmpty();
        assertThat(post("/api/legal/v1/admin/config/ca-providers", TENANT, USER, ALL, null,
                Map.of("provider_code", "CA-BAD", "secret_ref", "plain-secret"))
                .getStatusCode().is2xxSuccessful()).isFalse();

        assertThat(post("/api/legal/v1/admin/dlp/rules", TENANT, USER, "legal:contract:view", null,
                Map.of("name", "越权规则", "rule_type", "KEYWORD", "expression", "x",
                        "data_level", "SECRET")).getStatusCode().value()).isEqualTo(403);
        assertThat(ok(get("/api/legal/v1/admin/reports/contracts", TENANT, USER, ALL))
                .path("data").path("dim").asText()).isEqualTo("STATUS");
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
