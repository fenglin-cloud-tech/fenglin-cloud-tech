package com.fenglin.intelligence.service;

import com.fenglin.common.core.BizException;
import com.fenglin.intelligence.IntelligenceErrorCode;
import com.fenglin.intelligence.support.IntelligenceContext;
import com.fenglin.intelligence.support.IntelligenceMaps;
import com.fenglin.intelligence.support.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BI 数据集只读查询执行器。
 * 约束：仅允许单条 SELECT/WITH；禁止注释与语句分隔符；强制行数上限与 3 秒查询超时；
 * 执行连接为云智应用连接（fl_intelligence_app），租户隔离由 RLS（app.tenant_id）兜底。
 * 外部数据源（DIRECT/JDBC）需部署期连接器，本执行器只对云智库内落地的数据集生效。
 */
@Service
public class BiQueryService {

    private static final int MAX_LIMIT = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 3;

    private final JdbcTemplate jdbcTemplate;

    public BiQueryService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbcTemplate.setMaxRows(MAX_LIMIT);
    }

    public Map<String, Object> preview(Map<String, Object> dataset, Integer limit) {
        int rowLimit = IntelligenceContext.clamp(limit == null ? 20 : limit, 1, MAX_LIMIT);
        String sql = "SELECT * FROM (" + sourceSql(dataset) + ") t LIMIT " + rowLimit;
        return execute(dataset, sql, rowLimit);
    }

    public Map<String, Object> query(Map<String, Object> dataset, Map<String, Object> body) {
        int rowLimit = IntelligenceContext.clamp(
                IntelligenceContext.intValue(IntelligenceContext.first(body, "limit"), 100), 1, MAX_LIMIT);
        Map<String, Object> query = body;
        Object nested = IntelligenceContext.first(body, "query");
        if (nested instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            query = cast;
        }
        List<String> dimensions = identifiers(IntelligenceContext.first(query, "dimensions", "dimension"));
        List<String> measures = identifiers(IntelligenceContext.first(query, "measures", "measure"));
        if (dimensions.isEmpty() && measures.isEmpty()) {
            return execute(dataset, "SELECT * FROM (" + sourceSql(dataset) + ") t LIMIT " + rowLimit, rowLimit);
        }
        String aggregation = aggregation(IntelligenceContext.string(
                IntelligenceContext.first(query, "aggregation", "agg")));
        List<String> selects = new ArrayList<>(dimensions);
        for (String measure : measures) {
            selects.add(aggregation + "(" + measure + ") AS " + measure + "_value");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selects))
                .append(" FROM (").append(sourceSql(dataset)).append(") t ");
        if (!dimensions.isEmpty()) {
            sql.append("GROUP BY ").append(String.join(", ", dimensions)).append(' ');
        }
        if (!measures.isEmpty()) {
            sql.append("ORDER BY 1 ").append(dimensions.isEmpty() ? "" : "DESC ");
        }
        sql.append("LIMIT ").append(rowLimit);
        return execute(dataset, sql.toString(), rowLimit);
    }

    private Map<String, Object> execute(Map<String, Object> dataset, String sql, int rowLimit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset_id", Rows.lngVal(dataset, "id"));
        result.put("sql", sql);
        result.put("limit", rowLimit);
        result.put("execution_target", "intelligence-datasource");
        try {
            List<List<Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            jdbcTemplate.query(sql, rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                if (columns.isEmpty()) {
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                }
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            });
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("row_count", rows.size());
            result.put("degraded", false);
            return result;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据集查询失败：" + message);
        }
    }

    private String sourceSql(Map<String, Object> dataset) {
        String sqlText = Rows.str(dataset, "sql_text");
        if (sqlText == null || sqlText.isBlank()) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据集未配置 sqlText，无法查询");
        }
        String trimmed = sqlText.strip();
        String lower = trimmed.toLowerCase();
        if (!(lower.startsWith("select ") || lower.startsWith("select\n") || lower.startsWith("select\r")
                || lower.startsWith("with "))) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据集 SQL 仅允许 SELECT/WITH");
        }
        if (trimmed.contains(";") || lower.contains("--") || lower.contains("/*")) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "数据集 SQL 不允许语句分隔符或注释");
        }
        return trimmed;
    }

    private List<String> identifiers(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        List<?> values = raw instanceof List<?> list ? list : List.of(raw);
        for (Object value : values) {
            String text = String.valueOf(value).strip();
            if (!text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "字段名仅允许字母、数字与下划线：" + text);
            }
            result.add(text);
        }
        return result;
    }

    private String aggregation(String raw) {
        String value = raw == null ? "SUM" : raw.toUpperCase();
        if (!List.of("SUM", "AVG", "COUNT", "MAX", "MIN").contains(value)) {
            throw new BizException(IntelligenceErrorCode.PARAM_INVALID, "不支持的聚合函数：" + value);
        }
        return value;
    }
}
