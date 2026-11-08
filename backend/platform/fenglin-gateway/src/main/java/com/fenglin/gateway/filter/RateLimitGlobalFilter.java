package com.fenglin.gateway.filter;

import com.fenglin.gateway.config.GatewayProperties;
import com.fenglin.gateway.security.GatewayPrincipal;
import com.fenglin.gateway.web.GatewayResponses;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户 + 接口级限流骨架（F-GW-04）。P1 使用单节点内存窗口，阈值取自《接口文档》第 1 章：
 * 登录 50 QPS、列表 500 QPS、写 100 QPS。集群化时替换为 Redis 令牌桶（待办）。
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final String LOGIN_PATH = "/api/hub/v1/sso/login";

    private final GatewayProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitGlobalFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }
        GatewayPrincipal principal = exchange.getAttribute(AuthGlobalFilter.PRINCIPAL_ATTRIBUTE);
        String tenant = principal == null ? "anonymous" : String.valueOf(principal.tenantId());
        String category = category(exchange.getRequest());
        String key = tenant + ":" + category;

        int limit = limit(category);
        long second = System.currentTimeMillis() / 1000;
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        long current;
        synchronized (window) {
            if (window.second != second) {
                window.second = second;
                window.count = 0;
            }
            current = ++window.count;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().second < second - 60);
        }
        if (current > limit) {
            exchange.getResponse().getHeaders().set("Retry-After", "1");
            return GatewayResponses.error(exchange, HttpStatus.TOO_MANY_REQUESTS, "FL-COMMON-0429", "触发限流");
        }
        return chain.filter(exchange);
    }

    private String category(ServerHttpRequest request) {
        if (LOGIN_PATH.equals(request.getURI().getPath())) {
            return "login";
        }
        return HttpMethod.GET.equals(request.getMethod()) ? "read" : "write";
    }

    private int limit(String category) {
        return switch (category) {
            case "login" -> properties.getRateLimit().getLoginQps();
            case "read" -> properties.getRateLimit().getReadQps();
            default -> properties.getRateLimit().getWriteQps();
        };
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private static final class Window {
        private long second;
        private long count;
    }
}
