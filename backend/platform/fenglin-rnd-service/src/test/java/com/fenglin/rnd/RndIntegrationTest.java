package com.fenglin.rnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.rnd.client.WorkflowClient;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云研端到端主链测试：真实内嵌 PostgreSQL + 真实 HTTP。
 *
 * <p>覆盖 E1 产品/需求/迭代/看板/任务/缺陷/测试/代码/流水线/发布，E2 度量/洞察/配置/预警；
 * 同时验证评审与发布审批走工作流、测试失败自动登记缺陷、提交去重与 Webhook 签名。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RndIntegrationTest {

    private static final long TENANT = 9811L;
    private static final long USER_OWNER = 7201L;
    private static final long USER_DEV = 7202L;
    private static final long USER_QA = 7203L;
    private static final String ALL = "*";
    private static final String WEBHOOK_SECRET = "fenglin-rnd-webhook-dev";
    private static final LocalDate TODAY = LocalDate.now();

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_devops")
                .defaultSchema("fenglin_devops")
                .locations("classpath:db/rnd-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_devops");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_devops_app");
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
                + "FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_devops' "
                + "AND tablename NOT IN ('flyway_schema_history', 'devops_metric_config') LOOP "
                + "EXECUTE 'TRUNCATE TABLE fenglin_devops.' || quote_ident(r.tablename) || ' CASCADE'; "
                + "END LOOP; END $$");
    }

    @Test
    void productRequirementSprintTaskAndBoardChain() throws Exception {
        long productId = createProduct("PRD-A");
        assertThat(read(get("/api/rnd/v1/products/" + productId + "/statuses").getBody()).get("data")).hasSize(7);
        assertThat(read(get("/api/rnd/v1/products/" + productId + "/members").getBody()).get("data")).hasSize(1);

        assertThat(post("/api/rnd/v1/products/" + productId + "/members",
                Map.of("user_id", USER_DEV, "role_code", "developer", "data_scope", "self"))
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(get("/api/rnd/v1/products/" + productId + "/members").getBody()).get("data")).hasSize(2);

        ResponseEntity<String> requirement = post("/api/rnd/v1/requirements", Map.of(
                "product_id", productId, "title", "支持多租户隔离", "req_type", "user", "priority", "P1",
                "assignee_id", USER_DEV, "estimate_hours", 8, "acceptance_criteria", "租户数据互不可见"));
        assertThat(requirement.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode req = read(requirement.getBody()).get("data");
        long requirementId = req.get("id").asLong();
        assertThat(req.get("req_no").asText()).startsWith("REQ-");
        assertThat(req.get("status_code").asText()).isEqualTo("TODO");

        assertThat(read(post("/api/rnd/v1/requirements/" + requirementId + "/transition",
                Map.of("status_code", "DOING")).getBody()).get("data").get("status_code").asText())
                .isEqualTo("DOING");
        assertThat(post("/api/rnd/v1/requirements/" + requirementId + "/transition",
                Map.of("status_code", "REVIEWED")).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(read(post("/api/rnd/v1/requirements/" + requirementId + "/transition",
                Map.of("status_code", "DONE")).getBody()).get("data").get("status_code").asText())
                .isEqualTo("DONE");

        ResponseEntity<String> sprint = post("/api/rnd/v1/sprints", Map.of(
                "product_id", productId, "name", "Sprint 1", "goal", "主链可用",
                "start_date", TODAY.toString(), "end_date", TODAY.plusDays(10).toString(),
                "capacity_hours", 80,
                "members", List.of(Map.of("user_id", USER_DEV, "available_hours", 40))));
        assertThat(sprint.getStatusCode().is2xxSuccessful()).isTrue();
        long sprintId = read(sprint.getBody()).get("data").get("id").asLong();
        assertThat(read(post("/api/rnd/v1/sprints/" + sprintId + "/start", null).getBody())
                .get("data").get("status").asText()).isEqualTo("active");
        assertThat(read(post("/api/rnd/v1/requirements/" + requirementId + "/sprint",
                Map.of("sprint_id", sprintId)).getBody()).get("data").get("sprint_id").asLong())
                .isEqualTo(sprintId);

        ResponseEntity<String> task = post("/api/rnd/v1/tasks", Map.of(
                "product_id", productId, "title", "实现租户拦截器", "task_type", "dev",
                "sprint_id", sprintId, "requirement_id", requirementId, "assignee_id", USER_DEV,
                "estimate_hours", 8, "priority", "P1"));
        assertThat(task.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode taskNode = read(task.getBody()).get("data");
        long taskId = taskNode.get("id").asLong();
        assertThat(taskNode.get("task_no").asText()).startsWith("TASK-");

        ResponseEntity<String> worklog = post("/api/rnd/v1/worklogs", Map.of(
                "task_id", taskId, "work_date", TODAY.toString(), "hours", 4, "remark", "开发"));
        assertThat(worklog.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new java.math.BigDecimal(read(get("/api/rnd/v1/tasks/" + taskId).getBody())
                .get("data").get("actual_hours").asText())).isEqualByComparingTo("4");
        assertThat(post("/api/rnd/v1/worklogs", Map.of(
                "task_id", taskId, "work_date", TODAY.toString(), "hours", 21, "remark", "超限"))
                .getStatusCode().is2xxSuccessful()).isFalse();

        assertThat(read(post("/api/rnd/v1/tasks/" + taskId + "/transition", Map.of("status", "doing"))
                .getBody()).get("data").get("status").asText()).isEqualTo("doing");
        assertThat(read(post("/api/rnd/v1/tasks/" + taskId + "/transition", Map.of("status", "done"))
                .getBody()).get("data").get("status").asText()).isEqualTo("done");

        JsonNode burndown = read(get("/api/rnd/v1/sprints/" + sprintId + "/burndown").getBody()).get("data");
        assertThat(burndown.get("points")).isNotEmpty();
        assertThat(burndown.get("done_tasks").asInt()).isEqualTo(1);

        ResponseEntity<String> board = post("/api/rnd/v1/boards", Map.of(
                "product_id", productId, "name", "研发看板", "board_type", "scrum",
                "columns", List.of(
                        Map.of("column_name", "待处理", "status_codes", List.of("todo")),
                        Map.of("column_name", "进行中", "status_codes", List.of("doing"), "wip_limit", 2))));
        assertThat(board.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode boardNode = read(board.getBody()).get("data");
        long boardId = boardNode.get("id").asLong();
        long doingColumnId = boardNode.get("columns").get(1).get("id").asLong();
        ResponseEntity<String> moved = post("/api/rnd/v1/boards/" + boardId + "/move",
                Map.of("biz_type", "task", "biz_id", taskId, "column_id", doingColumnId));
        assertThat(moved.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(moved.getBody()).get("data").get("columns").get(1).get("item_count").asInt())
                .isGreaterThanOrEqualTo(1);
        assertThat(AUDIT_EVENTS).isNotEmpty();
    }

    @Test
    void defectAndTestExecutionChain() throws Exception {
        long productId = createProduct("PRD-B");
        ResponseEntity<String> defect = post("/api/rnd/v1/defects", Map.of(
                "product_id", productId, "title", "看板拖动后状态未刷新", "severity", "blocker",
                "priority", "P0", "env", "test", "found_stage", "test", "assignee_id", USER_DEV));
        assertThat(defect.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode defectNode = read(defect.getBody()).get("data");
        long defectId = defectNode.get("id").asLong();
        assertThat(defectNode.get("defect_no").asText()).startsWith("BUG-");
        for (String status : List.of("confirmed", "fixing", "fixed", "verifying", "closed", "reopened")) {
            ResponseEntity<String> moved = post("/api/rnd/v1/defects/" + defectId + "/transition",
                    Map.of("status", status));
            assertThat(moved.getStatusCode().is2xxSuccessful()).as("缺陷流转到 %s", status).isTrue();
        }
        assertThat(read(get("/api/rnd/v1/defects/" + defectId).getBody()).get("data").get("reopen_count")
                .asInt()).isEqualTo(1);

        ResponseEntity<String> testCase = post("/api/rnd/v1/test-cases", Map.of(
                "product_id", productId, "title", "看板卡片拖动", "module", "看板", "level", "P1",
                "case_type", "functional", "steps_json", "[{\"action\":\"拖动卡片\"}]"));
        assertThat(testCase.getStatusCode().is2xxSuccessful()).isTrue();
        long caseId = read(testCase.getBody()).get("data").get("id").asLong();
        assertThat(read(post("/api/rnd/v1/test-cases/" + caseId + "/review?reviewStatus=approved", null)
                .getBody()).get("data").get("review_status").asText()).isEqualTo("approved");

        ResponseEntity<String> plan = post("/api/rnd/v1/test-plans", Map.of(
                "product_id", productId, "name", "迭代一回归", "start_date", TODAY.toString(),
                "end_date", TODAY.plusDays(3).toString()));
        assertThat(plan.getStatusCode().is2xxSuccessful()).isTrue();
        long planId = read(plan.getBody()).get("data").get("id").asLong();

        long executionId = read(post("/api/rnd/v1/test-plans/" + planId + "/executions",
                Map.of("executor_id", USER_QA)).getBody()).get("data").get("id").asLong();
        JsonNode execution = read(post("/api/rnd/v1/executions/" + executionId + "/items",
                Map.of("case_id", caseId, "result", "fail", "remark", "状态未刷新")).getBody()).get("data");
        assertThat(execution.get("result").asText()).isEqualTo("fail");
        assertThat(execution.get("fail_count").asInt()).isEqualTo(1);
        assertThat(read(get("/api/rnd/v1/defects?productId=" + productId + "&size=20").getBody())
                .get("data")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void codePipelineAndReleaseChain() throws Exception {
        long productId = createProduct("PRD-C");
        long requirementId = read(post("/api/rnd/v1/requirements", Map.of(
                "product_id", productId, "title", "代码关联需求", "priority", "P2"))
                .getBody()).get("data").get("id").asLong();

        ResponseEntity<String> repo = post("/api/rnd/v1/repos", Map.of(
                "product_id", productId, "repo_name", "fenglin-rnd", "vcs_type", "gitlab",
                "repo_url", "https://gitlab.example.com/fenglin/rnd.git", "default_branch", "main",
                "access_token", "glpat-1234567890", "webhook_secret", "hook-secret"));
        assertThat(repo.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode repoNode = read(repo.getBody()).get("data");
        long repoId = repoNode.get("id").asLong();
        assertThat(repoNode.get("token_masked").asText()).isEqualTo("glpa****7890");
        assertThat(repoNode.get("token_masked").asText()).doesNotContain("12345");
        assertThat(read(post("/api/rnd/v1/repos/" + repoId + "/sync", null).getBody())
                .get("data").get("sync_status").asText()).isEqualTo("ok");

        Map<String, Object> commitBody = new LinkedHashMap<>();
        commitBody.put("repo_id", repoId);
        commitBody.put("commit_sha", "abc123def456");
        commitBody.put("branch", "main");
        commitBody.put("author_name", "张三");
        commitBody.put("author_email", "zhangsan@example.com");
        commitBody.put("add_lines", 120);
        commitBody.put("del_lines", 30);
        commitBody.put("files_changed", 4);
        commitBody.put("message", "feat: 租户隔离");
        commitBody.put("committed_at", TODAY + "T10:00:00+08:00");
        ResponseEntity<String> commit = post("/api/rnd/v1/commits", commitBody);
        assertThat(commit.getStatusCode().is2xxSuccessful()).isTrue();
        long commitId = read(commit.getBody()).get("data").get("id").asLong();
        ResponseEntity<String> replay = post("/api/rnd/v1/commits", commitBody);
        assertThat(read(replay.getBody()).get("data").get("id").asLong()).isEqualTo(commitId);
        assertThat(queryLong("SELECT count(1) FROM fenglin_devops.devops_commit")).isEqualTo(1);

        assertThat(post("/api/rnd/v1/commits/" + commitId + "/links", Map.of(
                "biz_type", "requirement", "biz_id", requirementId, "link_type", "manual"))
                .getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> webhook = postWithHeaders("/api/rnd/v1/webhooks/gitlab",
                Map.of("X-Webhook-Signature", WEBHOOK_SECRET),
                objectMapper.writeValueAsString(Map.of("object_kind", "push", "after", "abc123def456",
                        "project", Map.of("git_http_url", "https://gitlab.example.com/fenglin/rnd.git"),
                        "commits", List.of(Map.of("id", "abc123def456")))));
        assertThat(webhook.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(webhook.getBody()).get("data").get("event_type").asText()).isEqualTo("push");

        ResponseEntity<String> pipeline = post("/api/rnd/v1/pipelines", Map.of(
                "product_id", productId, "name", "主链流水线", "ci_type", "jenkins",
                "trigger_mode", "manual", "repo_id", repoId));
        assertThat(pipeline.getStatusCode().is2xxSuccessful()).isTrue();
        long pipelineId = read(pipeline.getBody()).get("data").get("id").asLong();
        ResponseEntity<String> run = post("/api/rnd/v1/pipelines/" + pipelineId + "/runs",
                Map.of("trigger_type", "manual"));
        assertThat(run.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode runNode = read(run.getBody()).get("data");
        long runId = runNode.get("id").asLong();
        assertThat(runNode.get("run_no").asText()).startsWith("RUN-");

        JsonNode finished = read(post("/api/rnd/v1/runs/" + runId + "/finish", Map.of(
                "status", "success", "artifact_name", "fenglin-rnd.jar", "artifact_version", "1.0.0",
                "artifact_url", "https://artifacts.example.com/rnd.jar", "quality_gate", "pass")).getBody())
                .get("data");
        assertThat(finished.get("status").asText()).isEqualTo("success");
        assertThat(finished.get("build_no").asText()).startsWith("BUILD-");
        long buildId = finished.get("build_id").asLong();

        ResponseEntity<String> release = post("/api/rnd/v1/releases", Map.of(
                "product_id", productId, "release_version", "1.0.0", "env", "staging", "build_id", buildId));
        assertThat(release.getStatusCode().is2xxSuccessful()).isTrue();
        long releaseId = read(release.getBody()).get("data").get("id").asLong();
        assertThat(read(post("/api/rnd/v1/releases/" + releaseId + "/submit", null).getBody())
                .get("data").get("status").asText()).isEqualTo("pending_approval");
        assertThat(WORKFLOW_CALLS).anyMatch(call -> call.startsWith("rndReleaseApproval:release:" + releaseId));
        assertThat(read(post("/api/rnd/v1/releases/" + releaseId + "/approve", null).getBody())
                .get("data").get("status").asText()).isEqualTo("approved");
        assertThat(read(post("/api/rnd/v1/releases/" + releaseId + "/deploy",
                Map.of("strategy", "blue_green")).getBody()).get("data").get("result").asText())
                .isEqualTo("success");
        assertThat(read(get("/api/rnd/v1/releases/" + releaseId + "/deployments").getBody()).get("data"))
                .hasSize(1);
        assertThat(read(post("/api/rnd/v1/releases/" + releaseId + "/rollback", null).getBody())
                .get("data").get("result").asText()).isEqualTo("rolled_back");
        assertThat(read(get("/api/rnd/v1/releases/" + releaseId).getBody()).get("data").get("status").asText())
                .isEqualTo("rolled_back");
    }

    @Test
    void metricComputeInsightConfigAndAlert() throws Exception {
        long productId = createProduct("PRD-D");
        long releaseId = read(post("/api/rnd/v1/releases", Map.of(
                "product_id", productId, "release_version", "2.0.0", "env", "prod")).getBody())
                .get("data").get("id").asLong();
        assertThat(post("/api/rnd/v1/releases/" + releaseId + "/submit", null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/rnd/v1/releases/" + releaseId + "/approve", null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/rnd/v1/releases/" + releaseId + "/deploy", Map.of("strategy", "rolling"))
                .getStatusCode().is2xxSuccessful()).isTrue();

        String periodKey = YearMonth.now().toString();
        ResponseEntity<String> computed = post("/api/rnd/v1/metrics/compute", Map.of(
                "product_id", productId, "period_type", "month", "period_key", periodKey));
        assertThat(computed.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = read(computed.getBody()).get("data");
        assertThat(result.get("metrics")).hasSize(10);
        assertThat(result.get("dora_level").asText()).isNotBlank();

        assertThat(read(get("/api/rnd/v1/metrics?productId=" + productId + "&periodType=month&periodKey="
                + periodKey).getBody()).get("data")).hasSize(10);
        JsonNode dora = read(get("/api/rnd/v1/metrics/dora?productId=" + productId + "&periodType=month&periodKey="
                + periodKey).getBody()).get("data");
        assertThat(dora.get("level").asText()).isNotBlank();
        JsonNode insight = read(get("/api/rnd/v1/metrics/insight?productId=" + productId
                + "&periodType=month&periodKey=" + periodKey).getBody()).get("data");
        assertThat(insight.get("data_quality").asText()).isNotBlank();
        assertThat(read(get("/api/rnd/v1/metric-configs").getBody()).get("data")).hasSize(10);

        ResponseEntity<String> config = post("/api/rnd/v1/metric-configs", Map.of(
                "metric_code", "delivery.throughput", "metric_name", "需求交付吞吐",
                "metric_group", "delivery", "formula", "count(done)/period", "weight", 1.0,
                "target_value", 20, "unit", "个"));
        assertThat(config.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> alert = post("/api/rnd/v1/metric-alerts", Map.of(
                "metric_code", "dora.change_failure_rate", "scope_type", "product", "scope_id", productId,
                "threshold", 0, "compare_op", "gte", "notify_channel", "im"));
        assertThat(alert.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode evaluated = read(post("/api/rnd/v1/metrics/alerts/evaluate?productId=" + productId
                + "&periodType=month&periodKey=" + periodKey, null).getBody()).get("data");
        assertThat(evaluated).anyMatch(node -> "triggered".equals(node.get("status").asText()));
    }

    @Test
    void managementConfigurationCoverage() throws Exception {
        long productId = createProduct("PRD-E");

        ResponseEntity<String> productUpdated = put("/api/rnd/v1/products/" + productId,
                Map.of("name", "丰麟云研-PRD-E-更新", "sprint_cycle_days", 21));
        assertThat(productUpdated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(productUpdated.getBody()).get("data").get("sprint_cycle_days").asInt()).isEqualTo(21);

        long memberId = read(post("/api/rnd/v1/products/" + productId + "/members",
                Map.of("user_id", USER_DEV, "role_code", "developer", "data_scope", "self"))
                .getBody()).get("data").get("id").asLong();
        JsonNode member = read(put("/api/rnd/v1/products/" + productId + "/members/" + memberId,
                Map.of("role_code", "tester", "data_scope", "all")).getBody()).get("data");
        assertThat(member.get("role_code").asText()).isEqualTo("tester");
        assertThat(delete("/api/rnd/v1/products/" + productId + "/members/" + memberId)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(get("/api/rnd/v1/products/" + productId + "/members").getBody()).get("data")).hasSize(1);

        JsonNode tenantConfig = read(put("/api/rnd/v1/tenant-config", Map.of(
                "config_json", Map.of("gitlab_url", "https://gitlab.example.com"),
                "timezone", "Asia/Shanghai", "data_retention_months", 36,
                "dora_target_level", "elite", "webhook_base_url", "https://hook.example.com"))
                .getBody()).get("data");
        assertThat(tenantConfig.get("data_retention_months").asInt()).isEqualTo(36);
        assertThat(tenantConfig.get("config_json").get("gitlab_url").asText())
                .isEqualTo("https://gitlab.example.com");
        assertThat(read(get("/api/rnd/v1/tenant-config").getBody()).get("data")
                .get("dora_target_level").asText()).isEqualTo("elite");

        ResponseEntity<String> configUpdated = put("/api/rnd/v1/metric-configs/delivery.throughput",
                Map.of("metric_name", "需求交付吞吐", "weight", 2.0, "target_value", 30, "unit", "个",
                        "status", "enabled"));
        assertThat(configUpdated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new java.math.BigDecimal(read(configUpdated.getBody()).get("data").get("weight").asText()))
                .isEqualByComparingTo("2");
        // 公式变更必须走口径版本发布
        assertThat(put("/api/rnd/v1/metric-configs/delivery.throughput",
                Map.of("formula", "count(new)/period")).getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode caliber = read(post("/api/rnd/v1/metric-calibers", Map.of(
                "metric_code", "delivery.throughput", "caliber_version", "v2",
                "formula", "count(done)/sprint", "effective_from", TODAY + "T00:00:00+08:00"))
                .getBody()).get("data");
        assertThat(caliber.get("caliber_version").asText()).isEqualTo("v2");
        assertThat(read(get("/api/rnd/v1/metric-calibers?metricCode=delivery.throughput").getBody())
                .get("data")).hasSize(1);
        assertThat(read(get("/api/rnd/v1/metric-configs").getBody()).get("data")).hasSize(10);

        long alertId = read(post("/api/rnd/v1/metric-alerts", Map.of(
                "metric_code", "dora.change_failure_rate", "scope_type", "product", "scope_id", productId,
                "threshold", 0.15, "compare_op", "gte", "notify_channel", "im"))
                .getBody()).get("data").get("id").asLong();
        JsonNode alertUpdated = read(put("/api/rnd/v1/metric-alerts/" + alertId,
                Map.of("threshold", 0.2, "status", "active")).getBody()).get("data");
        assertThat(new java.math.BigDecimal(alertUpdated.get("threshold").asText()))
                .isEqualByComparingTo("0.2");
        assertThat(read(post("/api/rnd/v1/metric-alerts/" + alertId + "/resolve", null).getBody())
                .get("data").get("status").asText()).isEqualTo("resolved");
        assertThat(read(get("/api/rnd/v1/metric-alerts?status=resolved").getBody()).get("data")).hasSize(1);

        String periodKey = YearMonth.now().toString();
        assertThat(post("/api/rnd/v1/metrics/compute", Map.of("product_id", productId,
                "period_type", "month", "period_key", periodKey)).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode quality = read(get("/api/rnd/v1/metric-data-quality?metricCode=delivery.throughput"
                + "&periodType=month&periodKey=" + periodKey).getBody()).get("data");
        assertThat(quality.get("sample_count").asInt()).isEqualTo(1);
        assertThat(quality.get("quality_flag").asText()).isNotBlank();

        long repoId = read(post("/api/rnd/v1/repos", Map.of(
                "product_id", productId, "repo_name", "fenglin-rnd", "vcs_type", "gitlab",
                "repo_url", "https://gitlab.example.com/fenglin/rnd2.git", "default_branch", "main",
                "access_token", "glpat-abcdefgh", "webhook_secret", "hook-2")).getBody())
                .get("data").get("id").asLong();
        JsonNode repoUpdated = read(put("/api/rnd/v1/repos/" + repoId, Map.of(
                "repo_name", "fenglin-rnd-v2", "default_branch", "release",
                "access_token", "glpat-1234567890")).getBody()).get("data");
        assertThat(repoUpdated.get("repo_name").asText()).isEqualTo("fenglin-rnd-v2");
        assertThat(repoUpdated.get("token_masked").asText()).isEqualTo("glpa****7890");
        assertThat(delete("/api/rnd/v1/repos/" + repoId).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(get("/api/rnd/v1/repos/" + repoId).getStatusCode().is2xxSuccessful()).isFalse();
    }

    private long createProduct(String code) {
        ResponseEntity<String> response = post("/api/rnd/v1/products", Map.of(
                "product_code", code + "-" + System.nanoTime(), "name", "丰麟云研" + code,
                "product_line", "devops", "owner_id", USER_OWNER, "visibility", "private",
                "sprint_cycle_days", 14, "description", "端到端主链验证"));
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return read(response.getBody()).get("data").get("id").asLong();
    }

    private ResponseEntity<String> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, Map.of());
    }

    private ResponseEntity<String> put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, Map.of());
    }

    private ResponseEntity<String> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, Map.of());
    }

    private ResponseEntity<String> postWithHeaders(String path, Map<String, String> extra, Object body) {
        return exchange(HttpMethod.POST, path, body, extra);
    }

    private ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, Map.of());
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Object body,
                                            Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(TENANT));
        headers.set("X-User-Id", String.valueOf(USER_OWNER));
        headers.set("X-Client-Type", "ADMIN");
        headers.set("X-Request-Id", "rnd-test-" + System.nanoTime());
        headers.set("X-Permissions", ALL);
        extraHeaders.forEach(headers::set);
        String payload = body == null ? null : (body instanceof String text ? text : write(body));
        return restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(payload, headers), String.class);
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
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

    private long queryLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
