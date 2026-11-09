package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.OpsMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * 流程挖掘：事件接入、模型发现、一致性/瓶颈/资源负荷分析与自动化候选。
 *
 * <p>算法采用基于事件日志的直接跟随图（DFG），不引入第三方挖掘引擎；结果落 svc_mining_result。</p>
 */
@Service
@Transactional
public class MiningService {

    private static final int MIN_EVENTS = 2;

    private final OpsMapper opsMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceAudit audit;

    public MiningService(OpsMapper opsMapper, CommonsMapper commonsMapper, ServiceAudit audit) {
        this.opsMapper = opsMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> importEvents(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("events");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID);
        }
        int inserted = 0;
        for (Object each : list) {
            if (each instanceof Map<?, ?> map) {
                ingest(map);
                inserted++;
            }
        }
        audit.low("mining.ingest", "mining_event", inserted);
        return ServiceMaps.of("inserted", inserted);
    }

    private void ingest(Map<?, ?> raw) {
        Map<String, Object> event = new LinkedHashMap<>();
        raw.forEach((key, value) -> event.put(String.valueOf(key), value));
        String caseId = ServiceContext.requireText(ServiceContext.string(event.get("case_id")));
        String activity = ServiceContext.requireText(ServiceContext.string(event.get("activity")));
        opsMapper.insertEvent(IdWorker.getId(), ServiceContext.tenantId(), caseId, activity,
                ServiceContext.string(event.get("activity_code")),
                event.get("event_time") == null ? commonsMapper.now() : timestamp(event.get("event_time")),
                ServiceContext.string(event.get("resource")),
                event.get("lifecycle") == null ? "COMPLETE" : ServiceContext.string(event.get("lifecycle")),
                ServiceContext.string(event.get("source_system")),
                ServiceMaps.json(event.get("attributes")), ServiceContext.userId());
    }

    @Transactional
    public Map<String, Object> discover(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Timestamp to = body != null && body.get("period_end") != null ? timestamp(body.get("period_end"))
                : commonsMapper.now();
        Timestamp from = body != null && body.get("period_start") != null ? timestamp(body.get("period_start"))
                : Timestamp.from(to.toInstant().minus(30, ChronoUnit.DAYS));
        String sourceSystem = body == null ? null : ServiceContext.string(body.get("source_system"));
        List<Map<String, Object>> events = opsMapper.listEvents(tenantId, from, to, sourceSystem);
        Map<String, List<Map<String, Object>>> cases = groupCases(events);
        if (events.size() < MIN_EVENTS || cases.isEmpty()) {
            throw new BizException(ServiceErrorCode.MINING_DATA_INSUFFICIENT);
        }
        long resultId = IdWorker.getId();
        Map<String, Integer> activityCount = new LinkedHashMap<>();
        Map<String, List<Long>> transitions = new LinkedHashMap<>();
        Map<String, Integer> variants = new LinkedHashMap<>();
        Map<String, Long> caseDurations = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : cases.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            List<String> sequence = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                String key = activityKey(row);
                sequence.add(key);
                activityCount.merge(key, 1, Integer::sum);
                if (i > 0) {
                    long seconds = durationSeconds(rows.get(i - 1), row);
                    transitions.computeIfAbsent(activityKey(rows.get(i - 1)) + ">" + key,
                            each -> new ArrayList<>()).add(seconds);
                }
            }
            variants.merge(String.join(" > ", sequence), 1, Integer::sum);
            caseDurations.put(entry.getKey(), caseDuration(rows));
        }
        ObjectNode model = JsonNodeFactory.instance.objectNode();
        ArrayNode nodes = model.putArray("nodes");
        activityCount.forEach((key, count) -> nodes.addObject().put("key", key).put("name", key).put("count", count));
        ArrayNode edges = model.putArray("edges");
        for (Map.Entry<String, List<Long>> entry : transitions.entrySet()) {
            String[] pair = entry.getKey().split(">", 2);
            ObjectNode edge = edges.addObject();
            edge.put("source", pair[0]).put("target", pair[1]).put("count", entry.getValue().size());
            edge.put("avg_duration_sec", average(entry.getValue()));
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        long candidateId = (resultId / 1000L) * 100L;
        for (Map.Entry<String, List<Long>> entry : transitions.entrySet()) {
            String[] pair = entry.getKey().split(">", 2);
            long avg = average(entry.getValue());
            boolean automatable = entry.getValue().size() >= 2 && avg >= 60;
            if (!automatable) {
                continue;
            }
            candidates.add(ServiceMaps.of("id", candidateId++, "from_activity", pair[0], "to_activity", pair[1],
                    "frequency", entry.getValue().size(), "avg_duration_sec", avg,
                    "suggestion", "自动执行 " + pair[1] + " 并回写 " + pair[0] + " 结果"));
            if (candidates.size() >= 5) {
                break;
            }
        }
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("case_count", cases.size());
        metrics.put("event_count", events.size());
        metrics.put("activity_count", activityCount.size());
        metrics.put("variant_count", variants.size());
        metrics.put("avg_case_duration_sec", average(new ArrayList<>(caseDurations.values())));
        metrics.put("candidate_count", candidates.size());
        String name = body != null && body.get("name") != null ? ServiceContext.string(body.get("name"))
                : "流程模型-" + to.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        opsMapper.insertResult(resultId, tenantId,
                body == null || body.get("result_type") == null ? "PROCESS_MODEL"
                        : ServiceContext.string(body.get("result_type")).toUpperCase(),
                name, sourceSystem, from, to, ServiceMaps.json(model), ServiceMaps.json(metrics),
                ServiceMaps.json(candidates), ServiceContext.userId());
        audit.low("mining.discover", "mining_result", resultId);
        return result(resultId);
    }

    public Map<String, Object> result(long id) {
        Map<String, Object> row = opsMapper.findResult(ServiceContext.tenantId(), id);
        if (row == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ServiceMaps.parse(row, "model_json", "metrics_json", "candidate_json");
    }

    public PageResult<Map<String, Object>> listResults(String resultType, Integer page, Integer size) {
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = opsMapper.listResults(ServiceContext.tenantId(),
                ServiceContext.text(resultType), limit, offset);
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    public Map<String, Object> conformance(long id) {
        Map<String, Object> model = result(id);
        List<Map<String, Object>> events = periodEvents(model);
        Set<String> actual = new LinkedHashSet<>();
        events.forEach(event -> actual.add(activityKey(event)));
        JsonNode nodes = ServiceMaps.node(model.get("model_json")).path("nodes");
        Set<String> expected = new LinkedHashSet<>();
        nodes.forEach(node -> expected.add(node.path("key").asText()));
        Set<String> matched = new LinkedHashSet<>(expected);
        matched.retainAll(actual);
        List<String> skipped = new ArrayList<>(expected);
        skipped.removeAll(actual);
        List<String> unexpected = new ArrayList<>(actual);
        unexpected.removeAll(expected);
        double fitness = expected.isEmpty() ? 1.0 : (double) matched.size() / expected.size();
        return ServiceMaps.of("model_id", id, "fitness", Math.round(fitness * 10000) / 10000.0,
                "expected_count", expected.size(), "observed_count", actual.size(),
                "skipped_activities", skipped, "unexpected_activities", unexpected);
    }

    public Map<String, Object> bottleneck(long id) {
        Map<String, Object> model = result(id);
        JsonNode edges = ServiceMaps.node(model.get("model_json")).path("edges");
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        edges.forEach(edge -> bottlenecks.add(ServiceMaps.of(
                "source", edge.path("source").asText(), "target", edge.path("target").asText(),
                "count", edge.path("count").asInt(), "avg_duration_sec", edge.path("avg_duration_sec").asLong())));
        bottlenecks.sort((left, right) -> Long.compare(
                ((Number) right.get("avg_duration_sec")).longValue(),
                ((Number) left.get("avg_duration_sec")).longValue()));
        return ServiceMaps.of("model_id", id, "bottlenecks", bottlenecks.stream().limit(5).toList());
    }

    public Map<String, Object> resourceLoad(long id) {
        Map<String, Object> model = result(id);
        Map<String, List<Long>> busy = new LinkedHashMap<>();
        Map<String, Integer> handled = new LinkedHashMap<>();
        for (List<Map<String, Object>> rows : groupCases(periodEvents(model)).values()) {
            for (int i = 0; i < rows.size(); i++) {
                String resource = Rows.str(rows.get(i), "resource");
                if (resource == null) {
                    continue;
                }
                handled.merge(resource, 1, Integer::sum);
                if (i > 0) {
                    busy.computeIfAbsent(resource, each -> new ArrayList<>())
                            .add(durationSeconds(rows.get(i - 1), rows.get(i)));
                }
            }
        }
        List<Map<String, Object>> load = new ArrayList<>();
        handled.forEach((resource, count) -> load.add(ServiceMaps.of("resource", resource, "handled", count,
                "avg_handling_sec", average(busy.getOrDefault(resource, List.of())))));
        load.sort((left, right) -> Integer.compare((Integer) right.get("handled"), (Integer) left.get("handled")));
        return ServiceMaps.of("model_id", id, "resources", load);
    }

    public List<Map<String, Object>> candidates(long id) {
        Map<String, Object> model = result(id);
        JsonNode candidates = ServiceMaps.node(model.get("candidate_json"));
        List<Map<String, Object>> rows = new ArrayList<>();
        candidates.forEach(node -> rows.add(ServiceMaps.of("id", node.path("id").asLong(),
                "from_activity", node.path("from_activity").asText(), "to_activity", node.path("to_activity").asText(),
                "frequency", node.path("frequency").asInt(), "avg_duration_sec", node.path("avg_duration_sec").asLong(),
                "suggestion", node.path("suggestion").asText())));
        return rows;
    }

    /** 候选转自动化编排草稿（RPA 编排由 RpaService 发布后执行）。 */
    @Transactional
    public Map<String, Object> toAutomation(long candidateId, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> row = opsMapper.findResultByCandidate(tenantId, candidateId);
        if (row == null) {
            throw new BizException(ServiceErrorCode.CANDIDATE_NOT_FOUND);
        }
        JsonNode candidates = ServiceMaps.node(Rows.str(row, "candidate_json"));
        JsonNode candidate = null;
        for (JsonNode each : candidates) {
            if (each.path("id").asLong() == candidateId) {
                candidate = each;
                break;
            }
        }
        if (candidate == null) {
            throw new BizException(ServiceErrorCode.CANDIDATE_NOT_FOUND);
        }
        String from = candidate.path("from_activity").asText();
        String to = candidate.path("to_activity").asText();
        long id = IdWorker.getId();
        String flowCode = "AUTO-" + candidateId;
        ObjectNode nodes = JsonNodeFactory.instance.objectNode();
        ArrayNode array = nodes.putArray("nodes");
        array.addObject().put("key", from).put("action", "READ");
        array.addObject().put("key", to).put("action", "AUTOMATE");
        opsMapper.insertAutomation(id, tenantId,
                body != null && body.get("app_id") != null ? ServiceContext.requireLong(body.get("app_id")) : null,
                flowCode, "挖掘候选-" + from + "→" + to, 1, ServiceMaps.json(nodes),
                ServiceMaps.json(Map.of("business_key", "string")), ServiceMaps.json(List.of()), 300,
                ServiceMaps.json(Map.of("max_retry", 3, "backoff_sec", 60)), candidateId, "DRAFT",
                ServiceContext.userId());
        audit.high("mining.to.automation", "automation_flow", id);
        return ServiceMaps.parse(opsMapper.findAutomation(tenantId, id),
                "nodes_json", "params_schema", "target_whitelist", "retry_policy");
    }

    public Map<String, Object> archive(long id) {
        if (opsMapper.archiveResult(ServiceContext.tenantId(), id, ServiceContext.userId()) == 0) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return result(id);
    }

    private List<Map<String, Object>> periodEvents(Map<String, Object> model) {
        Timestamp from = (Timestamp) model.get("period_start");
        Timestamp to = (Timestamp) model.get("period_end");
        return opsMapper.listEvents(ServiceContext.tenantId(), from, to, Rows.str(model, "source_system"));
    }

    private Map<String, List<Map<String, Object>>> groupCases(List<Map<String, Object>> events) {
        Map<String, List<Map<String, Object>>> cases = new LinkedHashMap<>();
        for (Map<String, Object> event : events) {
            cases.computeIfAbsent(Rows.str(event, "case_id"), each -> new ArrayList<>()).add(event);
        }
        return cases;
    }

    private String activityKey(Map<String, Object> row) {
        String code = Rows.str(row, "activity_code");
        return code == null || code.isBlank() ? Rows.str(row, "activity") : code;
    }

    private long durationSeconds(Map<String, Object> before, Map<String, Object> after) {
        Timestamp start = (Timestamp) before.get("event_time");
        Timestamp end = (Timestamp) after.get("event_time");
        return start == null || end == null ? 0 : Math.max(0, (end.getTime() - start.getTime()) / 1000);
    }

    private long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (Long value : values) {
            total += value == null ? 0 : value;
        }
        return total / values.size();
    }

    private long caseDuration(List<Map<String, Object>> rows) {
        Timestamp first = (Timestamp) rows.get(0).get("event_time");
        Timestamp last = (Timestamp) rows.get(rows.size() - 1).get("event_time");
        return first == null || last == null ? 0 : Math.max(0, (last.getTime() - first.getTime()) / 1000);
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
