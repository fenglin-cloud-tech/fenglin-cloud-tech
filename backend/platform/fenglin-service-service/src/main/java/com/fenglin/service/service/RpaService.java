package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.OpsMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceCrypto;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPA：机器人、凭据、自动化编排、调度计划与任务生命周期。
 *
 * <p>凭据以 AES-256-GCM 落库；明文仅通过短期临时令牌换取，不提供批量导出。</p>
 */
@Service
@Transactional
public class RpaService {

    private static final Set<String> ACTIVE_STATUS = Set.of("QUEUED", "RUNNING", "PAUSED");
    private static final Set<String> RETRYABLE = Set.of("FAILED", "MANUAL_REQUIRED");

    private final OpsMapper opsMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceCrypto crypto;
    private final ServiceAudit audit;
    private final Map<String, Object[]> tokens = new ConcurrentHashMap<>();

    public RpaService(OpsMapper opsMapper, CommonsMapper commonsMapper, ServiceCrypto crypto, ServiceAudit audit) {
        this.opsMapper = opsMapper;
        this.commonsMapper = commonsMapper;
        this.crypto = crypto;
        this.audit = audit;
    }

    // ---------- 机器人 ----------
    public Map<String, Object> registerRobot(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long id = IdWorker.getId();
        opsMapper.insertRobot(id, tenantId,
                ServiceContext.requireText(ServiceContext.string(body.get("robot_code"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("robot_type") == null ? "DESKTOP" : ServiceContext.string(body.get("robot_type")),
                ServiceContext.string(body.get("host")),
                ServiceMaps.json(body.get("capabilities")),
                body.get("max_concurrency") == null ? 1
                        : (int) ServiceContext.requireLong(body.get("max_concurrency")),
                body.get("status") == null ? "OFFLINE" : ServiceContext.string(body.get("status")).toUpperCase(),
                ServiceContext.string(body.get("agent_version")), ServiceContext.userId());
        audit.low("robot.register", "robot", id);
        return robot(tenantId, id);
    }

    public PageResult<Map<String, Object>> listRobots(Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = ServiceMaps.parseAll(
                opsMapper.listRobots(tenantId, limit, offset), "capabilities");
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    public Map<String, Object> govern(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long robotId = ServiceContext.requireLong(body.get("robot_id"));
        String status = ServiceContext.requireText(ServiceContext.string(body.get("status"))).toUpperCase();
        if (opsMapper.updateRobotStatus(tenantId, robotId, status, ServiceContext.userId()) == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        audit.high("robot.govern", "robot", robotId);
        return robot(tenantId, robotId);
    }

    // ---------- 凭据 ----------
    public Map<String, Object> saveCredential(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long id = IdWorker.getId();
        String secret = ServiceContext.requireText(ServiceContext.string(body.get("secret")));
        opsMapper.insertCredential(id, tenantId,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("cred_type") == null ? "PASSWORD" : ServiceContext.string(body.get("cred_type")),
                ServiceContext.string(body.get("target_system")),
                crypto.encrypt(tenantId, "svc_robot_credential", "secret", secret),
                body.get("kms_key_id") == null ? "LOCAL" : ServiceContext.string(body.get("kms_key_id")),
                body.get("kms_version") == null ? "v1" : ServiceContext.string(body.get("kms_version")),
                body.get("rotate_due_at") == null ? null : timestamp(body.get("rotate_due_at")),
                ServiceContext.userId());
        audit.high("credential.save", "robot_credential", id);
        return ServiceMaps.of("id", id, "name", body.get("name"), "target_system", body.get("target_system"),
                "status", "ACTIVE", "secret_masked", "******");
    }

    public List<Map<String, Object>> listCredentials() {
        return opsMapper.listCredentials(ServiceContext.tenantId());
    }

    /** 短期令牌换取：返回令牌与元数据，不返回明文；令牌 5 分钟有效，仅限内存。 */
    public Map<String, Object> credentialToken(long credentialId) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> credential = opsMapper.findCredentialSecret(tenantId, credentialId);
        if (credential == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        tokens.entrySet().removeIf(entry -> (long) entry.getValue()[1] < System.currentTimeMillis());
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + 300_000L;
        tokens.put(token, new Object[]{credentialId, expiresAt});
        audit.high("credential.token.issue", "robot_credential", credentialId);
        return ServiceMaps.of("token", token, "expires_at", Instant.ofEpochMilli(expiresAt),
                "credential_id", credentialId, "target_system", credential.get("target_system"),
                "kms_key_id", credential.get("kms_key_id"));
    }

    /** 内部消费令牌（不对外暴露明文，仅返回是否有效与目标系统）。 */
    public Map<String, Object> redeemToken(String token) {
        Object[] payload = token == null ? null : tokens.get(token);
        if (payload == null || (long) payload[1] < System.currentTimeMillis()) {
            throw new BizException(ServiceErrorCode.CREDENTIAL_FORBIDDEN);
        }
        return ServiceMaps.of("credential_id", payload[0], "valid", true);
    }

    // ---------- 自动化编排 ----------
    public Map<String, Object> saveAutomation(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long id = IdWorker.getId();
        Object nodes = body.get("nodes");
        if (nodes == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        opsMapper.insertAutomation(id, tenantId,
                body.get("app_id") == null ? null : ServiceContext.requireLong(body.get("app_id")),
                ServiceContext.requireText(ServiceContext.string(body.get("flow_code"))),
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("version_no") == null ? 1 : (int) ServiceContext.requireLong(body.get("version_no")),
                ServiceMaps.json(nodes), ServiceMaps.json(body.get("params_schema")),
                ServiceMaps.json(body.get("target_whitelist")),
                body.get("timeout_sec") == null ? 300
                        : (int) ServiceContext.requireLong(body.get("timeout_sec")),
                ServiceMaps.json(body.get("retry_policy")),
                body.get("source_candidate_id") == null ? null
                        : ServiceContext.requireLong(body.get("source_candidate_id")),
                body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")).toUpperCase(),
                ServiceContext.userId());
        audit.low("automation.save", "automation_flow", id);
        return automation(tenantId, id);
    }

    public Map<String, Object> automation(long tenantId, long id) {
        Map<String, Object> row = opsMapper.findAutomation(tenantId, id);
        if (row == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(row, "nodes_json", "params_schema", "target_whitelist", "retry_policy");
    }

    public PageResult<Map<String, Object>> listAutomations(String status, Integer page, Integer size) {
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = opsMapper.listAutomations(ServiceContext.tenantId(),
                ServiceContext.text(status), limit, offset);
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    @Transactional
    public Map<String, Object> publish(long id) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> flow = automation(tenantId, id);
        Object nodes = flow.get("nodes_json");
        if (nodes == null || "{}".equals(String.valueOf(nodes))) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        opsMapper.updateAutomationStatus(tenantId, id, "PUBLISHED", ServiceContext.userId());
        audit.high("automation.publish", "automation_flow", id);
        return automation(tenantId, id);
    }

    public Map<String, Object> updateWhitelist(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        if (opsMapper.updateWhitelist(tenantId, id, ServiceMaps.json(body.get("target_whitelist")),
                ServiceContext.userId()) == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return automation(tenantId, id);
    }

    // ---------- 调度 ----------
    public Map<String, Object> createSchedule(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long flowId = ServiceContext.requireLong(body.get("automation_flow_id"));
        Map<String, Object> flow = automation(tenantId, flowId);
        if (!"PUBLISHED".equals(Rows.str(flow, "status"))) {
            throw new BizException(ServiceErrorCode.AUTOMATION_NOT_PUBLISHED);
        }
        long id = IdWorker.getId();
        opsMapper.insertSchedule(id, tenantId,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))), flowId,
                body.get("trigger_type") == null ? "CRON"
                        : ServiceContext.requireText(ServiceContext.string(body.get("trigger_type"))).toUpperCase(),
                ServiceContext.string(body.get("cron_expr")), ServiceContext.string(body.get("event_topic")),
                ServiceMaps.json(body.get("robot_selector")),
                body.get("concurrency_limit") == null ? 1
                        : (int) ServiceContext.requireLong(body.get("concurrency_limit")),
                body.get("timeout_sec") == null ? 300
                        : (int) ServiceContext.requireLong(body.get("timeout_sec")),
                body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled")),
                ServiceContext.userId());
        audit.low("schedule.create", "robot_schedule", id);
        return ServiceMaps.of("id", id, "automation_flow_id", flowId);
    }

    public List<Map<String, Object>> listSchedules() {
        return ServiceMaps.parseAll(opsMapper.listSchedules(ServiceContext.tenantId()), "robot_selector");
    }

    // ---------- 任务 ----------
    @Transactional
    public Map<String, Object> triggerTask(Map<String, Object> body, String idemKey) {
        long tenantId = ServiceContext.tenantId();
        if (idemKey != null) {
            Long existing = opsMapper.findTaskByIdempotency(tenantId, idemKey);
            if (existing != null) {
                return task(existing);
            }
        }
        long flowId = ServiceContext.requireLong(body.get("automation_flow_id"));
        Map<String, Object> flow = automation(tenantId, flowId);
        if (!"PUBLISHED".equals(Rows.str(flow, "status"))) {
            throw new BizException(ServiceErrorCode.AUTOMATION_NOT_PUBLISHED);
        }
        Long robotId = body.get("robot_id") == null ? null : ServiceContext.requireLong(body.get("robot_id"));
        if (robotId != null) {
            Map<String, Object> robot = robot(tenantId, robotId);
            if ("OFFLINE".equals(Rows.str(robot, "status"))) {
                throw new BizException(ServiceErrorCode.ROBOT_OFFLINE);
            }
        }
        long id = IdWorker.getId();
        String taskNo = "RPA" + String.format("%010d", commonsMapper.nextRobotTaskNo());
        opsMapper.insertTask(id, tenantId, taskNo, flowId, robotId,
                body.get("trigger_type") == null ? "MANUAL" : ServiceContext.string(body.get("trigger_type")),
                body.get("priority") == null ? 5 : (int) ServiceContext.requireLong(body.get("priority")),
                ServiceMaps.json(body.get("input")), "QUEUED",
                body.get("max_retry") == null ? 3 : (int) ServiceContext.requireLong(body.get("max_retry")),
                ServiceContext.string(body.get("business_key")), idemKey, ServiceContext.userId());
        audit.low("task.trigger", "robot_task", id);
        return task(id);
    }

    @Transactional
    public Map<String, Object> batchDispatch(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        List<Object> taskIds = new ArrayList<>();
        for (Object each : list) {
            if (each instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((key, value) -> item.put(String.valueOf(key), value));
                taskIds.add(triggerTask(item, null).get("id"));
            }
        }
        return ServiceMaps.of("created", taskIds.size(), "task_ids", taskIds);
    }

    public PageResult<Map<String, Object>> listTasks(String status, Long robotId, Integer page, Integer size) {
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = opsMapper.listTasks(ServiceContext.tenantId(),
                ServiceContext.text(status), robotId, limit, offset);
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    public Map<String, Object> task(long id) {
        Map<String, Object> row = opsMapper.findTask(ServiceContext.tenantId(), id);
        if (row == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(row, "input_json", "output_json");
    }

    public List<Map<String, Object>> taskLogs(long id) {
        task(id);
        return opsMapper.listTaskLogs(ServiceContext.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> taskAction(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> task = task(id);
        String current = Rows.str(task, "status");
        String action = ServiceContext.requireText(ServiceContext.string(body.get("action"))).toUpperCase();
        String target;
        switch (action) {
            case "CANCEL" -> {
                if (!ACTIVE_STATUS.contains(current)) {
                    throw new BizException(ServiceErrorCode.TASK_ACTION_INVALID);
                }
                target = "CANCELLED";
            }
            case "PAUSE" -> {
                if (!"RUNNING".equals(current)) {
                    throw new BizException(ServiceErrorCode.TASK_ACTION_INVALID);
                }
                target = "PAUSED";
            }
            case "RESUME" -> {
                if (!"PAUSED".equals(current)) {
                    throw new BizException(ServiceErrorCode.TASK_ACTION_INVALID);
                }
                target = "QUEUED";
            }
            default -> throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        opsMapper.updateTaskStatus(tenantId, id, target, null, null, null, ServiceContext.userId());
        audit.low("task." + action.toLowerCase(), "robot_task", id);
        return task(id);
    }

    @Transactional
    public Map<String, Object> callback(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> task = task(id);
        String status = ServiceContext.requireText(ServiceContext.string(body.get("status"))).toUpperCase();
        if (!Set.of("RUNNING", "SUCCEEDED", "FAILED", "MANUAL_REQUIRED").contains(status)) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        if ("RUNNING".equals(status)) {
            opsMapper.updateTaskStatus(tenantId, id, status, null, null, null, ServiceContext.userId());
        } else {
            opsMapper.updateTaskResult(tenantId, id, status, ServiceMaps.json(body.get("output")),
                    ServiceContext.string(body.get("error_code")), ServiceContext.string(body.get("error_msg")),
                    ServiceContext.userId());
        }
        Object logs = body.get("logs");
        int attempt = Rows.intVal(task, "retry_count") + 1;
        if (logs instanceof List<?> list) {
            for (Object each : list) {
                if (each instanceof Map<?, ?> map) {
                    opsMapper.insertTaskLog(IdWorker.getId(), tenantId, id, attempt,
                            map.get("log_level") == null ? "INFO" : String.valueOf(map.get("log_level")),
                            map.get("step") == null ? null : String.valueOf(map.get("step")),
                            map.get("message") == null ? null : String.valueOf(map.get("message")),
                            map.get("screenshot_file_id") == null ? null
                                    : String.valueOf(map.get("screenshot_file_id")),
                            ServiceContext.userId());
                }
            }
        }
        return task(id);
    }

    @Transactional
    public Map<String, Object> retry(long id) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> task = task(id);
        if (!RETRYABLE.contains(Rows.str(task, "status"))
                || Rows.intVal(task, "retry_count") >= Rows.intVal(task, "max_retry")) {
            throw new BizException(ServiceErrorCode.TASK_ACTION_INVALID);
        }
        opsMapper.retryTask(tenantId, id, "QUEUED", commonsMapper.now(), ServiceContext.userId());
        audit.low("task.retry", "robot_task", id);
        return task(id);
    }

    public Map<String, Object> summary() {
        return opsMapper.rpaSummary(ServiceContext.tenantId());
    }

    public List<Map<String, Object>> utilization() {
        return opsMapper.robotUtilization(ServiceContext.tenantId());
    }

    private Map<String, Object> robot(long tenantId, long id) {
        Map<String, Object> robot = opsMapper.findRobot(tenantId, id);
        if (robot == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return robot;
    }

    private Timestamp timestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        try {
            return Timestamp.from(Instant.parse(String.valueOf(value)));
        } catch (RuntimeException ex) {
            return Timestamp.valueOf(String.valueOf(value).replace("T", " ").substring(0, 19));
        }
    }
}
