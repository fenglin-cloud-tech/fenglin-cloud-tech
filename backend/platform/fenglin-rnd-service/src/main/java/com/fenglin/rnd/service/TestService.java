package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.TestMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.DefectCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionItemRequest;
import com.fenglin.rnd.web.dto.RndDtos.ExecutionView;
import com.fenglin.rnd.web.dto.RndDtos.TestCaseCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TestCaseView;
import com.fenglin.rnd.web.dto.RndDtos.TestPlanCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TestPlanView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * E1 测试：用例评审、测试计划与执行，执行失败自动登记验证缺陷。
 */
@Service
@Transactional(readOnly = true)
public class TestService {

    private final TestMapper testMapper;
    private final CommonsMapper commonsMapper;
    private final DefectService defectService;
    private final ProductService productService;
    private final RndAudit audit;
    private final ObjectMapper objectMapper;

    public TestService(TestMapper testMapper, CommonsMapper commonsMapper, DefectService defectService,
                       ProductService productService, RndAudit audit, ObjectMapper objectMapper) {
        this.testMapper = testMapper;
        this.commonsMapper = commonsMapper;
        this.defectService = defectService;
        this.productService = productService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TestCaseView createCase(TestCaseCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        String steps = request.stepsJson() == null || request.stepsJson().isBlank() ? "[]" : request.stepsJson();
        assertJsonArray(steps);
        long id = IdWorker.getId();
        testMapper.insertCase(id, tenantId, request.productId(), "CASE-" + commonsMapper.nextCaseNo(),
                RndContext.requireText(request.title(), "title"), RndContext.text(request.module()),
                request.level() == null ? "P2" : request.level(),
                request.caseType() == null ? "functional" : request.caseType(),
                RndContext.text(request.preconditions()), steps, request.requirementId(), userId);
        audit.publish("TEST_CASE_CREATED", "create", "devops_test_case", id, "low");
        return getCase(id);
    }

    public TestCaseView getCase(long id) {
        Map<String, Object> row = testMapper.findCase(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toCaseView(row);
    }

    public List<TestCaseView> listCases(long productId, String reviewStatus, Integer page, Integer size) {
        return testMapper.listCases(RndContext.tenantId(), productId, RndContext.text(reviewStatus),
                        RndContext.size(size), RndContext.offset(page, size))
                .stream().map(TestService::toCaseView).toList();
    }

    @Transactional
    public TestCaseView reviewCase(long id, String reviewStatus) {
        long tenantId = RndContext.tenantId();
        getCase(id);
        String target = RndContext.requireText(reviewStatus, "reviewStatus");
        if (!List.of("reviewing", "approved", "rejected").contains(target)) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        testMapper.updateReviewStatus(tenantId, id, target, RndContext.userId());
        audit.publish("TEST_CASE_REVIEWED", "review", "devops_test_case", id, "low");
        return getCase(id);
    }

    @Transactional
    public TestPlanView createPlan(TestPlanCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long id = IdWorker.getId();
        testMapper.insertPlan(id, tenantId, request.productId(), "PLAN-" + commonsMapper.nextPlanNo(),
                RndContext.requireText(request.name(), "name"), request.sprintId(), request.buildId(), userId,
                request.startDate(), request.endDate(), userId);
        audit.publish("TEST_PLAN_CREATED", "create", "devops_test_plan", id, "low");
        return getPlan(id);
    }

    public TestPlanView getPlan(long id) {
        Map<String, Object> row = testMapper.findPlan(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return new TestPlanView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "planNo"),
                Rows.str(row, "name"), Rows.lng(row, "sprintId"), Rows.lng(row, "buildId"),
                Rows.str(row, "status"), Rows.intg(row, "caseCount"), Rows.intg(row, "passCount"),
                Rows.intg(row, "failCount"), Rows.str(row, "createdAt"));
    }

    @Transactional
    public ExecutionView createExecution(long planId, ExecutionCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        Map<String, Object> plan = testMapper.findPlan(tenantId, planId);
        if (plan == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        long executorId = request == null || request.executorId() == null ? userId : request.executorId();
        long id = IdWorker.getId();
        testMapper.insertExecution(id, tenantId, planId, executorId, Rows.lng(plan, "buildId"), userId);
        testMapper.updatePlanStatus(tenantId, planId, "running", userId);
        audit.publish("TEST_EXECUTION_STARTED", "create", "devops_test_execution", id, "low");
        return getExecution(id);
    }

    public ExecutionView getExecution(long id) {
        Map<String, Object> row = testMapper.findExecution(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return new ExecutionView(Rows.lng(row, "id"), Rows.lng(row, "planId"),
                Rows.lng(row, "executorId"), Rows.str(row, "result"), Rows.intg(row, "passCount"),
                Rows.intg(row, "failCount"), Rows.str(row, "executedAt"));
    }

    /**
     * 登记执行结果：用例必须评审通过；失败且未关联缺陷时自动登记生产/测试缺陷。
     */
    @Transactional
    public ExecutionView recordItem(long executionId, ExecutionItemRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.caseId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        Map<String, Object> execution = testMapper.findExecution(tenantId, executionId);
        if (execution == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> testCase = testMapper.findCase(tenantId, request.caseId());
        if (testCase == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"approved".equals(Rows.str(testCase, "reviewStatus"))) {
            throw new BizException(RndErrorCode.TEST_CASE_NOT_APPROVED);
        }
        Map<String, Object> plan = testMapper.findPlan(tenantId, Rows.lng(execution, "planId"));
        String result = request.result() == null ? "pass" : request.result();
        if (!List.of("pass", "fail", "blocked", "skip").contains(result)) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        Long defectId = null;
        if ("fail".equals(result)) {
            DefectCreateRequest defectRequest = new DefectCreateRequest(Rows.lng(plan, "productId"),
                    "测试失败：" + Rows.str(testCase, "title"), "normal", "P2", "test", "test",
                    Rows.lng(plan, "sprintId"), Rows.lng(testCase, "requirementId"), request.caseId(),
                    Rows.lng(execution, "executorId"), request.remark());
            defectId = defectService.create(defectRequest).id();
        }
        long itemId = IdWorker.getId();
        testMapper.insertExecutionItem(itemId, tenantId, executionId, request.caseId(), defectId,
                RndContext.text(request.remark()), userId);
        testMapper.updateExecutionItem(tenantId, itemId, result, defectId, RndContext.text(request.remark()), userId);

        long pass = testMapper.countItems(tenantId, executionId, "pass");
        long fail = testMapper.countItems(tenantId, executionId, "fail");
        long pending = testMapper.countItems(tenantId, executionId, "pending");
        long total = testMapper.countItemsTotal(tenantId, executionId);
        ExecutionView view;
        if (pending == 0) {
            String finalResult = fail > 0 ? "fail" : "pass";
            testMapper.updateExecution(tenantId, executionId, finalResult, pass, fail, userId);
            testMapper.updatePlanStatus(tenantId, Rows.lng(execution, "planId"), "finished", userId);
            view = getExecution(executionId);
        } else {
            testMapper.updateExecution(tenantId, executionId, "pending", pass, fail, userId);
            view = getExecution(executionId);
        }
        audit.publish("TEST_CASE_EXECUTED", "execute", "devops_test_execution_item", itemId,
                "fail".equals(result) ? "medium" : "low");
        return view;
    }

    private void assertJsonArray(String json) {
        try {
            if (!objectMapper.readTree(json).isArray()) {
                throw new BizException(RndErrorCode.PARAM_INVALID);
            }
        } catch (JsonProcessingException ex) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
    }

    public static TestCaseView toCaseView(Map<String, Object> row) {
        return new TestCaseView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "caseNo"),
                Rows.str(row, "title"), Rows.str(row, "module"), Rows.str(row, "level"),
                Rows.str(row, "caseType"), Rows.str(row, "preconditions"), Rows.str(row, "steps"),
                Rows.lng(row, "requirementId"), Rows.str(row, "reviewStatus"), Rows.str(row, "status"),
                Rows.str(row, "createdAt"));
    }
}
