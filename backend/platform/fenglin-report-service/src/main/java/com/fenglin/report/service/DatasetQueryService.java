package com.fenglin.report.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.mapper.DatasetMapper;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryResult;
import com.fenglin.report.web.dto.ReportDtos.DatasetView;
import com.fenglin.report.web.dto.ReportDtos.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HUB-API-1103 数据集查询。
 * 数据集由管理员预置只读 SQL 模板 + 字段白名单；本服务只允许白名单内的维度/度量/过滤列，
 * 过滤值全部走 PreparedStatement 绑定，避免 SQL 注入。
 */
@Service
public class DatasetQueryService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> AGGREGATES = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private static final Set<String> OPS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IN");

    private final DatasetMapper datasetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final int maxRows;

    public DatasetQueryService(DatasetMapper datasetMapper,
                               JdbcTemplate jdbcTemplate,
                               @Value("${fenglin.report.export.max-rows:5000}") int maxRows) {
        this.datasetMapper = datasetMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.maxRows = maxRows;
    }

    /** 扩展：数据集列表（总纲未定义，按管理端展示最小语义实现）。 */
    @Transactional(readOnly = true)
    public PageResult<DatasetView> listDatasets(String status, long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String filter = status == null || status.isBlank() ? null : status.trim();
        List<DatasetView> records = datasetMapper.list(tenantId, filter,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new DatasetView(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("description"),
                        ((Number) row.get("field_count")).longValue(),
                        (String) row.get("status")))
                .toList();
        return PageResult.of(records, datasetMapper.count(tenantId, filter), safePage, safeSize);
    }

    /**
     * 每个数据集查询使用独立事务：看板/导出按图表降级时，单个失败不会把外层事务标记为 rollback-only。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> dataset = datasetMapper.findById(tenantId, datasetId);
        if (dataset == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "数据集不存在: " + datasetId);
        }
        if (!"ENABLED".equals(dataset.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "数据集已停用: " + datasetId);
        }
        String template = (String) dataset.get("sql_template");
        validateTemplate(template);

        List<Field> fields = datasetMapper.findFields(tenantId, datasetId).stream()
                .map(row -> new Field((String) row.get("field_name"), (String) row.get("column_name"),
                        (String) row.get("field_role"), (String) row.get("aggregate")))
                .toList();
        if (fields.isEmpty()) {
            throw new BizException(CommonErrorCode.CONFLICT, "数据集未配置字段白名单: " + datasetId);
        }

        List<Field> dimensions = resolve(fields, request.dimensions(), "DIMENSION");
        List<Field> measures = resolve(fields, request.measures(), "MEASURE");
        if (dimensions.isEmpty() && measures.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "dimensions 与 measures 不能同时为空");
        }
        List<Object> args = new ArrayList<>();
        String sql = buildSql(template, dimensions, measures, request.filters(), fields, args);
        sql = sql + " LIMIT " + limit(request.limit());

        // RLS：报表数据集可能查询平台租户表，事务内显式设置租户上下文
        jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        return jdbcTemplate.query(sql, args.toArray(), rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) {
                    row.add(normalize(rs.getObject(i)));
                }
                rows.add(row);
            }
            return new DatasetQueryResult(columns, rows);
        });
    }

    private List<Field> resolve(List<Field> fields, List<String> requested, String role) {
        if (requested == null || requested.isEmpty()) {
            return fields.stream().filter(field -> role.equals(field.role())).toList();
        }
        List<Field> resolved = new ArrayList<>();
        for (String name : requested) {
            Field field = fields.stream()
                    .filter(candidate -> candidate.name().equals(name) && role.equals(candidate.role()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(CommonErrorCode.BAD_REQUEST,
                            "字段不在白名单或角色不符: " + name));
            if (!resolved.contains(field)) {
                resolved.add(field);
            }
        }
        return resolved;
    }

    private String buildSql(String template, List<Field> dimensions, List<Field> measures,
                            List<Filter> filters, List<Field> fields, List<Object> args) {
        List<String> selects = new ArrayList<>();
        for (Field field : dimensions) {
            selects.add("t." + quote(field.column()) + " AS " + quote(field.name()));
        }
        for (Field field : measures) {
            String aggregate = field.aggregate() == null ? "SUM" : field.aggregate().toUpperCase(Locale.ROOT);
            if (!AGGREGATES.contains(aggregate)) {
                throw new BizException(CommonErrorCode.CONFLICT, "度量聚合方式非法: " + aggregate);
            }
            selects.add(aggregate + "(t." + quote(field.column()) + ") AS " + quote(field.name()));
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selects))
                .append(" FROM (").append(template).append(") t");

        List<String> conditions = new ArrayList<>();
        if (filters != null) {
            for (Filter filter : filters) {
                if (filter == null || filter.field() == null) {
                    continue;
                }
                Field field = fields.stream()
                        .filter(candidate -> candidate.name().equals(filter.field()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(CommonErrorCode.BAD_REQUEST,
                                "过滤字段不在白名单: " + filter.field()));
                conditions.add(condition(field, filter, args));
            }
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (!dimensions.isEmpty()) {
            StringBuilder group = new StringBuilder(" GROUP BY ");
            for (int i = 1; i <= dimensions.size(); i++) {
                group.append(i == 1 ? "" : ", ").append(i);
            }
            sql.append(group);
            StringBuilder order = new StringBuilder(" ORDER BY ");
            for (int i = 1; i <= dimensions.size(); i++) {
                order.append(i == 1 ? "" : ", ").append(i);
            }
            sql.append(order);
        }
        return sql.toString();
    }

    private String condition(Field field, Filter filter, List<Object> args) {
        String op = filter.op() == null ? "EQ" : filter.op().trim().toUpperCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的过滤操作: " + op);
        }
        String column = "t." + quote(field.column());
        if ("IN".equals(op)) {
            if (!(filter.value() instanceof List<?> values) || values.isEmpty()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "IN 过滤需要非空数组: " + field.name());
            }
            args.addAll(values);
            return column + " IN (" + String.join(", ", java.util.Collections.nCopies(values.size(), "?")) + ")";
        }
        if (filter.value() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "过滤值不能为空: " + field.name());
        }
        args.add(filter.value());
        return switch (op) {
            case "EQ" -> column + " = ?";
            case "NE" -> column + " <> ?";
            case "GT" -> column + " > ?";
            case "GTE" -> column + " >= ?";
            case "LT" -> column + " < ?";
            case "LTE" -> column + " <= ?";
            case "LIKE" -> column + " LIKE ?";
            default -> throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的过滤操作: " + op);
        };
    }

    private void validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new BizException(CommonErrorCode.CONFLICT, "数据集 SQL 模板为空");
        }
        String normalized = template.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new BizException(CommonErrorCode.CONFLICT, "数据集 SQL 模板仅允许 SELECT/WITH");
        }
        if (template.contains(";")) {
            throw new BizException(CommonErrorCode.CONFLICT, "数据集 SQL 模板不允许多语句");
        }
    }

    private int limit(Integer requested) {
        int value = requested == null ? 1000 : requested;
        return Math.min(Math.max(value, 1), maxRows);
    }

    private String quote(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new BizException(CommonErrorCode.CONFLICT, "非法的字段列名: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private Object normalize(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        return value;
    }

    private record Field(String name, String column, String role, String aggregate) {
    }
}
