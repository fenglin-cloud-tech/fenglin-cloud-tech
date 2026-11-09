package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.PipelineMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.PipelineCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.PipelineView;
import com.fenglin.rnd.web.dto.RndDtos.RunFinishRequest;
import com.fenglin.rnd.web.dto.RndDtos.RunView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * E1 流水线：配置、触发、回调收口（运行结束同时落构建产物）。
 */
@Service
@Transactional(readOnly = true)
public class PipelineService {

    private final PipelineMapper pipelineMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final RndAudit audit;

    public PipelineService(PipelineMapper pipelineMapper, CommonsMapper commonsMapper,
                           ProductService productService, RndAudit audit) {
        this.pipelineMapper = pipelineMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.audit = audit;
    }

    @Transactional
    public PipelineView create(PipelineCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long id = IdWorker.getId();
        pipelineMapper.insertPipeline(id, tenantId, request.productId(),
                RndContext.requireText(request.name(), "name"),
                request.ciType() == null ? "jenkins" : request.ciType(),
                request.triggerMode() == null ? "manual" : request.triggerMode(), request.repoId(), userId);
        audit.publish("PIPELINE_CREATED", "create", "devops_pipeline", id, "low");
        return get(id);
    }

    public PipelineView get(long id) {
        Map<String, Object> row = pipelineMapper.findPipeline(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<PipelineView> list(long productId) {
        return pipelineMapper.listPipelines(RndContext.tenantId(), productId).stream()
                .map(PipelineService::toView).toList();
    }

    @Transactional
    public RunView trigger(long pipelineId, String triggerType) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> pipeline = pipelineMapper.findPipeline(tenantId, pipelineId);
        if (pipeline == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"active".equals(Rows.str(pipeline, "status"))) {
            throw new BizException(RndErrorCode.PIPELINE_STATE_INVALID);
        }
        long id = IdWorker.getId();
        pipelineMapper.insertRun(id, tenantId, pipelineId, "RUN-" + commonsMapper.nextRunNo(),
                triggerType == null ? "manual" : triggerType, RndContext.userId());
        audit.publish("PIPELINE_TRIGGERED", "trigger", "devops_pipeline_run", id, "low");
        return getRun(id);
    }

    @Transactional
    public RunView finish(long runId, RunFinishRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        Map<String, Object> run = pipelineMapper.findRun(tenantId, runId);
        if (run == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"running".equals(Rows.str(run, "status"))) {
            throw new BizException(RndErrorCode.PIPELINE_STATE_INVALID);
        }
        String status = request == null || request.status() == null ? "success" : request.status();
        if (!List.of("success", "failed", "aborted").contains(status)) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        pipelineMapper.finishRun(tenantId, runId, status, userId);
        if (request != null && request.artifactName() != null) {
            long buildId = IdWorker.getId();
            pipelineMapper.insertBuild(buildId, tenantId, runId, "BUILD-" + commonsMapper.nextBuildNo(),
                    status, null, request.artifactName(), request.artifactVersion(), request.artifactUrl(),
                    request.qualityGate(), userId);
            pipelineMapper.finishBuild(tenantId, buildId, status, request.qualityGate(), userId);
        }
        audit.publish("PIPELINE_FINISHED", "finish", "devops_pipeline_run", runId,
                "failed".equals(status) ? "medium" : "low");
        return getRun(runId);
    }

    public RunView getRun(long id) {
        Map<String, Object> row = pipelineMapper.findRun(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toRunView(row);
    }

    public List<RunView> listRuns(long pipelineId, Integer limit) {
        return pipelineMapper.listRuns(RndContext.tenantId(), pipelineId, limit == null ? 20 : Math.min(limit, 100))
                .stream().map(PipelineService::toRunView).toList();
    }

    public static PipelineView toView(Map<String, Object> row) {
        return new PipelineView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "name"),
                Rows.str(row, "ciType"), Rows.str(row, "triggerMode"), Rows.str(row, "status"),
                Rows.str(row, "createdAt"));
    }

    public static RunView toRunView(Map<String, Object> row) {
        return new RunView(Rows.lng(row, "id"), Rows.lng(row, "pipelineId"), Rows.str(row, "runNo"),
                Rows.str(row, "status"), Rows.str(row, "triggerType"), Rows.str(row, "startedAt"),
                Rows.str(row, "finishedAt"), Rows.lng(row, "durationMs"), Rows.lng(row, "buildId"),
                Rows.str(row, "buildNo"), Rows.str(row, "artifactName"), Rows.str(row, "artifactVersion"),
                Rows.str(row, "qualityGate"));
    }
}
