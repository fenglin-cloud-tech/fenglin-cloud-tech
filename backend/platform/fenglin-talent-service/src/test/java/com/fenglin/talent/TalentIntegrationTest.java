package com.fenglin.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.talent.client.WorkflowClient;
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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云才 C1 组织与人事主链端到端集成测试：真实 PostgreSQL（独立 schema fenglin_talent + RLS）+ 真实服务端口。
 *
 * <p>覆盖组织树/移动/撤销、员工 PII 脱敏与查重、岗位职级编制、招聘需求编制校验与审批、
 * 候选人内推提示、应聘/面试/Offer 带宽校验、入职试用期、异动生效、离职审批。</p>
 *
 * <p>工作流中心在测试中以 @Primary 替身记录提单调用，流程引擎本身由工作流服务集成测试覆盖。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalentIntegrationTest {

    private static final long TENANT = 9701L;
    private static final long USER_HR = 6101L;
    private static final long USER_INTERVIEWER = 6102L;
    private static final long GRADE_ID = 9001L;
    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    private static final String ALL_PERMISSIONS = String.join(",",
            "talent:org:tree", "talent:org:view",
            "talent:admin:org:create", "talent:admin:org:update", "talent:admin:org:move",
            "talent:admin:org:delete",
            "talent:admin:employee:list", "talent:employee:view", "talent:admin:employee:create",
            "talent:admin:employee:update", "talent:admin:employee:import", "talent:admin:employee:export",
            "talent:admin:position:list", "talent:admin:position:create", "talent:admin:grade:list",
            "talent:admin:headcount:create",
            "talent:admin:recruit:create", "talent:admin:recruit:list", "talent:admin:recruit:submit",
            "talent:admin:recruit:approve",
            "talent:admin:candidate:create", "talent:admin:candidate:list",
            "talent:admin:candidate:parse",
            "talent:admin:interview:create", "talent:interview:evaluate", "biz:talent:interview:my",
            "talent:admin:offer:create",
            "talent:admin:onboarding:create", "talent:admin:probation:evaluate",
            "talent:admin:transfer:create", "talent:admin:transfer:approve",
            "talent:admin:resignation:approve",
            "biz:talent:resignation:apply",
            "talent:admin:course:create", "talent:course:list", "talent:admin:session:create",
            "biz:talent:course:enroll", "biz:talent:course:cancel", "biz:talent:learning:progress",
            "biz:talent:exam:submit", "biz:talent:cert:list",
            "talent:knowledge:create", "talent:admin:knowledge:publish", "talent:knowledge:search",
            "talent:admin:skill:tag:create", "talent:skill:view", "talent:skill:update",
            "talent:admin:skill:inventory",
            "talent:admin:demand:create", "talent:admin:demand:publish", "biz:talent:flow:market",
            "biz:talent:flow:apply", "talent:flow:list", "talent:admin:flow:approve",
            "talent:admin:flow:match",
            "talent:admin:survey:create", "talent:admin:survey:list", "talent:admin:survey:publish",
            "biz:talent:survey:answer", "biz:talent:survey:submit", "talent:admin:survey:analysis",
            "talent:admin:attrition:list", "talent:admin:attrition:score",
            "talent:admin:intervention:create",
            "talent:admin:okr:period", "talent:okr:create", "talent:okr:list", "talent:okr:kr:create",
            "talent:okr:kr:update", "talent:okr:align", "talent:okr:align:view", "talent:okr:review",
            "biz:talent:innovation:submit", "talent:innovation:list", "talent:innovation:review",
            "biz:talent:innovation:vote", "talent:admin:innovation:incubate");

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_talent")
                .defaultSchema("fenglin_talent")
                .locations("classpath:db/talent-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_talent");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_talent_app");
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
            statement.execute("TRUNCATE fenglin_talent.talent_org, fenglin_talent.talent_position, "
                    + "fenglin_talent.talent_grade, fenglin_talent.talent_headcount_plan, "
                    + "fenglin_talent.talent_employee, fenglin_talent.talent_recruit_requisition, "
                    + "fenglin_talent.talent_candidate, fenglin_talent.talent_application, "
                    + "fenglin_talent.talent_interview, fenglin_talent.talent_offer, "
                    + "fenglin_talent.talent_onboarding, fenglin_talent.talent_probation, "
                    + "fenglin_talent.talent_transfer, fenglin_talent.talent_resignation, "
                    + "fenglin_talent.talent_training_course, fenglin_talent.talent_course_session, "
                    + "fenglin_talent.talent_learning_record, fenglin_talent.talent_certificate, "
                    + "fenglin_talent.talent_knowledge_category, fenglin_talent.talent_knowledge_article, "
                    + "fenglin_talent.talent_skill_tag, fenglin_talent.talent_employee_skill, "
                    + "fenglin_talent.talent_talent_pool, fenglin_talent.talent_talent_pool_member, "
                    + "fenglin_talent.talent_talent_demand, fenglin_talent.talent_flow_application, "
                    + "fenglin_talent.talent_engagement_survey, fenglin_talent.talent_survey_response, "
                    + "fenglin_talent.talent_attrition_risk, fenglin_talent.talent_intervention, "
                    + "fenglin_talent.talent_okr_period, fenglin_talent.talent_okr_objective, "
                    + "fenglin_talent.talent_okr_key_result, fenglin_talent.talent_okr_alignment, "
                    + "fenglin_talent.talent_innovation_proposal, fenglin_talent.talent_innovation_review, "
                    + "fenglin_talent.talent_tenant_key");
        }
    }

    // ---------- 组织 ----------

    @Test
    void orgTreeDetailMoveRejectsCycleAndGuardsDelete() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");
        long team = createOrg("平台组", "FL.RD.PLATFORM", rd, "TEAM");
        long sales = createOrg("销售中心", "FL.SALES", root, "DEPT");

        JsonNode tree = read(get("/api/talent/v1/orgs/tree", USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(tree.size()).isEqualTo(1);
        assertThat(tree.get(0).get("org_code").asText()).isEqualTo("FL");
        assertThat(tree.get(0).get("children").size()).isEqualTo(2);
        assertThat(tree.get(0).get("children").get(0).get("org_code").asText()).isEqualTo("FL.RD");
        assertThat(tree.get(0).get("children").get(0).get("children").get(0).get("org_code").asText())
                .isEqualTo("FL.RD.PLATFORM");

        JsonNode detail = read(get("/api/talent/v1/orgs/" + rd, USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(detail.get("level").asInt()).isEqualTo(2);
        assertThat(detail.get("employee_count").asLong()).isZero();
        assertThat(detail.get("status").asText()).isEqualTo("ENABLED");
        assertThat(detail.get("path").asText()).isEqualTo("/" + root + "/" + rd + "/");

        JsonNode moved = read(post("/api/talent/v1/orgs/" + rd + "/move", USER_HR, ALL_PERMISSIONS,
                Map.of("target_parent_id", sales)).getBody()).get("data");
        assertThat(moved.get("level").asInt()).isEqualTo(3);
        assertThat(moved.get("path").asText()).isEqualTo("/" + root + "/" + sales + "/" + rd + "/");
        assertThat(read(get("/api/talent/v1/orgs/" + team, USER_HR, ALL_PERMISSIONS).getBody())
                .get("data").get("level").asInt()).isEqualTo(4);

        ResponseEntity<String> cycle = post("/api/talent/v1/orgs/" + sales + "/move", USER_HR, ALL_PERMISSIONS,
                Map.of("target_parent_id", team));
        assertThat(cycle.getStatusCode().value()).isEqualTo(400);
        assertThat(read(cycle.getBody()).get("code").asText()).isEqualTo("3001005");

        ResponseEntity<String> notEmpty = delete("/api/talent/v1/orgs/" + rd, USER_HR, ALL_PERMISSIONS);
        assertThat(notEmpty.getStatusCode().value()).isEqualTo(409);
        assertThat(read(notEmpty.getBody()).get("code").asText()).isEqualTo("3001006");

        long tmp = createOrg("临时项目组", "FL.TMP", root, "TEAM");
        JsonNode disabled = read(delete("/api/talent/v1/orgs/" + tmp, USER_HR, ALL_PERMISSIONS).getBody())
                .get("data");
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
        assertThat(read(get("/api/talent/v1/orgs/tree", USER_HR, ALL_PERMISSIONS).getBody())
                .get("data").get(0).get("children").size()).isEqualTo(1);
        assertThat(read(get("/api/talent/v1/orgs/tree?includeDisabled=true", USER_HR, ALL_PERMISSIONS).getBody())
                .get("data").get(0).get("children").size()).isEqualTo(2);

        ResponseEntity<String> forbidden = get("/api/talent/v1/orgs/tree", USER_HR, "");
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_ORG_CREATED".equals(event.eventType())
                && event.tenantId() == TENANT);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_ORG_MOVED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_ORG_DISABLED".equals(event.eventType()));
    }

    // ---------- 员工 ----------

    @Test
    void employeeCreateMasksPiiRejectsDuplicateAndLists() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");

        JsonNode created = read(post("/api/talent/v1/employees", USER_HR, ALL_PERMISSIONS, employeeBody(
                "E1001", "张三", "13800001111", "110101199001011234", rd)).getBody()).get("data");
        long employeeId = created.get("id").asLong();
        assertThat(created.get("emp_no").asText()).isEqualTo("E1001");
        assertThat(created.get("emp_status").asText()).isEqualTo("PENDING");

        JsonNode detail = read(get("/api/talent/v1/employees/" + employeeId, USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(detail.get("masked_phone").asText()).isEqualTo("138****1111");
        assertThat(detail.get("masked_id_card").asText()).startsWith("1101").contains("****");
        assertThat(detail.has("phone")).isFalse();
        assertThat(detail.get("org").get("name").asText()).isEqualTo("研发中心");

        ResponseEntity<String> duplicated = post("/api/talent/v1/employees", USER_HR, ALL_PERMISSIONS,
                employeeBody("E1002", "李四", "13800001111", "110101199002022345", rd));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("3001001");

        JsonNode list = read(get("/api/talent/v1/employees?orgId=" + root + "&includeSubOrg=true&keyword=张",
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(list.get("total").asLong()).isEqualTo(1L);
        assertThat(list.get("records").get(0).get("masked_phone").asText()).isEqualTo("138****1111");
        assertThat(list.get("records").get(0).get("org").get("id").asLong()).isEqualTo(rd);

        JsonNode updated = read(exchange(HttpMethod.PUT, "/api/talent/v1/employees/" + employeeId, USER_HR,
                ALL_PERMISSIONS, Map.of("name", "张三丰", "version", 0)).getBody()).get("data");
        assertThat(updated.get("id").asLong()).isEqualTo(employeeId);

        ResponseEntity<String> stale = exchange(HttpMethod.PUT, "/api/talent/v1/employees/" + employeeId,
                USER_HR, ALL_PERMISSIONS, Map.of("name", "并发更新", "version", 0));
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(read(stale.getBody()).get("code").asText()).isEqualTo("3000004");

        String cipher = queryString("SELECT phone_enc FROM fenglin_talent.talent_employee WHERE id = "
                + employeeId);
        assertThat(cipher).doesNotContain("13800001111");
        assertThat(queryString("SELECT phone_hash FROM fenglin_talent.talent_employee WHERE id = " + employeeId))
                .hasSize(64);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_EMPLOYEE_CREATED".equals(event.eventType()));
    }

    // ---------- 岗位 / 职级 / 编制 ----------

    @Test
    void positionsGradesAndHeadcountPlansAreManaged() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        JsonNode position = read(post("/api/talent/v1/positions", USER_HR, ALL_PERMISSIONS, Map.of(
                "position_code", "RD-JAVA", "position_name", "Java 工程师", "org_id", root,
                "job_family", "研发", "job_sequence", "P", "headcount", 5)).getBody()).get("data");
        long positionId = position.get("id").asLong();
        assertThat(position.get("position_code").asText()).isEqualTo("RD-JAVA");

        JsonNode positions = read(get("/api/talent/v1/positions?page=1&size=10", USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(positions.get("total").asLong()).isEqualTo(1L);
        assertThat(positions.get("records").get(0).get("position_name").asText()).isEqualTo("Java 工程师");

        seedGrade();
        JsonNode grades = read(get("/api/talent/v1/grades", USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(grades.size()).isEqualTo(1);
        assertThat(grades.get(0).get("band_min").asDouble()).isEqualTo(20000.0);
        assertThat(grades.get(0).get("band_max").asDouble()).isEqualTo(40000.0);

        JsonNode plan = read(post("/api/talent/v1/headcount-plans", USER_HR, ALL_PERMISSIONS, Map.of(
                "plan_year", CURRENT_YEAR, "org_id", root, "position_id", positionId,
                "budget_headcount", 2)).getBody()).get("data");
        assertThat(plan.get("status").asText()).isEqualTo("DRAFT");

        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_POSITION_CREATED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_HEADCOUNT_PLAN_CREATED".equals(event.eventType()));
    }

    // ---------- 招聘需求 ----------

    @Test
    void requisitionChecksHeadcountAndSubmitsApproval() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long positionId = createPosition(root);
        createPlan(root, positionId, 2);

        ResponseEntity<String> exceeded = post("/api/talent/v1/recruit/requisitions", USER_HR, ALL_PERMISSIONS,
                requisitionBody(root, positionId, 5));
        assertThat(exceeded.getStatusCode().value()).isEqualTo(409);
        assertThat(read(exceeded.getBody()).get("code").asText()).isEqualTo("3001020");

        JsonNode created = read(post("/api/talent/v1/recruit/requisitions", USER_HR, ALL_PERMISSIONS,
                requisitionBody(root, positionId, 1)).getBody()).get("data");
        long requisitionId = created.get("id").asLong();
        String reqNo = created.get("req_no").asText();
        assertThat(reqNo).matches("TAL\\d{12}");
        assertThat(created.get("req_status").asText()).isEqualTo("DRAFT");

        JsonNode list = read(get("/api/talent/v1/recruit/requisitions?reqStatus=DRAFT&page=1&size=10",
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(list.get("total").asLong()).isEqualTo(1L);
        assertThat(list.get("records").get(0).get("position_name").asText()).isEqualTo("Java 工程师");

        JsonNode submitted = read(post("/api/talent/v1/recruit/requisitions/" + requisitionId + "/submit",
                USER_HR, ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(submitted.get("req_status").asText()).isEqualTo("APPROVING");
        assertThat(submitted.get("process_instance_id").asText()).isEqualTo("WF-" + reqNo);
        assertThat(WORKFLOW_CALLS).containsExactly("recruitRequisitionApproval:" + reqNo);

        read(post("/api/talent/v1/recruit/requisitions/" + requisitionId + "/submit", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody());
        assertThat(WORKFLOW_CALLS).hasSize(1);
        assertThat(AUDIT_EVENTS).anyMatch(
                event -> "TALENT_REQUISITION_SUBMITTED".equals(event.eventType()));
    }

    // ---------- 候选人 / 应聘 / 面试 / Offer ----------

    @Test
    void candidateReferralInterviewAndOfferBand() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long positionId = createPosition(root);
        createPlan(root, positionId, 5);
        seedGrade();
        JsonNode requisition = read(post("/api/talent/v1/recruit/requisitions", USER_HR, ALL_PERMISSIONS,
                requisitionBody(root, positionId, 3)).getBody()).get("data");
        long requisitionId = requisition.get("id").asLong();

        long employeeId = read(post("/api/talent/v1/employees", USER_HR, ALL_PERMISSIONS,
                employeeBody("E2001", "王五", "13900002222", "110101199003033456", root)).getBody())
                .get("data").get("id").asLong();
        assertThat(employeeId).isPositive();

        JsonNode candidate = read(post("/api/talent/v1/recruit/candidates", USER_HR, ALL_PERMISSIONS, Map.of(
                "name", "赵六", "phone", "13900002222", "source", "REFERRAL",
                "experience_years", 5.5, "current_company", "某大厂",
                "tags", List.of("Java", "架构"))).getBody()).get("data");
        long candidateId = candidate.get("id").asLong();
        assertThat(candidate.get("cand_status").asText()).isEqualTo("NEW");
        assertThat(candidate.get("referral_hint").asBoolean()).isTrue();

        ResponseEntity<String> duplicated = post("/api/talent/v1/recruit/candidates", USER_HR, ALL_PERMISSIONS,
                Map.of("name", "钱七", "phone", "13900002222", "source", "JOB_SITE"));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("3001001");

        read(post("/api/talent/v1/recruit/candidates", USER_HR, ALL_PERMISSIONS,
                Map.of("name", "孙八", "source", "OFFICIAL")).getBody());
        JsonNode candidates = read(get("/api/talent/v1/recruit/candidates?page=1&size=10", USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(candidates.get("total").asLong()).isEqualTo(2L);

        JsonNode application = read(post("/api/talent/v1/recruit/applications", USER_HR, ALL_PERMISSIONS, Map.of(
                "candidate_id", candidateId, "requisition_id", requisitionId,
                "stage", "SCREENING")).getBody()).get("data");
        long applicationId = application.get("id").asLong();
        assertThat(application.get("app_status").asText()).isEqualTo("IN_PROGRESS");

        JsonNode interview = read(post("/api/talent/v1/recruit/interviews", USER_HR, ALL_PERMISSIONS, Map.of(
                "application_id", applicationId, "round_no", 1, "interviewer_id", USER_INTERVIEWER,
                "scheduled_at", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(),
                "duration_min", 60, "mode", "VIDEO", "location", "线上")).getBody()).get("data");
        long interviewId = interview.get("id").asLong();
        assertThat(interview.get("int_status").asText()).isEqualTo("PENDING");
        assertThat(queryString("SELECT cand_status FROM fenglin_talent.talent_candidate WHERE id = "
                + candidateId)).isEqualTo("INTERVIEWING");

        JsonNode mine = read(get("/api/talent/v1/recruit/interviews/my", USER_INTERVIEWER, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("candidate_name").asText()).isEqualTo("赵六");

        JsonNode evaluated = read(post("/api/talent/v1/recruit/interviews/" + interviewId + "/evaluate",
                USER_INTERVIEWER, ALL_PERMISSIONS, Map.of("score", 4.5, "evaluation", "基础扎实",
                        "result", "PASS", "version", 0)).getBody()).get("data");
        assertThat(evaluated.get("int_status").asText()).isEqualTo("DONE");
        assertThat(evaluated.get("result").asText()).isEqualTo("PASS");
        assertThat(queryString("SELECT app_status FROM fenglin_talent.talent_application WHERE id = "
                + applicationId)).isEqualTo("PASS");

        ResponseEntity<String> outOfBand = post("/api/talent/v1/recruit/offers", USER_HR, ALL_PERMISSIONS,
                offerBody(candidateId, requisitionId, positionId, 50000));
        assertThat(outOfBand.getStatusCode().value()).isEqualTo(400);
        assertThat(read(outOfBand.getBody()).get("code").asText()).isEqualTo("3001095");

        JsonNode offer = read(post("/api/talent/v1/recruit/offers", USER_HR, ALL_PERMISSIONS,
                offerBody(candidateId, requisitionId, positionId, 30000)).getBody()).get("data");
        long offerId = offer.get("id").asLong();
        assertThat(offer.get("offer_status").asText()).isEqualTo("APPROVING");

        JsonNode sent = read(post("/api/talent/v1/recruit/offers/" + offerId + "/send", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(sent.get("offer_status").asText()).isEqualTo("SENT");

        JsonNode accepted = read(post("/api/talent/v1/recruit/offers/" + offerId + "/accept", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(accepted.get("offer_status").asText()).isEqualTo("ACCEPTED");
        assertThat(queryString("SELECT cand_status FROM fenglin_talent.talent_candidate WHERE id = "
                + candidateId)).isEqualTo("OFFER");

        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_OFFER_SENT".equals(event.eventType()));
    }

    // ---------- 入职 / 试用期 ----------

    @Test
    void onboardingAndProbationPromoteEmployee() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long employeeId = createEmployee("E3001", "周九", root);

        JsonNode onboarding = read(post("/api/talent/v1/onboarding", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "onboard_date", LocalDate.now().toString(),
                "mentor_id", USER_INTERVIEWER,
                "checklist", List.of(Map.of("item", "电脑领取", "done", false)))).getBody()).get("data");
        long onboardingId = onboarding.get("id").asLong();
        assertThat(onboarding.get("ob_status").asText()).isEqualTo("PENDING");
        assertThat(queryString("SELECT prob_status FROM fenglin_talent.talent_probation WHERE employee_id = "
                + employeeId)).isEqualTo("ONGOING");

        JsonNode completed = read(post("/api/talent/v1/onboarding/" + onboardingId + "/complete", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(completed.get("ob_status").asText()).isEqualTo("DONE");
        assertThat(completed.get("employee_status").asText()).isEqualTo("PROBATION");

        long probationId = queryLong("SELECT id FROM fenglin_talent.talent_probation WHERE employee_id = "
                + employeeId);
        JsonNode passed = read(post("/api/talent/v1/probation/" + probationId + "/evaluate", USER_HR,
                ALL_PERMISSIONS, Map.of("evaluation", "表现优秀", "result", "PASSED")).getBody())
                .get("data");
        assertThat(passed.get("prob_status").asText()).isEqualTo("PASSED");
        JsonNode detail = read(get("/api/talent/v1/employees/" + employeeId, USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(detail.get("emp_status").asText()).isEqualTo("ACTIVE");
        assertThat(detail.get("formal_date").asText()).isEqualTo(LocalDate.now().toString());

        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_EMPLOYEE_ONBOARDED".equals(event.eventType()));
    }

    // ---------- 异动 ----------

    @Test
    void transferRejectsPastDateAndAppliesOnEffective() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");
        long employeeId = createEmployee("E4001", "吴十", root);
        long positionId = createPosition(rd);
        seedGrade();

        ResponseEntity<String> past = post("/api/talent/v1/transfers", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "transfer_type", "TRANSFER",
                "after", Map.of("org_id", rd, "position_id", positionId),
                "effective_date", LocalDate.now().minusDays(1).toString(), "reason", "补录"));
        assertThat(past.getStatusCode().value()).isEqualTo(400);
        assertThat(read(past.getBody()).get("code").asText()).isEqualTo("3001090");

        JsonNode created = read(post("/api/talent/v1/transfers", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "transfer_type", "TRANSFER",
                "after", Map.of("org_id", rd, "position_id", positionId, "grade_id", GRADE_ID,
                        "salary", 30000),
                "effective_date", LocalDate.now().toString(), "reason", "组织调整")).getBody())
                .get("data");
        long transferId = created.get("id").asLong();
        assertThat(created.get("tf_status").asText()).isEqualTo("APPROVING");
        assertThat(created.get("process_instance_id").asText()).isEqualTo("WF-" + transferId);
        assertThat(WORKFLOW_CALLS).contains("employeeLifecycle:" + transferId);

        JsonNode effective = read(post("/api/talent/v1/transfers/" + transferId + "/effective", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(effective.get("tf_status").asText()).isEqualTo("EFFECTIVE");
        assertThat(effective.get("employee").get("org_id").asLong()).isEqualTo(rd);
        assertThat(queryLong("SELECT org_id FROM fenglin_talent.talent_employee WHERE id = " + employeeId))
                .isEqualTo(rd);
        assertThat(AUDIT_EVENTS).anyMatch(
                event -> "TALENT_EMPLOYEE_TRANSFERRED".equals(event.eventType()));
    }

    // ---------- 离职 ----------

    @Test
    void resignationApplyApproveMarksEmployeeResigned() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long employeeId = createEmployee("E5001", "郑十一", root);
        LocalDate lastWorkDate = LocalDate.now().plusDays(30);

        JsonNode created = read(post("/api/talent/v1/resignations", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "resign_type", "VOLUNTARY",
                "apply_date", LocalDate.now().toString(), "last_work_date", lastWorkDate.toString(),
                "reason_category", "个人发展")).getBody()).get("data");
        long resignationId = created.get("id").asLong();
        assertThat(created.get("rs_status").asText()).isEqualTo("APPROVING");

        ResponseEntity<String> duplicated = post("/api/talent/v1/resignations", USER_HR, ALL_PERMISSIONS,
                Map.of("employee_id", employeeId, "resign_type", "VOLUNTARY",
                        "apply_date", LocalDate.now().toString(), "last_work_date", lastWorkDate.toString()));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("3000004");

        JsonNode approved = read(post("/api/talent/v1/resignations/" + resignationId + "/approve", USER_HR,
                ALL_PERMISSIONS, Map.of("approved", true, "opinion", "同意")).getBody()).get("data");
        assertThat(approved.get("rs_status").asText()).isEqualTo("EFFECTIVE");

        JsonNode detail = read(get("/api/talent/v1/employees/" + employeeId, USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(detail.get("emp_status").asText()).isEqualTo("RESIGNED");
        assertThat(queryString("SELECT leave_date::text FROM fenglin_talent.talent_employee WHERE id = "
                + employeeId)).isEqualTo(lastWorkDate.toString());
        assertThat(AUDIT_EVENTS).anyMatch(
                event -> "TALENT_EMPLOYEE_RESIGNED".equals(event.eventType()));
    }

    // ---------- 批量导入 / 花名册导出 / 人员结构 ----------

    @Test
    void batchImportRosterExportAndHeadcountReport() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");

        JsonNode imported = read(post("/api/talent/v1/employees/import", USER_HR, ALL_PERMISSIONS,
                Map.of("rows", List.of(
                        employeeBody("I1001", "导入一", "13700001111", "110101199001011234", rd),
                        employeeBody("I1002", "导入二", "13700001111", "110101199002022345", rd)))).getBody())
                .get("data");
        assertThat(imported.get("total").asInt()).isEqualTo(2);
        assertThat(imported.get("success").asInt()).isEqualTo(1);
        assertThat(imported.get("failed").asInt()).isEqualTo(1);
        assertThat(imported.get("errors").get(0).get("index").asInt()).isEqualTo(1);
        assertThat(imported.get("errors").get(0).get("emp_no").asText()).isEqualTo("I1002");
        assertThat(imported.get("errors").get(0).get("code").asText()).isEqualTo("3001001");
        assertThat(queryLong("SELECT count(1) FROM fenglin_talent.talent_employee")).isEqualTo(1L);

        ResponseEntity<byte[]> roster = restTemplate.exchange(
                url("/api/talent/v1/employees/roster/export?orgId=" + root + "&includeSubOrg=true"),
                HttpMethod.GET, new HttpEntity<>(headers(USER_HR, ALL_PERMISSIONS)), byte[].class);
        assertThat(roster.getStatusCode().value()).isEqualTo(200);
        assertThat(roster.getHeaders().getContentType().toString()).contains("text/csv");
        byte[] bytes = roster.getBody();
        assertThat(bytes).isNotNull();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF工号,姓名,性别,组织,岗位,职级,直属上级,入职日期,员工状态,工作地,脱敏手机号");
        assertThat(csv).contains("137****1111").doesNotContain("13700001111");

        JsonNode report = read(get("/api/talent/v1/reports/headcount?orgId=" + root + "&includeSubOrg=true",
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(report.get("total").asLong()).isEqualTo(1L);
        assertThat(report.get("by_status").get("PENDING").asLong()).isEqualTo(1L);
        assertThat(report.get("by_org").size()).isEqualTo(1);
        assertThat(report.get("by_org").get(0).get("org_id").asLong()).isEqualTo(rd);
        assertThat(report.get("by_employment_type").get("REGULAR").asLong()).isEqualTo(1L);

        assertThat(queryLong("SELECT count(1) FROM fenglin_talent.talent_tenant_key WHERE tenant_id = "
                + TENANT)).isGreaterThanOrEqualTo(1L);
        assertThat(queryString("SELECT tenant_salt FROM fenglin_talent.talent_tenant_key WHERE tenant_id = "
                + TENANT + " LIMIT 1")).isNotBlank();
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_EMPLOYEES_IMPORTED".equals(event.eventType()));
    }

    // ---------- 简历解析 / 招聘与异动审批回调 ----------

    @Test
    void resumeParseAndApprovalCallbacks() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");
        long positionId = createPosition(root);
        createPlan(root, positionId, 5);
        seedGrade();

        long candidateId = read(post("/api/talent/v1/recruit/candidates", USER_HR, ALL_PERMISSIONS, Map.of(
                "name", "简历候选人", "source", "OFFICIAL")).getBody()).get("data").get("id").asLong();
        JsonNode parsed = read(post("/api/talent/v1/recruit/candidates/" + candidateId + "/resume/parse",
                USER_HR, ALL_PERMISSIONS, Map.of("resume_text",
                        "姓名：钱七\n手机：13612345678\n邮箱：qianqi@example.com\n5年经验")).getBody())
                .get("data");
        assertThat(parsed.get("candidate_id").asLong()).isEqualTo(candidateId);
        assertThat(parsed.get("applied").asBoolean()).isTrue();
        assertThat(parsed.get("parsed").get("name").asText()).isEqualTo("钱七");
        assertThat(parsed.get("parsed").get("phone").asText()).isEqualTo("13612345678");
        assertThat(parsed.get("parsed").get("email").asText()).isEqualTo("qianqi@example.com");
        assertThat(parsed.get("parsed").get("experience_years").asDouble()).isEqualTo(5.0);
        assertThat(queryString("SELECT phone_enc FROM fenglin_talent.talent_candidate WHERE id = "
                + candidateId)).doesNotContain("13612345678");
        assertThat(queryString("SELECT phone_hash FROM fenglin_talent.talent_candidate WHERE id = "
                + candidateId)).hasSize(64);

        JsonNode unresolvable = read(post("/api/talent/v1/recruit/candidates/" + candidateId + "/resume/parse",
                USER_HR, ALL_PERMISSIONS, Map.of("resume_text", "无可用信息")).getBody()).get("data");
        assertThat(unresolvable.get("applied").asBoolean()).isFalse();
        assertThat(unresolvable.get("parsed").has("phone")).isFalse();

        long requisitionId = read(post("/api/talent/v1/recruit/requisitions", USER_HR, ALL_PERMISSIONS,
                requisitionBody(root, positionId, 1)).getBody()).get("data").get("id").asLong();
        read(post("/api/talent/v1/recruit/requisitions/" + requisitionId + "/submit", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody());
        JsonNode approved = read(post("/api/talent/v1/recruit/requisitions/" + requisitionId + "/approval",
                USER_HR, ALL_PERMISSIONS, Map.of("approved", true, "opinion", "同意")).getBody()).get("data");
        assertThat(approved.get("req_status").asText()).isEqualTo("RECRUITING");
        JsonNode again = read(post("/api/talent/v1/recruit/requisitions/" + requisitionId + "/approval",
                USER_HR, ALL_PERMISSIONS, Map.of("approved", true)).getBody()).get("data");
        assertThat(again.get("req_status").asText()).isEqualTo("RECRUITING");

        long rejectedId = read(post("/api/talent/v1/recruit/requisitions", USER_HR, ALL_PERMISSIONS,
                requisitionBody(root, positionId, 1)).getBody()).get("data").get("id").asLong();
        read(post("/api/talent/v1/recruit/requisitions/" + rejectedId + "/submit", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody());
        JsonNode rejected = read(post("/api/talent/v1/recruit/requisitions/" + rejectedId + "/approval",
                USER_HR, ALL_PERMISSIONS, Map.of("approved", false, "opinion", "暂缓")).getBody()).get("data");
        assertThat(rejected.get("req_status").asText()).isEqualTo("DRAFT");

        long employeeId = createEmployee("A1001", "审批员工", root);
        long transferId = read(post("/api/talent/v1/transfers", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "transfer_type", "TRANSFER",
                "after", Map.of("org_id", rd, "position_id", positionId, "grade_id", GRADE_ID),
                "effective_date", LocalDate.now().toString(), "reason", "审批通过转岗")).getBody())
                .get("data").get("id").asLong();
        JsonNode transferred = read(post("/api/talent/v1/transfers/" + transferId + "/approval", USER_HR,
                ALL_PERMISSIONS, Map.of("approved", true)).getBody()).get("data");
        assertThat(transferred.get("tf_status").asText()).isEqualTo("EFFECTIVE");
        assertThat(transferred.get("employee").get("org_id").asLong()).isEqualTo(rd);
        assertThat(queryLong("SELECT org_id FROM fenglin_talent.talent_employee WHERE id = " + employeeId))
                .isEqualTo(rd);

        long canceledId = read(post("/api/talent/v1/transfers", USER_HR, ALL_PERMISSIONS, Map.of(
                "employee_id", employeeId, "transfer_type", "TRANSFER",
                "after", Map.of("org_id", root, "position_id", positionId),
                "effective_date", LocalDate.now().toString(), "reason", "审批驳回")).getBody())
                .get("data").get("id").asLong();
        JsonNode canceled = read(post("/api/talent/v1/transfers/" + canceledId + "/approval", USER_HR,
                ALL_PERMISSIONS, Map.of("approved", false)).getBody()).get("data");
        assertThat(canceled.get("tf_status").asText()).isEqualTo("CANCELED");
        assertThat(queryLong("SELECT org_id FROM fenglin_talent.talent_employee WHERE id = " + employeeId))
                .isEqualTo(rd);

        assertThat(AUDIT_EVENTS).anyMatch(
                event -> "TALENT_REQUISITION_APPROVED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(
                event -> "TALENT_REQUISITION_REJECTED".equals(event.eventType()));
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_TRANSFER_CANCELED".equals(event.eventType()));
    }

    // ---------- 培训与知识 ----------

    @Test
    void trainingWaitlistExamAndKnowledgePublish() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long employee1 = createEmployee("T1001", "岑一", root);
        long employee2 = createEmployee("T1002", "岑二", root);

        JsonNode course = read(post("/api/talent/v1/training/courses", USER_HR, ALL_PERMISSIONS, Map.of(
                "course_code", "CRS-001", "title", "新员工入职培训", "category", "ONBOARD",
                "mode", "ONLINE", "duration_hours", 6.5, "credit", 2.0)).getBody()).get("data");
        long courseId = course.get("id").asLong();
        assertThat(course.get("course_status").asText()).isEqualTo("DRAFT");
        JsonNode published = read(post("/api/talent/v1/training/courses/" + courseId + "/publish", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(published.get("course_status").asText()).isEqualTo("PUBLISHED");
        JsonNode courses = read(get("/api/talent/v1/training/courses?courseStatus=PUBLISHED&page=1&size=10",
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(courses.get("total").asLong()).isEqualTo(1L);

        JsonNode session = read(post("/api/talent/v1/training/courses/" + courseId + "/sessions", USER_HR,
                ALL_PERMISSIONS, Map.of(
                        "start_at", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).toString(),
                        "end_at", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).plusHours(3).toString(),
                        "capacity", 1,
                        "enroll_deadline", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(),
                        "venue", "上海 3F")).getBody()).get("data");
        long sessionId = session.get("id").asLong();

        JsonNode enrolled = read(post("/api/talent/v1/training/enrollments", USER_HR, ALL_PERMISSIONS, Map.of(
                "course_id", courseId, "session_id", sessionId, "employee_id", employee1)).getBody()).get("data");
        long enrollmentId = enrolled.get("id").asLong();
        assertThat(enrolled.get("enroll_status").asText()).isEqualTo("ENROLLED");
        assertThat(enrolled.get("waitlist").asBoolean()).isFalse();

        ResponseEntity<String> full = post("/api/talent/v1/training/enrollments", USER_HR, ALL_PERMISSIONS,
                Map.of("course_id", courseId, "session_id", sessionId, "employee_id", employee2));
        assertThat(full.getStatusCode().value()).isEqualTo(409);
        assertThat(read(full.getBody()).get("code").asText()).isEqualTo("3002005");
        long waitlistId = queryLong("SELECT id FROM fenglin_talent.talent_learning_record "
                + "WHERE employee_id = " + employee2 + " AND enroll_status = 'WAITLIST'");

        JsonNode canceled = read(delete("/api/talent/v1/training/enrollments/" + enrollmentId, USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(canceled.get("enroll_status").asText()).isEqualTo("CANCELED");
        assertThat(canceled.get("promoted_waitlist_user_id").asLong()).isEqualTo(employee2);
        assertThat(queryString("SELECT enroll_status FROM fenglin_talent.talent_learning_record WHERE id = "
                + waitlistId)).isEqualTo("ENROLLED");

        JsonNode progress = read(post("/api/talent/v1/training/learning/" + waitlistId + "/progress", USER_HR,
                ALL_PERMISSIONS, Map.of("progress", 60, "study_seconds", 600)).getBody()).get("data");
        assertThat(progress.get("progress").asInt()).isEqualTo(60);
        assertThat(progress.get("learn_status").asText()).isEqualTo("LEARNING");
        JsonNode done = read(post("/api/talent/v1/training/learning/" + waitlistId + "/progress", USER_HR,
                ALL_PERMISSIONS, Map.of("progress", 100, "study_seconds", 1200)).getBody()).get("data");
        assertThat(done.get("learn_status").asText()).isEqualTo("DONE");

        JsonNode exam = read(post("/api/talent/v1/training/exams/" + waitlistId + "/submit", USER_HR,
                ALL_PERMISSIONS, Map.of("score", 88)).getBody()).get("data");
        assertThat(exam.get("passed").asBoolean()).isTrue();
        assertThat(exam.get("learn_status").asText()).isEqualTo("DONE");
        JsonNode certificates = read(get("/api/talent/v1/training/certificates?employeeId=" + employee2,
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(certificates.size()).isEqualTo(1);

        JsonNode category = read(post("/api/talent/v1/knowledge/categories", USER_HR, ALL_PERMISSIONS,
                Map.of("name", "研发规范", "sort_no", 1)).getBody()).get("data");
        long categoryId = category.get("id").asLong();
        JsonNode article = read(post("/api/talent/v1/knowledge/articles", USER_HR, ALL_PERMISSIONS, Map.of(
                "title", "代码评审规范", "category_id", categoryId, "content", "# 评审要点",
                "tags", List.of("规范", "研发"))).getBody()).get("data");
        long articleId = article.get("id").asLong();
        assertThat(article.get("art_status").asText()).isEqualTo("DRAFT");
        read(post("/api/talent/v1/knowledge/articles/" + articleId + "/submit", USER_HR, ALL_PERMISSIONS,
                Map.of()).getBody());
        JsonNode publishedArticle = read(post("/api/talent/v1/knowledge/articles/" + articleId + "/publish",
                USER_HR, ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(publishedArticle.get("art_status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishedArticle.get("art_version").asInt()).isEqualTo(2);
        JsonNode search = read(get("/api/talent/v1/knowledge/articles?keyword=评审&page=1&size=10", USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(search.get("total").asLong()).isEqualTo(1L);
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_COURSE_ENROLLED".equals(event.eventType()));
    }

    // ---------- 技能与活水 ----------

    @Test
    void skillInventoryAndFlowApplicationApproval() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long rd = createOrg("研发中心", "FL.RD", root, "DEPT");
        long employee1 = createEmployee("S1001", "韩一", rd);
        long employee2 = createEmployee("S1002", "韩二", rd);
        long employee3 = createEmployee("S1003", "韩三", rd);

        long javaTag = read(post("/api/talent/v1/skills/tags", USER_HR, ALL_PERMISSIONS,
                Map.of("name", "Java", "category", "TECH")).getBody()).get("data").get("id").asLong();
        long sqlTag = read(post("/api/talent/v1/skills/tags", USER_HR, ALL_PERMISSIONS,
                Map.of("name", "SQL", "category", "TECH")).getBody()).get("data").get("id").asLong();

        JsonNode updated = read(post("/api/talent/v1/skills/employees/" + employee1, USER_HR, ALL_PERMISSIONS,
                Map.of("skills", List.of(
                        Map.of("skill_id", javaTag, "self_level", 4, "years", 5),
                        Map.of("skill_id", sqlTag, "self_level", 3, "years", 4)))).getBody()).get("data");
        assertThat(updated.get("updated").asInt()).isEqualTo(2);
        JsonNode employeeSkills = read(get("/api/talent/v1/skills/employees/" + employee1, USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(employeeSkills.size()).isEqualTo(2);

        JsonNode inventory = read(get("/api/talent/v1/skills/inventory?orgId=" + rd, USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(inventory.get("coverage").asDouble()).isEqualTo(0.33);
        assertThat(inventory.get("heatmap").size()).isEqualTo(2);

        JsonNode demand = read(post("/api/talent/v1/flow/demands", USER_HR, ALL_PERMISSIONS, Map.of(
                "org_id", rd, "skills_required", List.of(Map.of("skillId", javaTag, "level", 3)),
                "headcount", 2, "demand_type", "INTERNAL", "publish_scope", "ALL")).getBody()).get("data");
        long demandId = demand.get("id").asLong();
        assertThat(demand.get("demand_no").asText()).matches("TD\\d{12}");
        JsonNode published = read(post("/api/talent/v1/flow/demands/" + demandId + "/publish", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(published.get("dm_status").asText()).isEqualTo("PUBLIC");
        JsonNode market = read(get("/api/talent/v1/flow/market?page=1&size=10", USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(market.get("total").asLong()).isEqualTo(1L);

        execute("UPDATE fenglin_talent.talent_employee SET emp_status = 'PROBATION' WHERE id = " + employee3);
        ResponseEntity<String> probation = post("/api/talent/v1/flow/applications", USER_HR, ALL_PERMISSIONS,
                Map.of("demand_id", demandId, "employee_id", employee3, "apply_type", "APPLY",
                        "reason", "希望转岗"));
        assertThat(probation.getStatusCode().value()).isEqualTo(403);
        assertThat(read(probation.getBody()).get("code").asText()).isEqualTo("3003005");

        JsonNode application = read(post("/api/talent/v1/flow/applications", USER_HR, ALL_PERMISSIONS,
                Map.of("demand_id", demandId, "employee_id", employee1, "apply_type", "APPLY",
                        "reason", "希望转岗")).getBody()).get("data");
        long applicationId = application.get("id").asLong();
        assertThat(application.get("flow_status").asText()).isEqualTo("PENDING");
        ResponseEntity<String> duplicated = post("/api/talent/v1/flow/applications", USER_HR, ALL_PERMISSIONS,
                Map.of("demand_id", demandId, "employee_id", employee1, "apply_type", "APPLY"));
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);
        assertThat(read(duplicated.getBody()).get("code").asText()).isEqualTo("3003006");

        JsonNode firstApproval = read(post("/api/talent/v1/flow/applications/" + applicationId + "/approve",
                USER_HR, ALL_PERMISSIONS, Map.of("node", "CUR_MGR", "approved", true, "opinion", "同意"))
                .getBody()).get("data");
        assertThat(firstApproval.get("flow_status").asText()).isEqualTo("CUR_MGR_AGREE");
        JsonNode secondApproval = read(post("/api/talent/v1/flow/applications/" + applicationId + "/approve",
                USER_HR, ALL_PERMISSIONS, Map.of("node", "TARGET", "approved", true, "opinion", "面试通过"))
                .getBody()).get("data");
        assertThat(secondApproval.get("flow_status").asText()).isEqualTo("HR_REVIEW");
        JsonNode finalApproval = read(post("/api/talent/v1/flow/applications/" + applicationId + "/approve",
                USER_HR, ALL_PERMISSIONS, Map.of("node", "HR", "approved", true, "opinion", "办理调动"))
                .getBody()).get("data");
        assertThat(finalApproval.get("flow_status").asText()).isEqualTo("PASSED");

        JsonNode matches = read(get("/api/talent/v1/flow/demands/" + demandId + "/matches?topN=10", USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(matches.size()).isEqualTo(3);
        assertThat(matches.get(0).get("employee_id").asLong()).isEqualTo(employee1);
        assertThat(matches.get(0).get("skill_coverage").asDouble()).isEqualTo(1.0);
    }

    // ---------- 敬业度与离职预测 ----------

    @Test
    void engagementSurveyAnalysisAndAttritionScoring() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        long employee1 = createEmployee("E6001", "钱一", root);
        createEmployee("E6002", "钱二", root);

        List<Map<String, Object>> questions = List.of(
                Map.of("qid", "q1", "text", "工作投入度", "type", "SCALE", "dimension", "ENGAGEMENT",
                        "options", List.of(1, 2, 3, 4, 5)),
                Map.of("qid", "q2", "text", "推荐意愿", "type", "SCALE", "dimension", "ENPS",
                        "options", List.of(1, 2, 3, 4, 5)));
        JsonNode survey = read(post("/api/talent/v1/engagement/surveys", USER_HR, ALL_PERMISSIONS, Map.of(
                "title", "2026 Q4 敬业度", "survey_type", "QUARTERLY",
                "start_at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                "end_at", OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).toString(),
                "anonymous", true, "questions", questions)).getBody()).get("data");
        long surveyId = survey.get("id").asLong();
        assertThat(survey.get("survey_no").asText()).startsWith("SV");
        JsonNode published = read(post("/api/talent/v1/engagement/surveys/" + surveyId + "/publish", USER_HR,
                ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(published.get("target_count").asLong()).isEqualTo(2L);

        JsonNode surveyQuestions = read(get("/api/talent/v1/engagement/surveys/" + surveyId + "/questions",
                USER_HR, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(surveyQuestions.get("anonymous").asBoolean()).isTrue();
        assertThat(surveyQuestions.get("questions").size()).isEqualTo(2);

        for (int i = 0; i < 5; i++) {
            read(post("/api/talent/v1/engagement/surveys/" + surveyId + "/submit", USER_HR, ALL_PERMISSIONS,
                    Map.of("answers", List.of(Map.of("qid", "q1", "value", 4),
                            Map.of("qid", "q2", "value", 5)), "duration_sec", 120,
                            "employee_id", employee1)).getBody());
        }
        assertThat(queryString("SELECT employee_hash FROM fenglin_talent.talent_survey_response "
                + "WHERE survey_id = " + surveyId + " LIMIT 1")).isNull();
        JsonNode analysis = read(get("/api/talent/v1/engagement/surveys/" + surveyId + "/analysis", USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(analysis.get("overall").asDouble()).isEqualTo(4.5);
        assertThat(analysis.get("enps").asDouble()).isEqualTo(100.0);
        assertThat(analysis.get("org_breakdown").size()).isEqualTo(1);

        JsonNode smallSurvey = read(post("/api/talent/v1/engagement/surveys", USER_HR, ALL_PERMISSIONS, Map.of(
                "title", "小样本脉冲", "survey_type", "PULSE",
                "start_at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                "end_at", OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).toString(),
                "questions", questions)).getBody()).get("data");
        long smallSurveyId = smallSurvey.get("id").asLong();
        read(post("/api/talent/v1/engagement/surveys/" + smallSurveyId + "/publish", USER_HR, ALL_PERMISSIONS,
                Map.of()).getBody());
        read(post("/api/talent/v1/engagement/surveys/" + smallSurveyId + "/submit", USER_HR, ALL_PERMISSIONS,
                Map.of("answers", List.of(Map.of("qid", "q1", "value", 3)),
                        "employee_id", employee1)).getBody());
        ResponseEntity<String> insufficient = get("/api/talent/v1/engagement/surveys/" + smallSurveyId
                + "/analysis", USER_HR, ALL_PERMISSIONS);
        assertThat(insufficient.getStatusCode().value()).isEqualTo(403);
        assertThat(read(insufficient.getBody()).get("code").asText()).isEqualTo("3004005");

        JsonNode triggered = read(post("/api/talent/v1/attrition/score/trigger", USER_HR, ALL_PERMISSIONS,
                Map.of("scope", Map.of("org_id", root))).getBody()).get("data");
        assertThat(triggered.get("estimated_count").asLong()).isEqualTo(2L);
        JsonNode risks = read(get("/api/talent/v1/attrition/risks?page=1&size=10", USER_HR, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(risks.get("total").asLong()).isEqualTo(2L);
        assertThat(risks.get("records").get(0).get("risk_level").asText()).isIn("LOW", "MID", "HIGH");

        JsonNode intervention = read(post("/api/talent/v1/attrition/interventions", USER_HR, ALL_PERMISSIONS,
                Map.of("employee_id", employee1, "measure_type", "TALK", "content", "安排一对一沟通",
                        "owner_id", USER_HR)).getBody()).get("data");
        long interventionId = intervention.get("id").asLong();
        assertThat(intervention.get("iv_status").asText()).isEqualTo("OPEN");
        JsonNode updated = read(exchange(HttpMethod.PUT,
                "/api/talent/v1/attrition/interventions/" + interventionId, USER_HR, ALL_PERMISSIONS,
                Map.of("status", "FOLLOWING", "follow_up", "已沟通，观察一个月")).getBody()).get("data");
        assertThat(updated.get("iv_status").asText()).isEqualTo("FOLLOWING");
        assertThat(AUDIT_EVENTS).anyMatch(event -> "TALENT_ATTRITION_SCORED".equals(event.eventType()));
    }

    // ---------- OKR 与创新 ----------

    @Test
    void okrAlignmentKrProgressAndInnovation() throws Exception {
        long root = createOrg("丰麟云创", "FL", null, "COMPANY");
        JsonNode period = read(post("/api/talent/v1/okr/periods", USER_HR, ALL_PERMISSIONS, Map.of(
                "period_name", "2026 Q4", "period_type", "QUARTER",
                "start_date", LocalDate.now().minusDays(30).toString(),
                "end_date", LocalDate.now().plusDays(60).toString())).getBody()).get("data");
        long periodId = period.get("id").asLong();
        JsonNode companyObjective = read(post("/api/talent/v1/okr/objectives", USER_HR, ALL_PERMISSIONS,
                Map.of("period_id", periodId, "obj_level", "COMPANY", "objective", "提升客户满意度"))
                .getBody()).get("data");
        long companyObjectiveId = companyObjective.get("id").asLong();
        assertThat(companyObjective.get("obj_status").asText()).isEqualTo("DRAFT");
        JsonNode deptObjective = read(post("/api/talent/v1/okr/objectives", USER_HR, ALL_PERMISSIONS, Map.of(
                "period_id", periodId, "org_id", root, "obj_level", "DEPT",
                "objective", "交付质量提升", "parent_objective_id", companyObjectiveId)).getBody()).get("data");
        long deptObjectiveId = deptObjective.get("id").asLong();

        JsonNode aligned = read(post("/api/talent/v1/okr/alignments", USER_HR, ALL_PERMISSIONS, Map.of(
                "child_objective_id", deptObjectiveId, "parent_objective_id", companyObjectiveId,
                "align_type", "ALIGN", "weight", 100)).getBody()).get("data");
        assertThat(aligned.get("obj_status").asText()).isEqualTo("ALIGNED");
        ResponseEntity<String> cycle = post("/api/talent/v1/okr/alignments", USER_HR, ALL_PERMISSIONS, Map.of(
                "child_objective_id", companyObjectiveId, "parent_objective_id", deptObjectiveId));
        assertThat(cycle.getStatusCode().value()).isEqualTo(400);
        assertThat(read(cycle.getBody()).get("code").asText()).isEqualTo("3005005");

        JsonNode otherPeriod = read(post("/api/talent/v1/okr/periods", USER_HR, ALL_PERMISSIONS, Map.of(
                "period_name", "2027 Q1", "period_type", "QUARTER",
                "start_date", LocalDate.now().plusDays(61).toString(),
                "end_date", LocalDate.now().plusDays(151).toString())).getBody()).get("data");
        long otherPeriodId = otherPeriod.get("id").asLong();
        long otherObjectiveId = read(post("/api/talent/v1/okr/objectives", USER_HR, ALL_PERMISSIONS, Map.of(
                "period_id", otherPeriodId, "obj_level", "DEPT", "objective", "下季度目标")).getBody())
                .get("data").get("id").asLong();
        ResponseEntity<String> crossPeriod = post("/api/talent/v1/okr/alignments", USER_HR, ALL_PERMISSIONS,
                Map.of("child_objective_id", otherObjectiveId, "parent_objective_id", companyObjectiveId));
        assertThat(crossPeriod.getStatusCode().value()).isEqualTo(400);
        assertThat(read(crossPeriod.getBody()).get("code").asText()).isEqualTo("3005006");

        JsonNode kr = read(post("/api/talent/v1/okr/objectives/" + deptObjectiveId + "/key-results", USER_HR,
                ALL_PERMISSIONS, Map.of("title", "缺陷率下降", "metric_type", "PERCENT",
                        "start_value", 0, "target_value", 100, "weight", 100)).getBody()).get("data");
        long krId = kr.get("id").asLong();
        assertThat(kr.get("kr_status").asText()).isEqualTo("NOT_STARTED");
        JsonNode progress = read(exchange(HttpMethod.PUT, "/api/talent/v1/okr/key-results/" + krId
                + "/progress", USER_HR, ALL_PERMISSIONS, Map.of("current_value", 50)).getBody()).get("data");
        assertThat(progress.get("progress").asInt()).isEqualTo(50);
        assertThat(progress.get("kr_status").asText()).isEqualTo("ON_TRACK");
        assertThat(progress.get("objective_progress").asInt()).isEqualTo(50);

        JsonNode tree = read(get("/api/talent/v1/okr/alignments/tree?periodId=" + periodId, USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(tree.get("root").get("id").asLong()).isEqualTo(companyObjectiveId);
        assertThat(tree.get("children").size()).isEqualTo(1);
        assertThat(tree.get("children").get(0).get("objective").get("id").asLong()).isEqualTo(deptObjectiveId);

        JsonNode reviewed = read(post("/api/talent/v1/okr/objectives/" + deptObjectiveId + "/review", USER_HR,
                ALL_PERMISSIONS, Map.of("final_score", 0.8, "summary", "达成良好")).getBody()).get("data");
        assertThat(reviewed.get("obj_status").asText()).isEqualTo("CLOSED");
        assertThat(reviewed.get("final_score").asDouble()).isEqualTo(0.8);

        JsonNode proposal = read(post("/api/talent/v1/innovation/proposals", USER_HR, ALL_PERMISSIONS, Map.of(
                "title", "AI 简历初筛", "category", "TECH", "content", "用大模型加速筛选",
                "expected_benefit", "提效 50%")).getBody()).get("data");
        long proposalId = proposal.get("id").asLong();
        assertThat(proposal.get("prop_status").asText()).isEqualTo("REVIEWING");
        JsonNode voted = read(post("/api/talent/v1/innovation/proposals/" + proposalId + "/vote",
                USER_INTERVIEWER, ALL_PERMISSIONS, Map.of("comment", "有创意")).getBody()).get("data");
        assertThat(voted.get("votes").asInt()).isEqualTo(1);
        ResponseEntity<String> selfVote = post("/api/talent/v1/innovation/proposals/" + proposalId + "/vote",
                USER_HR, ALL_PERMISSIONS, Map.of());
        assertThat(selfVote.getStatusCode().value()).isEqualTo(403);
        assertThat(read(selfVote.getBody()).get("code").asText()).isEqualTo("3005015");

        JsonNode proposalReviewed = read(post("/api/talent/v1/innovation/proposals/" + proposalId + "/reviews",
                USER_HR, ALL_PERMISSIONS, Map.of("score", 85, "comment", "通过", "result", "PASS"))
                .getBody()).get("data");
        assertThat(proposalReviewed.get("rv_result").asText()).isEqualTo("PASS");
        JsonNode incubated = read(post("/api/talent/v1/innovation/proposals/" + proposalId + "/incubate",
                USER_HR, ALL_PERMISSIONS, Map.of()).getBody()).get("data");
        assertThat(incubated.get("prop_status").asText()).isEqualTo("INCUBATING");
        JsonNode proposals = read(get("/api/talent/v1/innovation/proposals?page=1&size=10", USER_HR,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(proposals.get("total").asLong()).isEqualTo(1L);
    }

    // ---------- helpers ----------

    private long createOrg(String name, String code, Long parentId, String type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("org_code", code);
        body.put("org_name", name);
        body.put("org_type", type);
        if (parentId != null) {
            body.put("parent_id", parentId);
        }
        return read(post("/api/talent/v1/orgs", USER_HR, ALL_PERMISSIONS, body).getBody())
                .get("data").get("id").asLong();
    }

    private long createPosition(long orgId) {
        return read(post("/api/talent/v1/positions", USER_HR, ALL_PERMISSIONS, Map.of(
                "position_code", "RD-JAVA", "position_name", "Java 工程师", "org_id", orgId,
                "job_family", "研发", "job_sequence", "P", "headcount", 5)).getBody())
                .get("data").get("id").asLong();
    }

    private void createPlan(long orgId, long positionId, int budget) {
        read(post("/api/talent/v1/headcount-plans", USER_HR, ALL_PERMISSIONS, Map.of(
                "plan_year", CURRENT_YEAR, "org_id", orgId, "position_id", positionId,
                "budget_headcount", budget)).getBody());
    }

    private Map<String, Object> requisitionBody(long orgId, long positionId, int headcount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("position_id", positionId);
        body.put("org_id", orgId);
        body.put("headcount", headcount);
        body.put("salary_min", 20000);
        body.put("salary_max", 40000);
        body.put("urgency", "MID");
        body.put("reason_type", "NEW");
        body.put("owner_id", USER_HR);
        return body;
    }

    private Map<String, Object> offerBody(long candidateId, long requisitionId, long positionId, int baseSalary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidate_id", candidateId);
        body.put("requisition_id", requisitionId);
        body.put("position_id", positionId);
        body.put("grade_id", GRADE_ID);
        body.put("base_salary", baseSalary);
        body.put("offer_date", LocalDate.now().toString());
        body.put("expect_join_date", LocalDate.now().plusDays(30).toString());
        return body;
    }

    private Map<String, Object> employeeBody(String empNo, String name, String phone, String idCard,
                                             long orgId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emp_no", empNo);
        body.put("name", name);
        body.put("gender", "M");
        body.put("phone", phone);
        body.put("id_card", idCard);
        body.put("email", empNo.toLowerCase() + "@fenglin.demo");
        body.put("org_id", orgId);
        body.put("employment_type", "REGULAR");
        body.put("hire_date", LocalDate.now().toString());
        body.put("work_location", "上海");
        return body;
    }

    private long createEmployee(String empNo, String name, long orgId) {
        return read(post("/api/talent/v1/employees", USER_HR, ALL_PERMISSIONS,
                employeeBody(empNo, name, null, null, orgId)).getBody()).get("data").get("id").asLong();
    }

    private void seedGrade() throws Exception {
        execute("INSERT INTO fenglin_talent.talent_grade "
                + "(id, tenant_id, grade_code, grade_name, sequence, grade_level, band_min, band_max, created_by) "
                + "VALUES (" + GRADE_ID + ", " + TENANT + ", 'P5', '高级工程师', 'P', 5, 20000, 40000, "
                + USER_HR + ")");
    }

    private ResponseEntity<String> post(String path, long userId, String permissions, Object body) {
        return exchange(HttpMethod.POST, path, userId, permissions, body);
    }

    private ResponseEntity<String> get(String path, long userId, String permissions) {
        return exchange(HttpMethod.GET, path, userId, permissions, null);
    }

    private ResponseEntity<String> delete(String path, long userId, String permissions) {
        return exchange(HttpMethod.DELETE, path, userId, permissions, null);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, long userId, String permissions,
                                            Object body) {
        return restTemplate.exchange(url(path), method,
                new HttpEntity<>(body == null ? null : write(body), headers(userId, permissions)),
                String.class);
    }

    private HttpHeaders headers(long userId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(TENANT));
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Client-Type", "ADMIN");
        headers.set("X-Request-Id", "talent-test-" + System.nanoTime());
        if (permissions != null) {
            headers.set("X-Permissions", permissions);
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

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
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

    private long queryLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
