package com.fenglin.common.mybatis.partition;

import org.springframework.core.env.Environment;

/**
 * 独立 DDL 数据源配置，前缀 {@code fenglin.db.ddl}。
 * <p>
 * 硬约束：
 * <ul>
 *   <li>只允许使用 DDL 登录角色（{@code fl_login_ddl}）+ {@code SET ROLE fl_ddl_owner}；</li>
 *   <li>口令只能来自环境变量 {@code FL_LOGIN_DDL_PASSWORD}，不得写入仓库；</li>
 *   <li>配置缺失或口令为空时直接失败，禁止回退到应用数据源（超级用户）凭据。</li>
 * </ul>
 */
public record DdlConnectionConfig(String url, String username, String password, String role) {

    public static final String PREFIX = "fenglin.db.ddl.";
    public static final String DEFAULT_ROLE = "fl_ddl_owner";

    public DdlConnectionConfig {
        if (!hasText(url)) {
            throw new IllegalStateException("缺少 DDL 数据源配置 " + PREFIX + "url，拒绝回退到应用数据源");
        }
        if (!hasText(username)) {
            throw new IllegalStateException("缺少 DDL 数据源配置 " + PREFIX + "username（应为 fl_login_ddl），拒绝回退到应用数据源");
        }
        if (!hasText(password)) {
            throw new IllegalStateException("DDL 数据源口令为空：请通过环境变量 FL_LOGIN_DDL_PASSWORD 注入，禁止写入仓库");
        }
        if (!hasText(role)) {
            throw new IllegalStateException("缺少 DDL 角色配置 " + PREFIX + "role（应为 fl_ddl_owner）");
        }
    }

    public static DdlConnectionConfig from(Environment environment) {
        return new DdlConnectionConfig(
                environment.getProperty(PREFIX + "url"),
                environment.getProperty(PREFIX + "username"),
                environment.getProperty(PREFIX + "password"),
                environment.getProperty(PREFIX + "role", DEFAULT_ROLE));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "DdlConnectionConfig[url=" + url + ", username=" + username + ", role=" + role + ", password=***]";
    }
}
