package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fenglin.common.core.BizException;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.mapper.OpsMapper;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程挖掘采集器：采集源清单、频率、字段映射全部走 svc_tenant_config（config_type = MINING_COLLECTOR），
 * 不硬编码任何产品假设。单个采集源以 config_key = code 存储。
 *
 * <p>链路：配置 -> HTTP 拉取 -> 按 list_path 取事件数组 -> 按 field_mapping 映射
 * -> 复用 {@link MiningService#importEvents} 入库。支持按 source 立即执行与按 due 批量执行。</p>
 */
@Service
@Transactional
public class MiningCollectorService {

    public static final String CONFIG_TYPE = "MINING_COLLECTOR";

    private final ConfigMapper configMapper;
    private final OpsMapper opsMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceAudit audit;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1500))
            .build();

    public MiningCollectorService(ConfigMapper configMapper, OpsMapper opsMapper,
                                  CommonsMapper commonsMapper, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.opsMapper = opsMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> configs = new ArrayList<>();
        for (Map<String, Object> row : configMapper.listConfigs(ServiceContext.tenantId(), CONFIG_TYPE)) {
            Map<String, Object> config = asMap(ServiceMaps.read(ServiceContext.string(row.get("config_json"))));
            config.putIfAbsent("code", row.get("config_key"));
            configs.add(config);
        }
        return configs;
    }

    /**
     * 保存或更新单个采集源配置。code 来自路径，写入 config_key；未知键原样保留。
     */
    public Map<String, Object> save(String code, Map<String, Object> body) {
        if (code == null || code.isBlank() || body == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "采集源 code 与配置体不能为空");
        }
        Map<String, Object> config = new LinkedHashMap<>(body);
        config.putIfAbsent("code", code);
        requireText(config, "url");
        requireText(config, "type");
        if (!"HTTP_JSON".equalsIgnoreCase(ServiceContext.string(config.get("type")))) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "当前仅支持 HTTP_JSON 采集源");
        }
        String url = ServiceContext.string(config.get("url"));
        validateUrl(url);
        Object mapping = config.get("field_mapping");
        if (!(mapping instanceof Map<?, ?> map)) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "field_mapping 必须为对象");
        }
        if (ServiceContext.string(map.get("case_id")) == null || ServiceContext.string(map.get("activity")) == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID,
                    "field_mapping 必须包含 case_id 与 activity");
        }
        long tenantId = ServiceContext.tenantId();
        configMapper.upsertConfig(IdWorker.getId(), tenantId, CONFIG_TYPE, code,
                ServiceMaps.json(config), "ACTIVE", ServiceContext.userId());
        audit.high("mining.collector.save", "tenant_config", CONFIG_TYPE + ":" + code);
        return ServiceMaps.of("code", code, "saved", true);
    }

    /**
     * 立即执行单个采集源；返回拉取/映射/入库统计，并回写 last_run_* 到配置。
     */
    public Map<String, Object> run(String code) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> row = find(tenantId, code);
        if (row == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND, "采集源不存在");
        }
        Map<String, Object> config = asMap(ServiceMaps.read(ServiceContext.string(row.get("config_json"))));
        Map<String, Object> result;
        try {
            List<Map<String, Object>> events = fetchAndMap(config);
            int inserted = ingest(events);
            result = ServiceMaps.of("code", code, "status", "SUCCESS",
                    "fetched", events.size(), "inserted", inserted,
                    "run_at", Instant.now().toString());
        } catch (Exception ex) {
            result = ServiceMaps.of("code", code, "status", "FAILED", "fetched", 0, "inserted", 0,
                    "error", ex.getMessage(), "run_at", Instant.now().toString());
        }
        persistRunState(tenantId, code, config, result);
        audit.low("mining.collector.run", "mining_collector", code);
        return result;
    }

    /**
     * 执行当前租户下所有“已启用且到期”的采集源。
     */
    public Map<String, Object> runDue() {
        List<Map<String, Object>> rows = configMapper.listConfigs(ServiceContext.tenantId(), CONFIG_TYPE);
        List<Map<String, Object>> results = new ArrayList<>();
        int ran = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> config = asMap(ServiceMaps.read(ServiceContext.string(row.get("config_json"))));
            if (!Boolean.TRUE.equals(config.get("enabled")) || !isDue(config)) {
                continue;
            }
            String code = ServiceContext.string(config.get("code"));
            if (code == null) {
                code = ServiceContext.string(row.get("config_key"));
            }
            results.add(run(code));
            ran++;
        }
        return ServiceMaps.of("due_sources", ran, "results", results);
    }

    private Map<String, Object> find(long tenantId, String code) {
        return configMapper.listConfigs(tenantId, CONFIG_TYPE).stream()
                .filter(row -> code.equals(ServiceContext.string(row.get("config_key"))))
                .findFirst().orElse(null);
    }

    private boolean isDue(Map<String, Object> config) {
        int interval = intVal(config.get("interval_minutes"), intVal(config.get("intervalMinutes"), 15));
        if (interval <= 0) {
            interval = 15;
        }
        String lastRun = ServiceContext.string(config.get("last_run_at"));
        if (lastRun == null) {
            return true;
        }
        try {
            Instant last = OffsetDateTime.parse(lastRun).toInstant();
            return !Instant.now().isBefore(last.plusSeconds(interval * 60L));
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private List<Map<String, Object>> fetchAndMap(Map<String, Object> config) throws Exception {
        String url = ServiceContext.string(config.get("url"));
        validateUrl(url);
        String method = ServiceContext.string(config.get("method"));
        method = method == null ? "GET" : method.toUpperCase();
        Duration timeout = Duration.ofMillis(intVal(config.get("timeout_ms"), 3000));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        Map<String, String> headers = asStringMap(config.get("headers"));
        headers.forEach(builder::header);
        if ("POST".equals(method)) {
            Object body = config.get("body");
            builder.header("Content-Type", headers.getOrDefault("Content-Type", "application/json"))
                    .POST(HttpRequest.BodyPublishers.ofString(ServiceMaps.json(body == null ? Map.of() : body)));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("采集源返回 HTTP " + response.statusCode());
        }
        JsonNode root = ServiceMaps.node(response.body());
        String listPath = ServiceContext.string(config.get("list_path"));
        JsonNode node = listPath == null || listPath.isBlank() ? root : at(root, listPath);
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("list_path 未指向数组");
        }
        Map<String, Object> mapping = asMap(config.get("field_mapping"));
        String defaultSource = ServiceContext.string(config.get("code"));
        List<Map<String, Object>> events = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("case_id", value(item, mapping.get("case_id")));
            event.put("activity", value(item, mapping.get("activity")));
            putIfPresent(event, "activity_code", value(item, mapping.get("activity_code")));
            putIfPresent(event, "resource", value(item, mapping.get("resource")));
            putIfPresent(event, "lifecycle", value(item, mapping.get("lifecycle")));
            Object eventTime = value(item, mapping.get("event_time"));
            if (eventTime != null) {
                event.put("event_time", eventTime);
            }
            String sourceSystem = ServiceContext.string(value(item, mapping.get("source_system")));
            event.put("source_system", sourceSystem == null ? defaultSource : sourceSystem);
            Object attributes = value(item, mapping.get("attributes"));
            event.put("attributes", attributes instanceof Map<?, ?> ? attributes : Map.of());
            if (ServiceContext.string(event.get("case_id")) == null
                    || ServiceContext.string(event.get("activity")) == null) {
                continue;
            }
            events.add(event);
        }
        return events;
    }

    private int ingest(List<Map<String, Object>> events) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        int inserted = 0;
        for (Map<String, Object> event : events) {
            String caseId = ServiceContext.string(event.get("case_id"));
            String activity = ServiceContext.string(event.get("activity"));
            if (caseId == null || activity == null) {
                continue;
            }
            opsMapper.insertEvent(IdWorker.getId(), tenantId, caseId, activity,
                    ServiceContext.string(event.get("activity_code")),
                    timestamp(event.get("event_time")),
                    ServiceContext.string(event.get("resource")),
                    event.get("lifecycle") == null ? "COMPLETE" : ServiceContext.string(event.get("lifecycle")),
                    ServiceContext.string(event.get("source_system")),
                    ServiceMaps.json(event.get("attributes")), userId);
            inserted++;
        }
        return inserted;
    }

    private Timestamp timestamp(Object value) {
        if (value == null) {
            return commonsMapper.now();
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return new Timestamp(raw < 1_000_000_000_000L ? raw * 1000L : raw);
        }
        String text = String.valueOf(value).trim();
        try {
            return Timestamp.from(OffsetDateTime.parse(text).toInstant());
        } catch (RuntimeException ignored) {
            // 继续尝试本地时间
        }
        try {
            return Timestamp.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException ignored) {
            return commonsMapper.now();
        }
    }

    private void persistRunState(long tenantId, String code, Map<String, Object> config,
                                 Map<String, Object> result) {
        config.put("last_run_at", result.get("run_at"));
        config.put("last_status", result.get("status"));
        config.put("last_inserted", result.get("inserted"));
        Object error = result.get("error");
        if (error == null) {
            config.remove("last_error");
        } else {
            config.put("last_error", error);
        }
        configMapper.upsertConfig(IdWorker.getId(), tenantId, CONFIG_TYPE, code,
                ServiceMaps.json(config), "ACTIVE", ServiceContext.userId());
    }

    private JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private Object value(JsonNode item, Object path) {
        String text = ServiceContext.string(path);
        if (text == null || text.isBlank()) {
            return null;
        }
        JsonNode node = at(item, text);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isObject() || node.isArray()) {
            return ServiceMaps.read(node.toString());
        }
        return node.asText();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, String> asStringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        }
        return result;
    }

    private int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void requireText(Map<String, Object> config, String key) {
        if (ServiceContext.string(config.get(key)) == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, key + " 不能为空");
        }
    }

    private void validateUrl(String url) {
        if (url == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "url 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException ex) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "url 不是合法 URI");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "url 仅支持 http/https");
        }
        if (uri.getHost() == null) {
            throw new BizException(ServiceErrorCode.PARAM_INVALID, "url 缺少主机名");
        }
    }
}
