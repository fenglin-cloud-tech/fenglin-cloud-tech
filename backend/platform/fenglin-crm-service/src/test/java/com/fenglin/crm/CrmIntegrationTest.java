package com.fenglin.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.crm.client.WorkflowClient;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM 主链端到端集成测试：真实 PostgreSQL 15（独立 schema fenglin_crm + RLS）+ 真实服务端口。
 * 覆盖 线索 → 客户 → 联系人 → 商机 → 报价 → 工作流审批 → 合同/回款计划。
 *
 * <p>工作流中心在本测试中以 @Primary 替身记录提单调用（真实 HTTP 客户端见 HttpWorkflowClient），
 * 审批引擎本身由 fenglin-workflow-service 的集成测试覆盖。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrmIntegrationTest {

    private static final long TENANT_A = 8601L;
    private static final long TENANT_B = 8602L;
    private static final String ALL_PERMISSIONS = String.join(",",
            "crm:lead:create", "crm:lead:view", "crm:lead:assign", "crm:lead:transfer",
            "crm:customer:view", "crm:customer:create", "crm:customer:manage",
            "crm:contact:view", "crm:contact:manage",
            "crm:opportunity:view", "crm:opportunity:create", "crm:opportunity:manage", "crm:report:sales",
            "crm:quotation:create", "crm:quotation:view", "crm:quotation:submit", "crm:quotation:approve",
            "crm:activity:create", "crm:activity:view",
            "crm:contract:manage", "crm:contract:view");
    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_crm")
                .defaultSchema("fenglin_crm")
                .locations("classpath:db/crm-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_crm");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_crm_app");
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
            return (permissions, requestId, quotationNo, quotationId, customerId, totalAmount, discountRate) -> {
                WORKFLOW_CALLS.add(quotationNo);
                return "PROC-TEST-1";
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
            statement.execute("TRUNCATE fenglin_crm.crm_lead, fenglin_crm.crm_customer, fenglin_crm.crm_contact, "
                    + "fenglin_crm.crm_opportunity, fenglin_crm.crm_opportunity_stage_log, "
                    + "fenglin_crm.crm_activity, fenglin_crm.crm_quotation, fenglin_crm.crm_quotation_item, "
                    + "fenglin_crm.crm_service_contract, fenglin_crm.crm_payment_plan");
        }
    }

    @Test
    void completesMainChainFromLeadToApprovedQuotationAndWon() throws Exception {
        JsonNode lead = read(post("/api/crm/v1/leads", TENANT_A, ALL_PERMISSIONS, leadBody("云枢科技")).getBody())
                .get("data");
        long leadId = lead.get("lead_id").asLong();
        assertThat(lead.get("lead_no").asText()).matches("CRM\\d{12}");
        assertThat(lead.get("status").asText()).isEqualTo("NEW");
        assertThat(lead.get("mobile_masked").asText()).isEqualTo("***8000");

        ResponseEntity<String> duplicated = post("/api/crm/v1/leads", TENANT_A, ALL_PERMISSIONS,
                leadBody("云枢科技重复"));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("1001001");

        JsonNode duplicates = read(post("/api/crm/v1/leads/check-duplicate", TENANT_A, ALL_PERMISSIONS,
                Map.of("mobile", "13800138000")).getBody()).get("data");
        assertThat(duplicates.get("exact_matches")).hasSize(1);

        JsonNode assigned = read(post("/api/crm/v1/leads/" + leadId + "/assign", TENANT_A, ALL_PERMISSIONS,
                Map.of("owner_id", 4002)).getBody()).get("data");
        assertThat(assigned.get("owner_id").asLong()).isEqualTo(4002L);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("biz_type", "LEAD");
        activity.put("biz_id", leadId);
        activity.put("activity_type", "CALL");
        activity.put("subject", "首次电话沟通");
        activity.put("content", "确认预算与决策链");
        activity.put("duration_min", 15);
        activity.put("result", "CONNECTED");
        assertThat(post("/api/crm/v1/activities", TENANT_A, ALL_PERMISSIONS, activity)
                .getStatusCode().value()).isEqualTo(200);

        Map<String, Object> convert = new LinkedHashMap<>();
        convert.put("create_customer", true);
        convert.put("create_contact", true);
        convert.put("create_opportunity", true);
        convert.put("opportunity_name", "云枢科技-协同办公");
        convert.put("amount", 300000);
        convert.put("stage", "S1");
        JsonNode converted = read(post("/api/crm/v1/leads/" + leadId + "/convert", TENANT_A, ALL_PERMISSIONS,
                convert).getBody()).get("data");
        long customerId = converted.get("customer_id").asLong();
        long opportunityId = converted.get("opportunity_id").asLong();
        assertThat(converted.get("contact_id").asLong()).isPositive();

        JsonNode convertedLead = read(get("/api/crm/v1/leads/" + leadId, TENANT_A, ALL_PERMISSIONS).getBody())
                .get("data").get("lead");
        assertThat(convertedLead.get("status").asText()).isEqualTo("CONVERTED");
        assertThat(convertedLead.get("converted_customer_id").asLong()).isEqualTo(customerId);

        JsonNode customer = read(get("/api/crm/v1/customers/" + customerId, TENANT_A, ALL_PERMISSIONS).getBody())
                .get("data");
        assertThat(customer.get("customer").get("mobile_masked").asText()).isEqualTo("***8000");
        assertThat(customer.get("contacts")).hasSize(1);
        assertThat(customer.get("opportunities")).hasSize(1);

        JsonNode duplicateCustomer = read(post("/api/crm/v1/customers/check-duplicate", TENANT_A, ALL_PERMISSIONS,
                Map.of("mobile", "13800138000")).getBody()).get("data");
        assertThat(duplicateCustomer.get("exact_matches")).hasSize(1);

        Map<String, Object> keyContact = new LinkedHashMap<>();
        keyContact.put("customer_id", customerId);
        keyContact.put("name", "李娜");
        keyContact.put("position", "信息化总监");
        keyContact.put("role_type", "DECISION");
        keyContact.put("influence", 5);
        keyContact.put("is_key", true);
        assertThat(post("/api/crm/v1/contacts", TENANT_A, ALL_PERMISSIONS, keyContact)
                .getStatusCode().value()).isEqualTo(200);

        JsonNode s2 = read(patch("/api/crm/v1/opportunities/" + opportunityId + "/stage", TENANT_A, ALL_PERMISSIONS,
                Map.of("to_stage", "S2", "opinion", "确认需求")).getBody()).get("data");
        assertThat(s2.get("stage").asText()).isEqualTo("S2");

        Map<String, Object> toS3 = new LinkedHashMap<>();
        toS3.put("to_stage", "S3");
        toS3.put("amount", 300000);
        toS3.put("expected_close_date", LocalDate.now().plusMonths(2).toString());
        JsonNode s3 = read(patch("/api/crm/v1/opportunities/" + opportunityId + "/stage", TENANT_A, ALL_PERMISSIONS,
                toS3).getBody()).get("data");
        assertThat(s3.get("stage").asText()).isEqualTo("S3");

        ResponseEntity<String> s4WithoutQuotation = patch("/api/crm/v1/opportunities/" + opportunityId + "/stage",
                TENANT_A, ALL_PERMISSIONS, Map.of("to_stage", "S4"));
        assertThat(s4WithoutQuotation.getStatusCode().value()).isEqualTo(400);
        assertThat(read(s4WithoutQuotation.getBody()).get("code").asText()).isEqualTo("1002001");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("product_code", "SKU-COLLAB-01");
        item.put("product_name", "协同办公标准版");
        item.put("unit", "套");
        item.put("quantity", 1);
        item.put("unit_price", 100000);
        item.put("discount_rate", 30);
        Map<String, Object> quotationBody = new LinkedHashMap<>();
        quotationBody.put("opportunity_id", opportunityId);
        quotationBody.put("customer_id", customerId);
        quotationBody.put("valid_until", LocalDate.now().plusDays(30).toString());
        quotationBody.put("items", List.of(item));
        JsonNode quotation = read(post("/api/crm/v1/quotations", TENANT_A, ALL_PERMISSIONS, quotationBody)
                .getBody()).get("data");
        long quotationId = quotation.get("quotation_id").asLong();
        assertThat(quotation.get("status").asText()).isEqualTo("DRAFT");
        assertThat(quotation.get("items")).hasSize(1);
        assertThat(new BigDecimal(quotation.get("total_amount").asText())).isEqualByComparingTo("70000.00");
        assertThat(new BigDecimal(quotation.get("discount_rate").asText())).isEqualByComparingTo("30");

        JsonNode submitted = read(post("/api/crm/v1/quotations/" + quotationId + "/submit", TENANT_A,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(submitted.get("status").asText()).isEqualTo("PENDING");
        assertThat(submitted.get("approval_required").asBoolean()).isTrue();
        assertThat(submitted.get("approve_instance_id").asText()).isEqualTo("PROC-TEST-1");
        assertThat(WORKFLOW_CALLS).hasSize(1);

        JsonNode s4 = read(patch("/api/crm/v1/opportunities/" + opportunityId + "/stage", TENANT_A, ALL_PERMISSIONS,
                Map.of("to_stage", "S4")).getBody()).get("data");
        assertThat(s4.get("stage").asText()).isEqualTo("S4");

        JsonNode approved = read(post("/api/crm/v1/internal/quotations/" + quotationId + "/approval", TENANT_A,
                ALL_PERMISSIONS, Map.of("result", "APPROVED", "comment", "同意")).getBody()).get("data");
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        JsonNode won = read(patch("/api/crm/v1/opportunities/" + opportunityId + "/stage", TENANT_A, ALL_PERMISSIONS,
                Map.of("to_stage", "WON")).getBody()).get("data");
        assertThat(won.get("stage").asText()).isEqualTo("WON");
        assertThat(won.get("status").asText()).isEqualTo("WON");

        JsonNode stageLogs = read(get("/api/crm/v1/opportunities/" + opportunityId + "/stage-logs",
                TENANT_A, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(stageLogs).hasSize(4);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "CRM_LEAD_CREATED".equals(event.eventType())
                && event.tenantId() == TENANT_A);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "CRM_LEAD_CONVERTED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "CRM_QUOTATION_SUBMITTED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "CRM_OPPORTUNITY_STAGE_CHANGED".equals(event.eventType())
                && "HIGH".equals(event.riskLevel()));

        String mobileEnc = queryString("SELECT mobile_enc FROM fenglin_crm.crm_lead WHERE id = " + leadId);
        assertThat(mobileEnc).isNotEqualTo("13800138000");
        assertThat(mobileEnc).doesNotContain("13800138000");
        assertThat(mobileEnc.length()).isGreaterThan(20);
        String mobileHash = queryString("SELECT mobile_hash FROM fenglin_crm.crm_lead WHERE id = " + leadId);
        assertThat(mobileHash).hasSize(64);
        assertThat(queryString("SELECT mobile_enc FROM fenglin_crm.crm_customer WHERE id = " + customerId))
                .isNotEqualTo("13800138000");
    }

    @Test
    void autoApprovesQuotationUnderThresholds() throws Exception {
        long customerId = createCustomer(TENANT_A, "报价阈值客户", null);
        long opportunityId = createOpportunity(TENANT_A, customerId, "小额商机", 50000);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("product_code", "SKU-BASIC-01");
        item.put("product_name", "基础版");
        item.put("quantity", 1);
        item.put("unit_price", 50000);
        item.put("discount_rate", 5);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("opportunity_id", opportunityId);
        body.put("customer_id", customerId);
        body.put("items", List.of(item));
        long quotationId = read(post("/api/crm/v1/quotations", TENANT_A, ALL_PERMISSIONS, body)
                .getBody()).get("data").get("quotation_id").asLong();

        JsonNode submitted = read(post("/api/crm/v1/quotations/" + quotationId + "/submit", TENANT_A,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(submitted.get("status").asText()).isEqualTo("APPROVED");
        assertThat(submitted.get("approval_required").asBoolean()).isFalse();
        assertThat(WORKFLOW_CALLS).isEmpty();
        assertThat(AUDIT_EVENTS).anyMatch(event -> "CRM_QUOTATION_AUTO_APPROVED".equals(event.eventType()));
    }

    @Test
    void isolatesTenantsAndEnforcesPermissions() throws Exception {
        long leadId = read(post("/api/crm/v1/leads", TENANT_A, ALL_PERMISSIONS, leadBody("租户隔离线索"))
                .getBody()).get("data").get("lead_id").asLong();

        JsonNode listB = read(get("/api/crm/v1/leads", TENANT_B, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(listB.get("total").asLong()).isZero();
        assertThat(get("/api/crm/v1/leads/" + leadId, TENANT_B, ALL_PERMISSIONS)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(post("/api/crm/v1/leads", TENANT_A, null, leadBody("无权限线索"))
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void rejectsStageAdvanceWithoutPrerequisites() throws Exception {
        long customerId = createCustomer(TENANT_A, "阶段准入客户", null);
        long opportunityId = createOpportunity(TENANT_A, customerId, "准入校验商机", 0);

        ResponseEntity<String> missingAmount = patch("/api/crm/v1/opportunities/" + opportunityId + "/stage",
                TENANT_A, ALL_PERMISSIONS, Map.of("to_stage", "S3"));
        assertThat(missingAmount.getStatusCode().value()).isEqualTo(400);
        assertThat(read(missingAmount.getBody()).get("code").asText()).isEqualTo("1002001");

        Map<String, Object> withAmount = new LinkedHashMap<>();
        withAmount.put("to_stage", "S3");
        withAmount.put("amount", 200000);
        withAmount.put("expected_close_date", LocalDate.now().plusMonths(1).toString());
        ResponseEntity<String> missingKeyContact = patch("/api/crm/v1/opportunities/" + opportunityId + "/stage",
                TENANT_A, ALL_PERMISSIONS, withAmount);
        assertThat(missingKeyContact.getStatusCode().value()).isEqualTo(400);
        assertThat(read(missingKeyContact.getBody()).get("message").asText()).contains("关键决策人");

        ResponseEntity<String> lostWithoutReason = patch("/api/crm/v1/opportunities/" + opportunityId + "/stage",
                TENANT_A, ALL_PERMISSIONS, Map.of("to_stage", "LOST"));
        assertThat(lostWithoutReason.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void createsContractWithPaymentPlans() throws Exception {
        long customerId = createCustomer(TENANT_A, "合同客户", "91310000MA1K000001");

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("period_no", 1);
        first.put("due_date", LocalDate.now().plusMonths(1).toString());
        first.put("amount", 60000);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("period_no", 2);
        second.put("due_date", LocalDate.now().plusMonths(6).toString());
        second.put("amount", 60000);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer_id", customerId);
        body.put("sla_level", "P1");
        body.put("service_package", "标准服务包");
        body.put("amount", 120000);
        body.put("start_date", LocalDate.now().toString());
        body.put("end_date", LocalDate.now().plusYears(1).toString());
        body.put("payment_plans", List.of(first, second));

        JsonNode contract = read(post("/api/crm/v1/contracts", TENANT_A, ALL_PERMISSIONS, body)
                .getBody()).get("data");
        long contractId = contract.get("contract_id").asLong();
        assertThat(contract.get("contract_no").asText()).matches("CT\\d{10}");
        assertThat(contract.get("payment_plans")).hasSize(2);
        assertThat(contract.get("payment_plans").get(0).get("status").asText()).isEqualTo("PENDING");

        JsonNode detail = read(get("/api/crm/v1/contracts/" + contractId, TENANT_A, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(detail.get("sla_level").asText()).isEqualTo("P1");

        JsonNode contracts = read(get("/api/crm/v1/customers/" + customerId + "/contracts", TENANT_A,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(contracts).hasSize(1);
    }

    // ---------- helpers ----------

    private long createCustomer(long tenantId, String name, String uscc) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (uscc != null) {
            body.put("uscc", uscc);
        }
        body.put("industry", "软件");
        body.put("segment", "A");
        return read(post("/api/crm/v1/customers", tenantId, ALL_PERMISSIONS, body)
                .getBody()).get("data").get("customer_id").asLong();
    }

    private long createOpportunity(long tenantId, long customerId, String name, long amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("customer_id", customerId);
        body.put("amount", amount);
        body.put("stage", "S1");
        return read(post("/api/crm/v1/opportunities", tenantId, ALL_PERMISSIONS, body)
                .getBody()).get("data").get("opportunity_id").asLong();
    }

    private Map<String, Object> leadBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("contact_name", "张伟");
        body.put("mobile", "13800138000");
        body.put("email", "zhangwei@example.com");
        body.put("industry", "软件");
        body.put("region_code", "310000");
        body.put("source", "MANUAL");
        return body;
    }

    private ResponseEntity<String> post(String path, long tenantId, String permissions, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(write(body), headers(tenantId, permissions)), String.class);
    }

    private ResponseEntity<String> get(String path, long tenantId, String permissions) {
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(headers(tenantId, permissions)), String.class);
    }

    private ResponseEntity<String> patch(String path, long tenantId, String permissions, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PATCH,
                new HttpEntity<>(write(body), headers(tenantId, permissions)), String.class);
    }

    private HttpHeaders headers(long tenantId, String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", "3001");
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
