package com.fenglin.gateway.filter;

import com.fenglin.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 灰度骨架（F-GW-05）：按配置规则匹配请求头，命中后向下游注入 X-Gray-Route 标签。
 * hub_gw_gray_rule 表已建，P1 先由配置中心/Nacos 下发规则，动态表读取待配置中心接入后补齐。
 */
@Component
public class GrayGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_GRAY_ROUTE = "X-Gray-Route";

    private final GatewayProperties properties;

    public GrayGlobalFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayProperties.Gray gray = properties.getGray();
        if (!gray.isEnabled() || gray.getRules() == null || gray.getRules().isEmpty()) {
            return chain.filter(exchange);
        }
        for (GatewayProperties.GrayRule rule : gray.getRules()) {
            String actual = exchange.getRequest().getHeaders().getFirst(rule.getMatchHeader());
            if (actual != null && actual.equals(rule.getMatchValue())) {
                return chain.filter(exchange.mutate()
                        .request(exchange.getRequest().mutate()
                                .headers(headers -> headers.set(HEADER_GRAY_ROUTE, rule.getTargetTag()))
                                .build())
                        .build());
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -80;
    }
}
