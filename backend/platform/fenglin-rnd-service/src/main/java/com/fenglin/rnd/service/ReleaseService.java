package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.client.WorkflowClient;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.ReleaseMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.DeploymentView;
import com.fenglin.rnd.web.dto.RndDtos.ReleaseCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ReleaseView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * E1 发布：草稿 -> 审批 -> 发布 -> 回滚；生产发布走 BPMN 审批。
 */
@Service
@Transactional(readOnly = true)
public class ReleaseService {

    private final ReleaseMapper releaseMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final WorkflowClient workflowClient;
    private final RndAudit audit;

    public ReleaseService(ReleaseMapper releaseMapper, CommonsMapper commonsMapper,
                          ProductService productService, WorkflowClient workflowClient, RndAudit audit) {
        this.releaseMapper = releaseMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    @Transactional
    public ReleaseView create(ReleaseCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        String env = request.env() == null ? "staging" : request.env();
        if (!List.of("test", "staging", "prod").contains(env)) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        if (request.windowStart() != null && request.windowEnd() != null
                && request.windowEnd().compareTo(request.windowStart()) <= 0) {
            throw new BizException(RndErrorCode.RELEASE_WINDOW_INVALID);
        }
        long id = IdWorker.getId();
        releaseMapper.insertRelease(id, tenantId, request.productId(),
                "REL-" + commonsMapper.nextReleaseNo(),
                RndContext.requireText(request.releaseVersion(), "releaseVersion"), env, request.buildId(),
                RndContext.text(request.windowStart()), RndContext.text(request.windowEnd()), userId);
        audit.publish("RELEASE_CREATED", "create", "devops_release", id, "medium");
        return get(id);
    }

    public ReleaseView get(long id) {
        Map<String, Object> row = releaseMapper.findRelease(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<ReleaseView> list(long productId, String env, String status, Integer page, Integer size) {
        return releaseMapper.listReleases(RndContext.tenantId(), productId, RndContext.text(env),
                        RndContext.text(status), RndContext.size(size), RndContext.offset(page, size))
                .stream().map(ReleaseService::toView).toList();
    }

    @Transactional
    public ReleaseView submit(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> release = requireRelease(tenantId, id);
        if (!"draft".equals(Rows.str(release, "status"))) {
            throw new BizException(RndErrorCode.RELEASE_STATE_INVALID);
        }
        String instance = workflowClient.startApproval(null, null, "rndReleaseApproval",
                "release:" + id, "release", "发布审批：" + Rows.str(release, "releaseNo"),
                Map.of("releaseId", id, "env", Rows.str(release, "env"),
                        "version", Rows.str(release, "releaseVersion")));
        releaseMapper.updateStatus(tenantId, id, "pending_approval", instance, RndContext.userId());
        audit.publish("RELEASE_SUBMITTED", "submit", "devops_release", id, "high");
        return get(id);
    }

    @Transactional
    public ReleaseView approve(long id, boolean approved) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> release = requireRelease(tenantId, id);
        if (!"pending_approval".equals(Rows.str(release, "status"))) {
            throw new BizException(RndErrorCode.RELEASE_STATE_INVALID);
        }
        releaseMapper.updateStatus(tenantId, id, approved ? "approved" : "rejected", null, RndContext.userId());
        audit.publish(approved ? "RELEASE_APPROVED" : "RELEASE_REJECTED", "approve", "devops_release", id, "high");
        return get(id);
    }

    @Transactional
    public DeploymentView deploy(long id, String strategy) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> release = requireRelease(tenantId, id);
        if (!"approved".equals(Rows.str(release, "status"))) {
            throw new BizException(RndErrorCode.RELEASE_STATE_INVALID);
        }
        releaseMapper.updateStatus(tenantId, id, "deployed", null, RndContext.userId());
        long deploymentId = IdWorker.getId();
        releaseMapper.insertDeployment(deploymentId, tenantId, id, Rows.str(release, "env"), "success",
                strategy == null ? "rolling" : strategy, null, RndContext.userId());
        audit.publish("RELEASE_DEPLOYED", "deploy", "devops_release", id, "high");
        return listDeployments(id).stream().filter(d -> d.id() == deploymentId).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public DeploymentView rollback(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> release = requireRelease(tenantId, id);
        if (!"deployed".equals(Rows.str(release, "status"))) {
            throw new BizException(RndErrorCode.RELEASE_STATE_INVALID);
        }
        releaseMapper.updateStatus(tenantId, id, "rolled_back", null, RndContext.userId());
        long deploymentId = IdWorker.getId();
        releaseMapper.insertDeployment(deploymentId, tenantId, id, Rows.str(release, "env"), "rolled_back",
                "rolling", "手工回滚", RndContext.userId());
        audit.publish("RELEASE_ROLLED_BACK", "rollback", "devops_release", id, "high");
        return listDeployments(id).stream().filter(d -> d.id() == deploymentId).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<DeploymentView> listDeployments(long releaseId) {
        long tenantId = RndContext.tenantId();
        requireRelease(tenantId, releaseId);
        return releaseMapper.listDeployments(tenantId, releaseId).stream()
                .map(row -> new DeploymentView(Rows.lng(row, "id"), Rows.lng(row, "releaseId"),
                        Rows.str(row, "env"), Rows.str(row, "result"), Rows.str(row, "strategy"),
                        Rows.lng(row, "operatorId"), Rows.str(row, "startedAt"), Rows.str(row, "finishedAt")))
                .toList();
    }

    private Map<String, Object> requireRelease(long tenantId, long id) {
        Map<String, Object> release = releaseMapper.findRelease(tenantId, id);
        if (release == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return release;
    }

    public static ReleaseView toView(Map<String, Object> row) {
        return new ReleaseView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "releaseNo"),
                Rows.str(row, "releaseVersion"), Rows.str(row, "env"), Rows.lng(row, "buildId"),
                Rows.str(row, "status"), Rows.str(row, "approvalInstanceId"), Rows.str(row, "windowStart"),
                Rows.str(row, "windowEnd"), Rows.str(row, "deployedAt"), Rows.str(row, "createdAt"));
    }
}
