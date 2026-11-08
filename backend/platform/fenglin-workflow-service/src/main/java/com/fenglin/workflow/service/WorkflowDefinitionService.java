package com.fenglin.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.workflow.mapper.WorkflowDefinitionMapper;
import com.fenglin.workflow.web.dto.DefinitionView;
import com.fenglin.workflow.web.dto.DeployDefinitionRequest;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class WorkflowDefinitionService {

    private static final Pattern PROCESS_KEY = Pattern.compile("[A-Za-z0-9_]{1,128}");
    private static final int MAX_PAGE_SIZE = 200;

    private final WorkflowDefinitionMapper definitionMapper;
    private final RepositoryService repositoryService;
    private final WorkflowAuditor auditor;

    public WorkflowDefinitionService(WorkflowDefinitionMapper definitionMapper,
                                     RepositoryService repositoryService,
                                     WorkflowAuditor auditor) {
        this.definitionMapper = definitionMapper;
        this.repositoryService = repositoryService;
        this.auditor = auditor;
    }

    @Transactional
    public DefinitionView deploy(DeployDefinitionRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Long operatorId = currentUserId();
        String processKey = request == null ? null : request.processKey();
        String bpmnXml = request == null ? null : request.bpmnXml();
        if (processKey == null || !PROCESS_KEY.matcher(processKey).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "processKey 必须为 1~128 位字母、数字或下划线");
        }
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "bpmnXml 不能为空");
        }

        Deployment deployment;
        ProcessDefinition processDefinition;
        try {
            deployment = repositoryService.createDeployment()
                    .name(processKey + "#" + System.currentTimeMillis())
                    .tenantId(String.valueOf(tenantId))
                    .addString(processKey + ".bpmn20.xml", bpmnXml)
                    .deploy();
            processDefinition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();
        } catch (FlowableException ex) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "流程定义部署失败: " + rootMessage(ex));
        }
        if (processDefinition == null) {
            repositoryService.deleteDeployment(deployment.getId(), true);
            throw new BizException(CommonErrorCode.BAD_REQUEST, "BPMN 中未找到可执行流程定义");
        }
        if (!processKey.equals(processDefinition.getKey())) {
            repositoryService.deleteDeployment(deployment.getId(), true);
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "processKey 与 BPMN process id 不一致: " + processDefinition.getKey());
        }

        definitionMapper.suspendPublished(tenantId, processKey, operatorId);
        long definitionId = IdWorker.getId();
        String name = request.name() == null || request.name().isBlank()
                ? (processDefinition.getName() == null ? processKey : processDefinition.getName())
                : request.name();
        definitionMapper.insert(definitionId, tenantId, processKey, name, request.category(),
                processDefinition.getVersion(), bpmnXml, request.formKey(), operatorId);
        auditor.publish(tenantId, "WF_DEFINITION_DEPLOYED", "WORKFLOW_DEFINITION",
                processKey + ":" + processDefinition.getVersion(), "MEDIUM");
        return new DefinitionView(definitionId, processKey, name, request.category(),
                processDefinition.getVersion(), "PUBLISHED", request.formKey(), Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResult<DefinitionView> list(String category, String status, Integer page, Integer size) {
        long tenantId = TenantContext.requireTenantId();
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        List<Map<String, Object>> rows = definitionMapper.page(tenantId, blankToNull(category),
                blankToNull(status), currentSize, (currentPage - 1) * currentSize);
        long total = definitionMapper.count(tenantId, blankToNull(category), blankToNull(status));
        List<DefinitionView> views = rows.stream()
                .map(row -> new DefinitionView(
                        longValue(row.get("id")),
                        (String) row.get("process_key"),
                        (String) row.get("name"),
                        (String) row.get("category"),
                        intValue(row.get("def_version")),
                        (String) row.get("status"),
                        (String) row.get("form_key"),
                        toInstant(row.get("created_at"))))
                .toList();
        return PageResult.of(views, total, currentPage, currentSize);
    }

    private Long currentUserId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        return snapshot == null ? null : snapshot.userId();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
