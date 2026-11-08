package com.fenglin.gateway.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关管理 API（扩展）：总纲仅定义 hub_gw_route / hub_gw_gray_rule 表，未定义接口契约；
 * 按前端列表展示的最小语义提供只读视图。网关为配置型组件，不直连业务库，因此不提供在线写入。
 */
@RestController
public class GatewayAdminController {

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final GatewayProperties properties;

    public GatewayAdminController(RouteDefinitionLocator routeDefinitionLocator,
                                  GatewayProperties properties) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.properties = properties;
    }

    /** 扩展：当前生效路由列表。 */
    @GetMapping("/api/hub/v1/admin/gateway/routes")
    public Mono<ApiResponse<List<RouteView>>> routes() {
        return routeDefinitionLocator.getRouteDefinitions()
                .map(this::toView)
                .collectList()
                .map(ApiResponse::success);
    }

    /** 扩展：灰度规则（来自网关配置）。 */
    @GetMapping("/api/hub/v1/admin/gateway/gray-rules")
    public ApiResponse<GrayView> grayRules() {
        GatewayProperties.Gray gray = properties.getGray();
        List<GrayRuleView> rules = gray.getRules().stream()
                .map(rule -> new GrayRuleView(rule.getRouteIdPrefix(), rule.getMatchHeader(),
                        rule.getMatchValue(), rule.getTargetTag()))
                .toList();
        return ApiResponse.success(new GrayView(gray.isEnabled(), rules));
    }

    /** 扩展：限流阈值（来自网关配置）。 */
    @GetMapping("/api/hub/v1/admin/gateway/rate-limits")
    public ApiResponse<RateLimitView> rateLimits() {
        GatewayProperties.RateLimit rateLimit = properties.getRateLimit();
        return ApiResponse.success(new RateLimitView(rateLimit.getLoginQps(),
                rateLimit.getReadQps(), rateLimit.getWriteQps()));
    }

    private RouteView toView(RouteDefinition definition) {
        List<PredicateView> predicates = definition.getPredicates().stream()
                .map(predicate -> new PredicateView(predicate.getName(),
                        predicate.getArgs() == null ? Map.of() : new LinkedHashMap<>(predicate.getArgs())))
                .toList();
        return new RouteView(definition.getId(),
                definition.getUri() == null ? null : definition.getUri().toString(),
                predicates, definition.getOrder());
    }

    public record RouteView(@JsonProperty("route_id") String routeId,
                            @JsonProperty("uri") String uri,
                            @JsonProperty("predicates") List<PredicateView> predicates,
                            @JsonProperty("order") int order) {
    }

    public record PredicateView(@JsonProperty("name") String name,
                                @JsonProperty("args") Map<String, String> args) {
    }

    public record GrayView(@JsonProperty("enabled") boolean enabled,
                           @JsonProperty("rules") List<GrayRuleView> rules) {
    }

    public record GrayRuleView(@JsonProperty("route_id_prefix") String routeIdPrefix,
                               @JsonProperty("match_header") String matchHeader,
                               @JsonProperty("match_value") String matchValue,
                               @JsonProperty("target_tag") String targetTag) {
    }

    public record RateLimitView(@JsonProperty("login_qps") int loginQps,
                                @JsonProperty("read_qps") int readQps,
                                @JsonProperty("write_qps") int writeQps) {
    }
}
