package com.fenglin.message.service;

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
 * 消息表按月分区维护：启动补建当月/下月分区，之后每月滚动补建。
 * 建分区属 DDL，必须以库属主连接执行。
 */
@Component
public class MessagePartitionMaintainer {

    private static final Logger log = LoggerFactory.getLogger(MessagePartitionMaintainer.class);

    private static final String PARENT = "fenglin_hub.hub_message";
    private static final String DEFAULT_PARTITION = "fenglin_hub.hub_message_default";
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx");
    private static final List<String> GRANTEES = List.of("fl_app", "fl_platform_role");

    private final DataSourceProperties dataSourceProperties;
    private final boolean enabled;

    public MessagePartitionMaintainer(DataSourceProperties dataSourceProperties,
                                      @Value("${fenglin.message.partition-maintenance-enabled:true}") boolean enabled) {
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
            log.error("消息月分区启动补建失败，消息暂时落在默认分区", ex);
        }
    }

    @Scheduled(cron = "${fenglin.message.partition-maintenance-cron:0 10 1 1 * *}")
    public void scheduledMaintain() {
        if (!enabled) {
            return;
        }
        try {
            ensurePartitions(LocalDate.now(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            log.error("消息月分区月度维护失败", ex);
        }
    }

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

    public boolean ensureMonth(YearMonth month) {
        String table = "hub_message_" + month.format(SUFFIX);
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
            log.info("消息月分区 {} 就绪，从默认分区迁移 {} 条记录", table, defaultRows);
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("创建消息月分区失败: " + table, ex);
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
                            + " WHERE created_at >= ? AND created_at < ?")) {
                bind(insert, start, end);
                insert.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + DEFAULT_PARTITION + " WHERE created_at >= ? AND created_at < ?")) {
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
                "SELECT count(*) FROM " + DEFAULT_PARTITION + " WHERE created_at >= ? AND created_at < ?")) {
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
