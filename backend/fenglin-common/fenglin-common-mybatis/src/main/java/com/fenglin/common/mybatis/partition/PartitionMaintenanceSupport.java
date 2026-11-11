package com.fenglin.common.mybatis.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 月分区维护公共实现：单事务内完成建分区/迁移默认分区数据/授权/RLS 策略复制。
 * <p>
 * 硬约束：
 * <ul>
 *   <li>同一事务内执行 DETACH → CREATE → 搬数 → DELETE → ATTACH，失败整体回滚，不会留下脱挂的 DEFAULT；</li>
 *   <li>以 {@code pg_try_advisory_xact_lock(2100, lockKey)} 串行化，拿不到锁直接放弃本次，不做任何部分操作；</li>
 *   <li>新分区开启 RLS 并逐条复制父表策略，不新增父表没有的策略。</li>
 * </ul>
 * DDL 需要库属主权限，因此使用 spring.datasource 的属主凭据单独建短连接，不占用应用连接池
 * （应用连接池已 SET ROLE fl_app，无 DDL 权限）。
 */
public class PartitionMaintenanceSupport {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceSupport.class);

    private static final int LOCK_NAMESPACE = 2100;
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter TIMESTAMPTZ_BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx");
    private static final DateTimeFormatter DATE_BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DataSourceProperties dataSourceProperties;

    public PartitionMaintenanceSupport(DataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    /**
     * 从 {@code today} 所在月起，连续确保 {@code monthCount} 个月的分区存在，返回本次新建数量。
     */
    public int ensureMonths(PartitionSpec spec, LocalDate today, int monthCount) {
        YearMonth start = YearMonth.from(today);
        int created = 0;
        for (int i = 0; i < monthCount; i++) {
            if (ensureMonth(spec, start.plusMonths(i))) {
                created++;
            }
        }
        return created;
    }

    /**
     * 确保指定月份分区存在；已存在或未取得锁时返回 false。
     */
    public boolean ensureMonth(PartitionSpec spec, YearMonth month) {
        String child = spec.childName(month.format(SUFFIX));
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!tryLock(connection, spec)) {
                    log.info("分区维护未取得锁，跳过本轮：{}.{} lockKey={}", spec.schema(), child, spec.lockKey());
                    connection.rollback();
                    return false;
                }
                if (exists(connection, spec, child)) {
                    connection.rollback();
                    return false;
                }
                long defaultRows = countDefaultRows(connection, spec, month);
                if (defaultRows == 0) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(createPartitionSql(spec, child, month));
                    }
                } else {
                    migrateDefaultPartition(connection, spec, child, month);
                }
                grant(connection, spec, child);
                applyRls(connection, spec, child);
                connection.commit();
                log.info("月分区 {}.{} 就绪，从默认分区迁移 {} 条记录", spec.schema(), child, defaultRows);
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("维护月分区失败: " + spec.qualifiedParent() + " " + child, ex);
        }
    }

    private boolean tryLock(Connection connection, PartitionSpec spec) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, spec.lockKey());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private boolean exists(Connection connection, PartitionSpec spec, String child) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, spec.schema() + "." + child);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private long countDefaultRows(Connection connection, PartitionSpec spec, YearMonth month) throws SQLException {
        String sql = "SELECT count(*) FROM " + spec.qualifiedDefault()
                + " WHERE " + spec.keyColumn() + " >= ? AND " + spec.keyColumn() + " < ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, spec, month);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void migrateDefaultPartition(Connection connection, PartitionSpec spec, String child, YearMonth month)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + spec.qualifiedParent() + " DETACH PARTITION " + spec.qualifiedDefault());
            statement.execute(createPartitionSql(spec, child, month));
        }
        String predicate = spec.keyColumn() + " >= ? AND " + spec.keyColumn() + " < ?";
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + spec.qualifiedParent() + " SELECT * FROM " + spec.qualifiedDefault()
                        + " WHERE " + predicate)) {
            bindRange(insert, spec, month);
            insert.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + spec.qualifiedDefault() + " WHERE " + predicate)) {
            bindRange(delete, spec, month);
            delete.executeUpdate();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + spec.qualifiedParent()
                    + " ATTACH PARTITION " + spec.qualifiedDefault() + " DEFAULT");
        }
    }

    private void bindRange(PreparedStatement statement, PartitionSpec spec, YearMonth month) throws SQLException {
        if (spec.keyType() == PartitionSpec.KeyType.TIMESTAMPTZ) {
            statement.setObject(1, OffsetDateTime.ofInstant(
                    month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
            statement.setObject(2, OffsetDateTime.ofInstant(
                    month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
        } else {
            statement.setObject(1, java.sql.Date.valueOf(month.atDay(1)));
            statement.setObject(2, java.sql.Date.valueOf(month.plusMonths(1).atDay(1)));
        }
    }

    private String createPartitionSql(PartitionSpec spec, String child, YearMonth month) {
        String from;
        String to;
        if (spec.keyType() == PartitionSpec.KeyType.TIMESTAMPTZ) {
            from = TIMESTAMPTZ_BOUND.format(month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC));
            to = TIMESTAMPTZ_BOUND.format(month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        } else {
            from = DATE_BOUND.format(month.atDay(1));
            to = DATE_BOUND.format(month.plusMonths(1).atDay(1));
        }
        return "CREATE TABLE " + spec.schema() + "." + child + " PARTITION OF " + spec.qualifiedParent()
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
    }

    private void grant(Connection connection, PartitionSpec spec, String child) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String grantee : spec.grantees()) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + spec.schema() + "." + child
                        + " TO " + grantee);
            }
        }
    }

    /**
     * 与父表保持一致的 RLS：父表开启则子分区开启，并逐条复制父表策略；子分区已存在的同名策略跳过。
     */
    private void applyRls(Connection connection, PartitionSpec spec, String child) throws SQLException {
        boolean rls;
        boolean force;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE oid = ?::regclass")) {
            statement.setString(1, spec.qualifiedParent());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                rls = rs.getBoolean(1);
                force = rs.getBoolean(2);
            }
        }
        if (!rls) {
            return;
        }
        String qualifiedChild = spec.schema() + "." + child;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + qualifiedChild + " ENABLE ROW LEVEL SECURITY");
            if (force) {
                statement.execute("ALTER TABLE " + qualifiedChild + " FORCE ROW LEVEL SECURITY");
            }
        }
        for (Policy policy : parentPolicies(connection, spec)) {
            if (policyExists(connection, qualifiedChild, policy.name())) {
                continue;
            }
            String sql = "CREATE POLICY " + quote(policy.name()) + " ON " + qualifiedChild
                    + " AS " + (policy.permissive() ? "PERMISSIVE" : "RESTRICTIVE")
                    + " FOR " + policy.command()
                    + " TO " + String.join(", ", policy.roles())
                    + (policy.using() == null ? "" : " USING (" + policy.using() + ")")
                    + (policy.check() == null ? "" : " WITH CHECK (" + policy.check() + ")");
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private List<Policy> parentPolicies(Connection connection, PartitionSpec spec) throws SQLException {
        String sql = "SELECT p.polname, p.polcmd, p.polpermissive, "
                + "pg_get_expr(p.polqual, p.polrelid), pg_get_expr(p.polwithcheck, p.polrelid), "
                + "ARRAY(SELECT CASE WHEN r = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(r)) END "
                + "FROM unnest(p.polroles) AS r) "
                + "FROM pg_policy p WHERE p.polrelid = ?::regclass ORDER BY p.polname";
        List<Policy> policies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, spec.qualifiedParent());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    policies.add(new Policy(rs.getString(1), command(rs.getString(2)), rs.getBoolean(3),
                            rs.getString(4), rs.getString(5),
                            (String[]) rs.getArray(6).getArray()));
                }
            }
        }
        return policies;
    }

    private boolean policyExists(Connection connection, String qualifiedChild, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_policy WHERE polrelid = ?::regclass AND polname = ?")) {
            statement.setString(1, qualifiedChild);
            statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String command(String code) {
        return switch (code) {
            case "r" -> "SELECT";
            case "a" -> "INSERT";
            case "w" -> "UPDATE";
            case "d" -> "DELETE";
            default -> "ALL";
        };
    }

    private String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private Connection ownerConnection() throws SQLException {
        String url = dataSourceProperties.getUrl();
        String username = dataSourceProperties.getUsername();
        String password = dataSourceProperties.getPassword();
        return DriverManager.getConnection(url, username == null ? "" : username,
                password == null ? "" : password);
    }

    private record Policy(String name, String command, boolean permissive, String using, String check,
                          String[] roles) {
    }
}
