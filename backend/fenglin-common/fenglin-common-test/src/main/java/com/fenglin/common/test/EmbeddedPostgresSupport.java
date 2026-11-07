package com.fenglin.common.test;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 真实 PostgreSQL 15 测试实例（无需 Docker），用于迁移校验与 RLS 行为验证。
 */
public final class EmbeddedPostgresSupport implements AutoCloseable {

    private final EmbeddedPostgres postgres;

    private EmbeddedPostgresSupport(EmbeddedPostgres postgres) {
        this.postgres = postgres;
    }

    public static EmbeddedPostgresSupport start() {
        try {
            return new EmbeddedPostgresSupport(EmbeddedPostgres.builder().start());
        } catch (IOException ex) {
            throw new UncheckedIOException("无法启动内嵌 PostgreSQL", ex);
        }
    }

    public String jdbcUrl() {
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    public DataSource dataSource() {
        return postgres.getPostgresDatabase();
    }

    @Override
    public void close() {
        try {
            postgres.close();
        } catch (IOException ex) {
            throw new UncheckedIOException("关闭内嵌 PostgreSQL 失败", ex);
        }
    }
}
