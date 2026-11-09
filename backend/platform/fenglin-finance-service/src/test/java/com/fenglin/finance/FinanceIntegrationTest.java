package com.fenglin.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import com.fenglin.finance.client.WorkflowClient;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云财端到端主链测试：真实内嵌 PostgreSQL + 真实 HTTP。
 *
 * <p>覆盖 D1 账套/凭证/结账/ARAP/报销预算、D2 项目成本与结算、D3 折旧与盘点、D4 碳核算与 ESG；
 * 同时验证幂等、借贷平衡、制单人≠审核人、凭证哈希链与期间关闭约束。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinanceIntegrationTest {

    private static final long TENANT = 9801L;
    private static final long USER_MAKER = 7101L;
    private static final long USER_AUDITOR = 7102L;
    private static final LocalDate TODAY = LocalDate.now();
    private static final String PERIOD = TODAY.toString().substring(0, 7);

    private static final String ALL_PERMISSIONS = String.join(",",
            "tenant:finance:ledger:manage", "tenant:finance:subject:manage", "tenant:finance:period:manage",
            "biz:finance:ledger:view", "biz:finance:subject:view", "biz:finance:period:view",
            "biz:finance:report:view", "biz:finance:voucher:create", "biz:finance:voucher:submit",
            "biz:finance:voucher:audit", "biz:finance:voucher:post", "biz:finance:voucher:reverse",
            "biz:finance:voucher:view", "tenant:finance:audit:view", "biz:finance:ar:view",
            "biz:finance:ap:view", "biz:finance:receipt:create", "biz:finance:payment:create",
            "biz:finance:arap:writeoff", "biz:finance:aging:view", "biz:finance:expense:submit",
            "biz:finance:expense:approve", "biz:finance:expense:pay", "biz:finance:expense:view",
            "tenant:finance:budget:manage", "biz:finance:budget:view", "biz:finance:budget:control",
            "biz:finance:invoice:manage", "biz:finance:bank:manage", "tenant:finance:reconcile:manage",
            "biz:finance:project:create", "biz:finance:project:approve", "biz:finance:project:view",
            "biz:finance:project:member:manage", "biz:finance:timesheet:submit",
            "biz:finance:timesheet:approve", "biz:finance:timesheet:view", "biz:finance:cost:collect",
            "biz:finance:revenue:recognize", "biz:finance:settlement:create",
            "biz:finance:settlement:approve", "biz:finance:profitability:view",
            "tenant:finance:asset:category:manage", "tenant:finance:asset:stocktaking:approve",
            "tenant:finance:itasset:manage", "biz:finance:asset:view", "biz:finance:asset:create",
            "biz:finance:asset:depreciate", "biz:finance:asset:dispose", "biz:finance:asset:stocktaking",
            "biz:finance:itasset:borrow", "tenant:finance:carbon:factor:manage",
            "tenant:finance:carbon:target:manage", "tenant:finance:esg:report:publish",
            "biz:finance:carbon:submit", "biz:finance:carbon:verify", "biz:finance:carbon:calculate",
            "biz:finance:esg:manage");

    private static final String JDBC_URL;
    private static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<String> WORKFLOW_CALLS = new CopyOnWriteArrayList<>();

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_finance")
                .defaultSchema("fenglin_finance")
                .locations("classpath:db/finance-migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_finance");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_finance_app");
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
                + "FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_finance' "
                + "AND tablename <> 'flyway_schema_history' LOOP "
                + "EXECUTE 'TRUNCATE TABLE fenglin_finance.' || quote_ident(r.tablename) || ' CASCADE'; "
                + "END LOOP; END $$");
    }

    @Test
    void ledgerVoucherReportAndPeriodClose() throws Exception {
        long ledgerId = createLedger();
        assertThat(get("/api/finance/v1/ledgers/" + ledgerId + "/subjects", USER_MAKER, ALL_PERMISSIONS)
                .getBody()).contains("1002").contains("4001");
        assertThat(read(get("/api/finance/v1/ledgers/" + ledgerId + "/periods", USER_MAKER,
                ALL_PERMISSIONS).getBody()).get("data")).hasSize(12);

        ResponseEntity<String> created = postWithKey("/api/finance/v1/vouchers", USER_MAKER, "voucher-1",
                voucherBody(ledgerId, "5000", "5000"));
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode voucher = read(created.getBody()).get("data");
        long voucherId = voucher.get("id").asLong();
        assertThat(voucher.get("status").asText()).isEqualTo("DRAFT");

        ResponseEntity<String> replay = postWithKey("/api/finance/v1/vouchers", USER_MAKER, "voucher-1",
                voucherBody(ledgerId, "5000", "5000"));
        assertThat(read(replay.getBody()).get("data").get("id").asLong()).isEqualTo(voucherId);

        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/submit", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/audit", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/audit", USER_AUDITOR, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<String> posted = post("/api/finance/v1/vouchers/" + voucherId + "/post", USER_MAKER,
                ALL_PERMISSIONS, null);
        assertThat(posted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(posted.getBody()).get("data").get("status").asText()).isEqualTo("POSTED");
        assertThat(read(posted.getBody()).get("data").get("chain_hash").asText()).isNotBlank();

        JsonNode auditTrail = read(get("/api/finance/v1/vouchers/" + voucherId + "/audit-trail", USER_MAKER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(auditTrail.get("chain_valid").asBoolean()).isTrue();
        assertThat(auditTrail.get("logs")).hasSize(4);

        JsonNode trial = read(get("/api/finance/v1/ledgers/" + ledgerId + "/periods/" + PERIOD
                + "/trial-balance", USER_MAKER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(trial.get("balanced").asBoolean()).isTrue();

        JsonNode balance = read(get("/api/finance/v1/reports/balance-sheet?ledgerId=" + ledgerId
                + "&periodCode=" + PERIOD, USER_MAKER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(balance.get("balanced").asBoolean()).isTrue();
        assertThat(num(balance.get("total_assets"))).isEqualByComparingTo("5000.0000");

        ResponseEntity<String> unbalanced = post("/api/finance/v1/vouchers", USER_MAKER, ALL_PERMISSIONS,
                voucherBody(ledgerId, "100", "90"));
        assertThat(unbalanced.getStatusCode().is2xxSuccessful()).isFalse();

        assertThat(post("/api/finance/v1/ledgers/" + ledgerId + "/periods/" + PERIOD + "/close", USER_MAKER,
                ALL_PERMISSIONS, null).getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<String> closedPeriod = post("/api/finance/v1/vouchers", USER_MAKER, ALL_PERMISSIONS,
                voucherBody(ledgerId, "100", "100"));
        assertThat(closedPeriod.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(AUDIT_EVENTS).isNotEmpty();
    }

    @Test
    void receivableReceiptIsIdempotentAndSettles() throws Exception {
        long ledgerId = createLedger();
        execute("INSERT INTO fenglin_finance.fin_receivable (id, tenant_id, ar_no, customer_id, customer_name, "
                + "source_type, currency_code, amount, bill_date, due_date, status, created_by) VALUES "
                + "(5001, " + TENANT + ", 'AR-T1', 8001, '测试客户', 'MANUAL', 'CNY', 1200, CURRENT_DATE, "
                + "CURRENT_DATE + 30, 'OPEN', " + USER_MAKER + ")");

        Map<String, Object> body = Map.of("customer_id", 8001, "customer_name", "测试客户",
                "currency_code", "CNY", "amount", 1200, "pay_method", "BANK",
                "receipt_date", TODAY.toString(), "receivable_ids", List.of(5001));
        ResponseEntity<String> first = postWithKey("/api/finance/v1/receipts", USER_MAKER, "receipt-1", body);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        long receiptId = read(first.getBody()).get("data").get("id").asLong();
        assertThat(read(first.getBody()).get("data").get("write_offs")).hasSize(1);

        ResponseEntity<String> replay = postWithKey("/api/finance/v1/receipts", USER_MAKER, "receipt-1", body);
        assertThat(read(replay.getBody()).get("data").get("id").asLong()).isEqualTo(receiptId);
        assertThat(queryLong("SELECT count(1) FROM fenglin_finance.fin_receipt")).isEqualTo(1);
        assertThat(queryString("SELECT status FROM fenglin_finance.fin_receivable WHERE id = 5001"))
                .isEqualTo("SETTLED");
        assertThat(queryLong("SELECT count(1) FROM fenglin_finance.fin_voucher WHERE source_type = 'RECEIPT'"))
                .isEqualTo(1);

        assertThat(read(get("/api/finance/v1/arap/aging?type=AR", USER_MAKER, ALL_PERMISSIONS).getBody())
                .get("data").get("type").asText()).isEqualTo("AR");
        assertThat(ledgerId).isPositive();
    }

    @Test
    void expenseBudgetControlAndPayment() throws Exception {
        long ledgerId = createLedger();
        String budgetNo = "BUD-" + System.nanoTime();
        ResponseEntity<String> budget = post("/api/finance/v1/budgets", USER_MAKER, ALL_PERMISSIONS,
                Map.of("budget_no", budgetNo, "budget_name", "部门费用预算", "period_year", TODAY.getYear(),
                        "dimension", "DEPARTMENT", "dimension_id", 1, "subject_code", "6602",
                        "budget_amount", 1000, "control_mode", "STRICT", "currency_code", "CNY"));
        assertThat(budget.getStatusCode().is2xxSuccessful()).isTrue();
        long budgetId = read(budget.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/budgets/" + budgetId + "/release", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> over = post("/api/finance/v1/expenses", USER_MAKER, ALL_PERMISSIONS,
                expenseBody(1500));
        assertThat(over.getStatusCode().is2xxSuccessful()).isTrue();
        long overId = read(over.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/expenses/" + overId + "/submit", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isFalse();

        long claimId = createExpense(600);
        assertThat(post("/api/finance/v1/expenses/" + claimId + "/submit", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(num(read(get("/api/finance/v1/expenses/" + claimId, USER_MAKER, ALL_PERMISSIONS)
                .getBody()).get("data").get("budget_occupied"))).isEqualByComparingTo("600.0000");
        assertThat(post("/api/finance/v1/expenses/" + claimId + "/approve", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(queryLong("SELECT count(1) FROM fenglin_finance.fin_payable WHERE source_type = 'EXPENSE'"))
                .isEqualTo(1);

        ResponseEntity<String> paid = post("/api/finance/v1/expenses/" + claimId + "/pay", USER_MAKER,
                ALL_PERMISSIONS, Map.of("payment_date", TODAY.toString()));
        assertThat(paid.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(paid.getBody()).get("data").get("status").asText()).isEqualTo("PAID");
        assertThat(queryString("SELECT executed_amount::text FROM fenglin_finance.fin_budget "
                + "WHERE budget_no = '" + budgetNo + "'")).isEqualTo("600.0000");
        assertThat(ledgerId).isPositive();
    }

    @Test
    void projectTimesheetRevenueSettlementChain() {
        createLedger();
        ResponseEntity<String> created = post("/api/finance/v1/projects", USER_MAKER, ALL_PERMISSIONS,
                projectBody());
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        long projectId = read(created.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/projects/" + projectId + "/approve", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(post("/api/finance/v1/projects/" + projectId + "/members", USER_MAKER, ALL_PERMISSIONS,
                Map.of("employee_id", 6101, "employee_name", "张三", "member_role", "DEVELOPER",
                        "allocation_pct", 100, "cost_rate", 100, "join_date", TODAY.toString()))
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/finance/v1/projects/" + projectId + "/milestones", USER_MAKER,
                ALL_PERMISSIONS, Map.of("milestone_name", "上线验收", "plan_date", TODAY.plusDays(60).toString(),
                        "deliverable", "验收报告", "weight", 100)).getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> timesheet = post("/api/finance/v1/projects/" + projectId + "/timesheets",
                USER_MAKER, ALL_PERMISSIONS, Map.of("employee_id", 6101, "employee_name", "张三",
                        "work_date", TODAY.toString(), "hours", 8, "task_desc", "凭证模块开发"));
        assertThat(timesheet.getStatusCode().is2xxSuccessful()).isTrue();
        long timesheetId = read(timesheet.getBody()).get("data").get("id").asLong();

        assertThat(post("/api/finance/v1/projects/" + projectId + "/timesheets", USER_MAKER, ALL_PERMISSIONS,
                Map.of("employee_id", 6101, "employee_name", "张三", "work_date", TODAY.toString(),
                        "hours", 20, "task_desc", "超时")).getStatusCode().is2xxSuccessful()).isFalse();

        JsonNode approved = read(post("/api/finance/v1/projects/timesheets/approve", USER_MAKER,
                ALL_PERMISSIONS, Map.of("timesheet_ids", List.of(timesheetId))).getBody()).get("data");
        assertThat(approved.get("approved_count").asInt()).isEqualTo(1);
        assertThat(num(approved.get("cost_total"))).isEqualByComparingTo("800.0000");

        JsonNode revenue = read(post("/api/finance/v1/projects/" + projectId + "/revenue", USER_MAKER,
                ALL_PERMISSIONS, Map.of("amount", 2000)).getBody()).get("data");
        assertThat(num(revenue.get("recognized_revenue"))).isEqualByComparingTo("2000.0000");
        assertThat(revenue.get("voucher_id").asLong()).isPositive();

        ResponseEntity<String> settlement = post("/api/finance/v1/projects/" + projectId + "/settlements",
                USER_MAKER, ALL_PERMISSIONS, Map.of("settlement_amount", 5000, "currency_code", "CNY",
                        "remark", "首期结算"));
        assertThat(settlement.getStatusCode().is2xxSuccessful()).isTrue();
        long settlementId = read(settlement.getBody()).get("data").get("id").asLong();
        JsonNode settled = read(post("/api/finance/v1/projects/settlements/" + settlementId + "/approve",
                USER_MAKER, ALL_PERMISSIONS, null).getBody()).get("data");
        assertThat(settled.get("status").asText()).isEqualTo("SETTLED");
        assertThat(settled.get("receivable_id").asLong()).isPositive();

        JsonNode profitability = read(get("/api/finance/v1/projects/" + projectId + "/profitability",
                USER_MAKER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(num(profitability.get("actual_cost"))).isEqualByComparingTo("800.0000");
        assertThat(num(profitability.get("gross_profit"))).isEqualByComparingTo("1200.0000");
        assertThat(WORKFLOW_CALLS).isNotEmpty();
    }

    @Test
    void assetDepreciationDisposalAndStocktaking() throws Exception {
        long ledgerId = createLedger();
        ResponseEntity<String> category = post("/api/finance/v1/assets/categories", USER_MAKER,
                ALL_PERMISSIONS, Map.of("category_code", "IT_EQUIP", "category_name", "电子设备",
                        "useful_life_months", 12, "residual_rate", 0.05, "depreciation_method",
                        "STRAIGHT_LINE"));
        assertThat(category.getStatusCode().is2xxSuccessful()).isTrue();
        long categoryId = read(category.getBody()).get("data").get("id").asLong();

        LocalDate enableDate = LocalDate.parse(PERIOD + "-01").minusDays(5);
        ResponseEntity<String> card = post("/api/finance/v1/assets/cards", USER_MAKER, ALL_PERMISSIONS,
                Map.of("asset_name", "开发笔记本", "category_id", categoryId, "specification", "32G/1T",
                        "location", "上海", "dept_id", 1, "custodian_id", 6101,
                        "enable_date", enableDate.toString(), "original_value", 12000, "it_asset_flag", false,
                        "source", "PURCHASE"));
        assertThat(card.getStatusCode().is2xxSuccessful()).isTrue();
        long assetId = read(card.getBody()).get("data").get("id").asLong();

        ResponseEntity<String> depreciation = post("/api/finance/v1/assets/depreciation/run", USER_MAKER,
                ALL_PERMISSIONS, Map.of("ledger_id", ledgerId, "period_code", PERIOD));
        assertThat(depreciation.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode run = read(depreciation.getBody()).get("data");
        assertThat(run.get("asset_count").asInt()).isEqualTo(1);
        assertThat(num(run.get("total_amount"))).isEqualByComparingTo("950.0000");
        assertThat(queryString("SELECT net_value::text FROM fenglin_finance.fin_asset_card WHERE id = "
                + assetId)).isEqualTo("11050.0000");

        assertThat(post("/api/finance/v1/assets/depreciation/run", USER_MAKER, ALL_PERMISSIONS,
                Map.of("ledger_id", ledgerId, "period_code", PERIOD)).getStatusCode().is2xxSuccessful())
                .isFalse();

        ResponseEntity<String> stocktaking = post("/api/finance/v1/assets/stocktakings", USER_MAKER,
                ALL_PERMISSIONS, Map.of("stocktaking_name", "年度盘点", "scope_type", "ALL",
                        "plan_start_date", TODAY.toString(), "plan_end_date", TODAY.plusDays(7).toString()));
        assertThat(stocktaking.getStatusCode().is2xxSuccessful()).isTrue();
        long stocktakingId = read(stocktaking.getBody()).get("data").get("id").asLong();
        JsonNode detail = read(post("/api/finance/v1/assets/stocktakings/" + stocktakingId + "/count",
                USER_MAKER, ALL_PERMISSIONS, Map.of("asset_id", assetId, "result", "NORMAL",
                        "actual_location", "上海")).getBody()).get("data");
        assertThat(detail.get("items")).hasSize(1);
        assertThat(detail.get("items").get(0).get("result").asText()).isEqualTo("NORMAL");
        assertThat(post("/api/finance/v1/assets/stocktakings/" + stocktakingId + "/complete", USER_MAKER,
                ALL_PERMISSIONS, null).getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> disposed = post("/api/finance/v1/assets/" + assetId + "/dispose", USER_MAKER,
                ALL_PERMISSIONS, Map.of("disposal_income", 10000, "disposal_date", TODAY.toString(),
                        "reason", "报废出售"));
        assertThat(disposed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(disposed.getBody()).get("data").get("status").asText()).isEqualTo("DISPOSED");
    }

    @Test
    void carbonAccountingAndEsgReport() {
        createLedger();
        ResponseEntity<String> factor = post("/api/finance/v1/carbon/factors", USER_MAKER, ALL_PERMISSIONS,
                Map.of("factor_code", "EF-ELEC", "factor_name", "华东电网电力排放因子", "scope", "SCOPE_2",
                        "activity_type", "ELECTRICITY", "activity_unit", "kWh", "factor_value", 0.5,
                        "factor_unit", "kgCO2e/kWh", "factor_version", "2026", "source", "生态环境部",
                        "effective_from", TODAY.withDayOfYear(1).toString()));
        assertThat(factor.getStatusCode().is2xxSuccessful()).isTrue();
        long factorId = read(factor.getBody()).get("data").get("id").asLong();

        ResponseEntity<String> activity = post("/api/finance/v1/carbon/activities", USER_MAKER,
                ALL_PERMISSIONS, Map.of("org_id", 1, "dept_id", 1, "activity_type", "ELECTRICITY",
                        "scope", "SCOPE_2", "activity_date", TODAY.toString(), "amount", 1000, "unit", "kWh",
                        "factor_id", factorId, "data_source", "METER"));
        assertThat(activity.getStatusCode().is2xxSuccessful()).isTrue();
        long activityId = read(activity.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/carbon/activities/" + activityId + "/verify", USER_MAKER,
                ALL_PERMISSIONS, null).getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode result = read(post("/api/finance/v1/carbon/calculate", USER_MAKER, ALL_PERMISSIONS,
                Map.of("period_code", PERIOD)).getBody()).get("data");
        assertThat(result.get("activity_count").asInt()).isEqualTo(1);
        assertThat(num(result.get("total_emission"))).isEqualByComparingTo("500.0000");

        assertThat(post("/api/finance/v1/carbon/targets", USER_MAKER, ALL_PERMISSIONS,
                Map.of("target_name", "2026 碳减排目标", "target_year", TODAY.getYear(),
                        "base_year", TODAY.getYear() - 1, "base_emission", 1000, "target_emission", 800,
                        "owner_id", USER_MAKER)).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode targets = read(get("/api/finance/v1/carbon/targets", USER_MAKER, ALL_PERMISSIONS)
                .getBody()).get("data");
        assertThat(targets.get(0).get("status").asText()).isEqualTo("ON_TRACK");

        assertThat(post("/api/finance/v1/esg/indicators", USER_MAKER, ALL_PERMISSIONS,
                Map.of("indicator_code", "E-01", "indicator_name", "单位营收碳排放", "category", "E",
                        "unit", "tCO2e/万元", "period_year", TODAY.getYear(), "target_value", 0.5,
                        "actual_value", 0.42, "data_source", "碳核算系统")).getStatusCode().is2xxSuccessful())
                .isTrue();
        ResponseEntity<String> report = post("/api/finance/v1/esg/reports", USER_MAKER, ALL_PERMISSIONS,
                Map.of("report_name", "2026 年度 ESG 报告", "period_year", TODAY.getYear(),
                        "report_type", "ANNUAL", "report_boundary", "合并范围"));
        assertThat(report.getStatusCode().is2xxSuccessful()).isTrue();
        long reportId = read(report.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/esg/reports/" + reportId + "/publish", USER_MAKER, ALL_PERMISSIONS,
                null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(get("/api/finance/v1/esg/reports/" + reportId + "/export", USER_MAKER, ALL_PERMISSIONS)
                .getBody()).contains("report_no").contains("E-01");
    }

    @Test
    void managementOpsCloseGaps() throws Exception {
        long ledgerId = createLedger();
        String base = "/api/finance/v1/ledgers/" + ledgerId;

        long expenseSubjectId = 0;
        long financeSubjectId = 0;
        for (JsonNode subject : read(get(base + "/subjects", USER_MAKER, ALL_PERMISSIONS).getBody()).get("data")) {
            if ("6602".equals(subject.get("subject_code").asText())) {
                expenseSubjectId = subject.get("id").asLong();
            }
            if ("6603".equals(subject.get("subject_code").asText())) {
                financeSubjectId = subject.get("id").asLong();
            }
        }
        assertThat(expenseSubjectId).isPositive();
        assertThat(put(base + "/subjects/" + expenseSubjectId, USER_MAKER,
                Map.of("subject_name", "管理费用（改）")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post(base + "/subjects/" + financeSubjectId + "/disable", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();

        // 凭证模板 + 批量导入 + 一般明细账
        ResponseEntity<String> template = post("/api/finance/v1/voucher-templates", USER_MAKER,
                ALL_PERMISSIONS, Map.of("template_code", "TPL-001", "template_name", "收到投资款",
                        "biz_type", "INVESTMENT", "debit_subject_code", "1002",
                        "credit_subject_code", "4001", "auxiliary_mapping", Map.of(),
                        "condition_expr", "amount > 0"));
        assertThat(template.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(get("/api/finance/v1/voucher-templates", USER_MAKER, ALL_PERMISSIONS).getBody())
                .get("data")).hasSize(1);

        Map<String, Object> importBody = new java.util.LinkedHashMap<>();
        importBody.put("ledger_id", ledgerId);
        importBody.put("vouchers", List.of(voucherBody(ledgerId, "2000", "2000")));
        JsonNode imported = read(post("/api/finance/v1/vouchers/import", USER_MAKER, ALL_PERMISSIONS, importBody)
                .getBody()).get("data");
        assertThat(imported.get("imported").asInt()).isEqualTo(1);
        long voucherId = imported.get("voucher_ids").get(0).asLong();
        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/submit", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/audit", USER_AUDITOR, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/finance/v1/vouchers/" + voucherId + "/post", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode ledger = read(get(base + "/subjects/1002/general-ledger?periodCode=" + PERIOD, USER_MAKER,
                ALL_PERMISSIONS).getBody()).get("data");
        assertThat(ledger.get("lines")).hasSize(1);
        assertThat(num(ledger.get("closing"))).isEqualByComparingTo("2000.0000");
        long cashSubjectId = 0;
        for (JsonNode subject : read(get(base + "/subjects", USER_MAKER, ALL_PERMISSIONS).getBody()).get("data")) {
            if ("1002".equals(subject.get("subject_code").asText())) {
                cashSubjectId = subject.get("id").asLong();
            }
        }
        assertThat(post(base + "/subjects/" + cashSubjectId + "/disable", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isFalse();

        // 期间结账 → 封账 → 反结账
        assertThat(post(base + "/periods/" + PERIOD + "/close", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(post(base + "/periods/" + PERIOD + "/seal", USER_MAKER, ALL_PERMISSIONS, null).getBody())
                .get("data").get("status").asText()).isEqualTo("SEALED");
        assertThat(read(post(base + "/periods/" + PERIOD + "/reopen", USER_MAKER, ALL_PERMISSIONS, null).getBody())
                .get("data").get("status").asText()).isEqualTo("OPEN");

        // 发票
        Map<String, Object> invoiceBody = new java.util.LinkedHashMap<>();
        invoiceBody.put("invoice_code", "033001");
        invoiceBody.put("invoice_number", "INV-2026-001");
        invoiceBody.put("invoice_type", "INPUT");
        invoiceBody.put("buyer_name", "丰麟云创科技有限公司");
        invoiceBody.put("seller_name", "测试供应商");
        invoiceBody.put("amount", 1000);
        invoiceBody.put("tax_rate", 0.06);
        invoiceBody.put("invoice_date", TODAY.toString());
        ResponseEntity<String> invoice = post("/api/finance/v1/invoices", USER_MAKER, ALL_PERMISSIONS, invoiceBody);
        assertThat(invoice.getStatusCode().is2xxSuccessful()).isTrue();
        long invoiceId = read(invoice.getBody()).get("data").get("id").asLong();
        assertThat(read(post("/api/finance/v1/invoices/" + invoiceId + "/verify", USER_MAKER, ALL_PERMISSIONS,
                null).getBody()).get("data").get("verify_status").asText()).isEqualTo("VERIFIED");
        assertThat(read(get("/api/finance/v1/invoices", USER_MAKER, ALL_PERMISSIONS).getBody())
                .get("data")).hasSize(1);

        // 币种与汇率
        assertThat(post("/api/finance/v1/currencies", USER_MAKER, ALL_PERMISSIONS,
                Map.of("currency_code", "USD", "currency_name", "美元", "symbol", "$", "decimal_places", 2))
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(post("/api/finance/v1/exchange-rates", USER_MAKER, ALL_PERMISSIONS,
                Map.of("from_currency", "USD", "to_currency", "CNY", "rate", 7.1,
                        "effective_date", TODAY.toString(), "source", "PBOC")).getBody())
                .get("data").get("rate").asText()).isEqualTo("7.1000");

        // 业财规则
        assertThat(post("/api/finance/v1/rules", USER_MAKER, ALL_PERMISSIONS,
                Map.of("rule_code", "AR_AP_AUTO", "rule_name", "往来自动凭证", "rule_type", "VOUCHER",
                        "config", Map.of("auto_create", true))).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(read(get("/api/finance/v1/rules", USER_MAKER, ALL_PERMISSIONS).getBody())
                .get("data").get(0).get("config").get("auto_create").asBoolean()).isTrue();

        // 付款计划 + 预算可用额度
        execute("INSERT INTO fenglin_finance.fin_payable (id, tenant_id, ap_no, supplier_name, source_type, "
                + "currency_code, amount, settled_amount, bill_date, due_date, status, created_by) VALUES "
                + "(6001, " + TENANT + ", 'AP-T1', '测试供应商', 'MANUAL', 'CNY', 900, 0, CURRENT_DATE, "
                + "CURRENT_DATE + 10, 'OPEN', " + USER_MAKER + ")");
        JsonNode plan = read(get("/api/finance/v1/payment-plan", USER_MAKER, ALL_PERMISSIONS).getBody()).get("data");
        assertThat(plan).hasSize(1);
        assertThat(num(plan.get(0).get("outstanding"))).isEqualByComparingTo("900.0000");

        ResponseEntity<String> budget = post("/api/finance/v1/budgets", USER_MAKER, ALL_PERMISSIONS,
                Map.of("budget_no", "BUD-T1", "budget_name", "管理费用预算", "period_year", TODAY.getYear(),
                        "dimension", "DEPARTMENT", "dimension_id", 1,
                        "subject_code", "6602", "budget_amount", 1000, "control_mode", "STRICT",
                        "currency_code", "CNY"));
        long budgetId = read(budget.getBody()).get("data").get("id").asLong();
        assertThat(post("/api/finance/v1/budgets/" + budgetId + "/release", USER_MAKER, ALL_PERMISSIONS, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(num(read(get("/api/finance/v1/budgets/available?periodYear=" + TODAY.getYear()
                + "&dimension=DEPARTMENT&dimensionId=1&subjectCode=6602", USER_MAKER, ALL_PERMISSIONS)
                .getBody()).get("data").get("available"))).isEqualByComparingTo("1000.0000");
    }

    private long createLedger() {
        ResponseEntity<String> response = post("/api/finance/v1/ledgers", USER_MAKER, ALL_PERMISSIONS,
                Map.of("ledger_code", "L" + System.nanoTime(), "ledger_name", "丰麟测试账套",
                        "company_name", "丰麟云创科技有限公司", "base_currency", "CNY",
                        "fiscal_year_start_month", 1, "accounting_standard", "CAS",
                        "tax_no", "91310000MA1K000001"));
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return read(response.getBody()).get("data").get("id").asLong();
    }

    private long createExpense(long amount) {
        ResponseEntity<String> response = post("/api/finance/v1/expenses", USER_MAKER, ALL_PERMISSIONS,
                expenseBody(amount));
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return read(response.getBody()).get("data").get("id").asLong();
    }

    private Map<String, Object> expenseBody(long amount) {
        return Map.of("applicant_id", USER_MAKER, "applicant_name", "张三", "dept_id", 1,
                "claim_type", "TRAVEL", "currency_code", "CNY", "claim_date", TODAY.toString(),
                "items", List.of(Map.of("expense_date", TODAY.toString(), "expense_category", "办公费",
                        "subject_code", "6602", "amount", amount, "tax_amount", 0, "remark", "出差")));
    }

    private Map<String, Object> projectBody() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("project_name", "丰麟云财实施项目");
        body.put("project_type", "PROJECT");
        body.put("customer_id", 8001);
        body.put("customer_name", "测试客户");
        body.put("contract_no", "HT-001");
        body.put("contract_amount", 100000);
        body.put("budget_amount", 50000);
        body.put("estimated_total_cost", 60000);
        body.put("start_date", TODAY.toString());
        body.put("end_date", TODAY.plusDays(90).toString());
        body.put("manager_id", USER_MAKER);
        body.put("dept_id", 1);
        body.put("currency_code", "CNY");
        body.put("description", "端到端主链验证");
        return body;
    }

    private Map<String, Object> voucherBody(long ledgerId, String debit, String credit) {
        return Map.of("ledger_id", ledgerId, "voucher_word", "记", "voucher_date", TODAY.toString(),
                "summary", "收到投资款", "currency_code", "CNY", "entries", List.of(
                        Map.of("summary", "银行存款", "subject_code", "1002", "direction", "DEBIT",
                                "amount", new java.math.BigDecimal(debit)),
                        Map.of("summary", "实收资本", "subject_code", "4001", "direction", "CREDIT",
                                "amount", new java.math.BigDecimal(credit))));
    }

    private ResponseEntity<String> post(String path, long userId, String permissions, Object body) {
        return exchange(HttpMethod.POST, path, userId, permissions, body, null);
    }

    private ResponseEntity<String> postWithKey(String path, long userId, String idempotencyKey, Object body) {
        return exchange(HttpMethod.POST, path, userId, ALL_PERMISSIONS, body, idempotencyKey);
    }

    private ResponseEntity<String> get(String path, long userId, String permissions) {
        return exchange(HttpMethod.GET, path, userId, permissions, null, null);
    }

    private ResponseEntity<String> put(String path, long userId, Object body) {
        return exchange(HttpMethod.PUT, path, userId, ALL_PERMISSIONS, body, null);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, long userId, String permissions,
                                            Object body, String idempotencyKey) {
        return restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body == null ? null : write(body), headers(userId, permissions,
                        idempotencyKey)), String.class);
    }

    private HttpHeaders headers(long userId, String permissions, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(TENANT));
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Client-Type", "ADMIN");
        headers.set("X-Request-Id", "finance-test-" + System.nanoTime());
        if (permissions != null) {
            headers.set("X-Permissions", permissions);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
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

    private BigDecimal num(JsonNode node) {
        return new BigDecimal(node.asText());
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
