package com.fenglin.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志按月分区维护：启动补建当月/下月分区，之后每月滚动补建下月。
 * 建分区属 DDL，必须以库属主连接执行（应用连接已 SET ROLE fl_app，无 DDL 权限）。
 * 若默认分区已存在当月数据，先摘除默认分区、迁移数据后再创建并重新挂回。
 */
@Component
public class AuditPartitionMaintainer {

    private static final Logger log = LoggerFactory.getLogger(AuditPartitionMaintainer.class);

    private static final String PARENT = "fenglin_hub.hub_audit_log";
    private static final String DEFAULT_PARTITION = "fenglin_hub.hub_audit_log_default";
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx");
    private static final List<String> GRANTEES = List.of("fl_app", "fl_platform_role");

    private final DataSourceProperties dataSourceProperties;
    private final boolean enabled;

    public AuditPartitionMaintainer(DataSourceProperties dataSourceProperties,
                                    @Value("${fenglin.audit.partition-maintenance-enabled:true}") boolean enabled) {
        this.dataSourceProperties = dataSourceProperties;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) {
            return;
        }
        try {
            ensurePartitions(LocalDate.now(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            log.error("审计月分区启动补建失败，审计写入暂时落在默认分区", ex);
        }
    }

    @Scheduled(cron = "${fenglin.audit.partition-maintenance-cron:0 5 1 1 * *}")
    public void scheduledMaintain() {
        if (!enabled) {
            return;
        }
        try {
            ensurePartitions(LocalDate.now(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            log.error("审计月分区月度维护失败", ex);
        }
    }

    /**
     * 补建指定日期所在月与下个月的分区，返回本次新建的分区数。
     */
    public int ensurePartitions(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        int created = 0;
        if (ensureMonth(month)) {
            created++;
        }
        if (ensureMonth(month.plusMonths(1))) {
            created++;
        }
        return created;
    }

    /**
     * 确保指定月份分区存在；已存在时返回 false。
     */
    public boolean ensureMonth(YearMonth month) {
        String table = "hub_audit_log_" + month.format(SUFFIX);
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        try (Connection connection = ownerConnection()) {
            if (exists(connection, table)) {
                return false;
            }
            long defaultRows = countDefaultRows(connection, start, end);
            if (defaultRows == 0) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(createPartitionSql(table, start, end));
                }
            } else {
                migrateDefaultPartition(connection, table, start, end);
            }
            grantCredentials(connection, table);
            log.info("审计月分区 {} 就绪，从默认分区迁移 {} 条记录", table, defaultRows);
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("创建审计月分区失败: " + table, ex);
        }
    }

    private void migrateDefaultPartition(Connection connection, String table, Instant start, Instant end)
            throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + PARENT + " DETACH PARTITION " + DEFAULT_PARTITION);
            statement.execute(createPartitionSql(table, start, end));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + PARENT + " SELECT * FROM " + DEFAULT_PARTITION
                            + " WHERE occurred_at >= ? AND occurred_at < ?")) {
                bind(insert, start, end);
                insert.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + DEFAULT_PARTITION + " WHERE occurred_at >= ? AND occurred_at < ?")) {
                bind(delete, start, end);
                delete.executeUpdate();
            }
            statement.execute("ALTER TABLE " + PARENT + " ATTACH PARTITION " + DEFAULT_PARTITION + " DEFAULT");
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void bind(PreparedStatement statement, Instant start, Instant end) throws SQLException {
        statement.setObject(1, OffsetDateTime.ofInstant(start, ZoneOffset.UTC));
        statement.setObject(2, OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
    }

    private String createPartitionSql(String table, Instant start, Instant end) {
        return "CREATE TABLE fenglin_hub." + table + " PARTITION OF " + PARENT
                + " FOR VALUES FROM ('" + BOUND.format(OffsetDateTime.ofInstant(start, ZoneOffset.UTC)) + "')"
                + " TO ('" + BOUND.format(OffsetDateTime.ofInstant(end, ZoneOffset.UTC)) + "')";
    }

    private boolean exists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, "fenglin_hub." + table);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private long countDefaultRows(Connection connection, Instant start, Instant end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM " + DEFAULT_PARTITION + " WHERE occurred_at >= ? AND occurred_at < ?")) {
            bind(statement, start, end);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void grantCredentials(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String grantee : GRANTEES) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_hub." + table
                        + " TO " + grantee);
            }
        }
    }

    private Connection ownerConnection() throws SQLException {
        String url = dataSourceProperties.getUrl();
        String username = dataSourceProperties.getUsername();
        String password = dataSourceProperties.getPassword();
        return DriverManager.getConnection(url, username == null ? "" : username,
                password == null ? "" : password);
    }
}
