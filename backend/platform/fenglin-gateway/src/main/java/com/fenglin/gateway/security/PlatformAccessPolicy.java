package com.fenglin.gateway.security;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

/**
 * OPEN-06 一期：平台切换令牌的跨租户只读边界。
 *
 * <p>采用<b>正向白名单</b>：只有列出的平台治理域路径可跨租户访问；业务域与其他路径一律拒绝。
 * 业务域前缀仅用于生成更明确的错误提示，不参与放行判断，避免未来新增业务域时默认漏放。</p>
 */
public final class PlatformAccessPolicy {

    public static final String READ_ONLY_MESSAGE =
            "平台切换令牌为只读：仅允许跨租户查看平台治理域数据（租户/组织/用户/角色/审计/配置/订阅）";
    public static final String BUSINESS_DOMAIN_MESSAGE =
            "业务域数据不属于一期跨租户只读范围（八个业务域跨租户读为二期，需重新评审可见范围与脱敏）";
    public static final String GOVERNANCE_MESSAGE =
            "跨租户只读范围仅限平台治理域（租户/组织/用户/角色/审计/配置/订阅）";

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    /** 正向前缀白名单：命中即允许（仍需方法为安全方法）。 */
    private static final List<String> GOVERNANCE_PATHS = List.of(
            "/api/hub/v1/admin/tenants",
            "/api/hub/v1/admin/orgs",
            "/api/hub/v1/admin/users",
            "/api/hub/v1/admin/roles",
            "/api/hub/v1/admin/permissions",
            "/api/hub/v1/admin/menus",
            "/api/hub/v1/admin/dicts",
            "/api/hub/v1/admin/params",
            "/api/hub/v1/admin/audits",
            "/api/hub/v1/admin/audit",
            "/api/hub/v1/admin/subscriptions",
            "/api/hub/v1/sso/userinfo"
    );

    private static final List<String> BUSINESS_PREFIXES = List.of(
            "/api/crm/",
            "/api/collab/",
            "/api/talent/",
            "/api/finance/",
            "/api/rnd/",
            "/api/service/",
            "/api/legal/",
            "/api/intelligence/"
    );

    private PlatformAccessPolicy() {
    }

    public static boolean isSafeMethod(String method) {
        return method != null && SAFE_METHODS.contains(method.toUpperCase());
    }

    public static boolean isGovernancePath(String path) {
        if (path == null) {
            return false;
        }
        return GOVERNANCE_PATHS.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    public static boolean isBusinessDomainPath(String path) {
        return path != null && BUSINESS_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
