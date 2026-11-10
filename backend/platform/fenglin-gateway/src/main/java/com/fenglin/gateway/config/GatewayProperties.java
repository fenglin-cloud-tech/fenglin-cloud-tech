package com.fenglin.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置：本地验签参数、限流阈值与灰度骨架。网关为配置型组件，不直连业务库。
 */
@ConfigurationProperties(prefix = "fenglin.gateway")
public class GatewayProperties {

    private String issuer = "https://hub.fenglincloud.com";
    private String jwksUri = "http://localhost:18081/.well-known/jwks.json";
    private final RateLimit rateLimit = new RateLimit();
    private final Gray gray = new Gray();
    private final Revocation revocation = new Revocation();
    private final PermissionCache permissionCache = new PermissionCache();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Gray getGray() {
        return gray;
    }

    public Revocation getRevocation() {
        return revocation;
    }

    public PermissionCache getPermissionCache() {
        return permissionCache;
    }

    /**
     * PLAT-10 权限解析：source=jwt 时优先使用旧令牌的 permissions claim，缺失则回源；
     * source=resolver 时一律回源。Redis L2 不可用时自动降级为 L1 短 TTL（10s），auth 也不可用则 fail-close。
     */
    public static class PermissionCache {
        private String source = "jwt";
        private Duration l1Ttl = Duration.ofSeconds(60);
        private Duration l2Ttl = Duration.ofMinutes(10);
        private Duration degradedL1Ttl = Duration.ofSeconds(10);
        private Duration timeout = Duration.ofMillis(500);
        private String authInternalUri = "http://localhost:18081";
        private long maxSize = 100_000;
        private boolean redisEnabled = true;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Duration getL1Ttl() {
            return l1Ttl;
        }

        public void setL1Ttl(Duration l1Ttl) {
            this.l1Ttl = l1Ttl;
        }

        public Duration getL2Ttl() {
            return l2Ttl;
        }

        public void setL2Ttl(Duration l2Ttl) {
            this.l2Ttl = l2Ttl;
        }

        public Duration getDegradedL1Ttl() {
            return degradedL1Ttl;
        }

        public void setDegradedL1Ttl(Duration degradedL1Ttl) {
            this.degradedL1Ttl = degradedL1Ttl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public String getAuthInternalUri() {
            return authInternalUri;
        }

        public void setAuthInternalUri(String authInternalUri) {
            this.authInternalUri = authInternalUri;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }
    }

    /**
     * 令牌撤销检查：验签通过后回源 auth 内部接口，结果本地缓存；回源失败按未撤销放行（fail-open）。
     */
    public static class Revocation {
        private boolean enabled = true;
        private Duration cacheTtl = Duration.ofSeconds(30);
        private String authInternalUri = "http://localhost:18081";
        private Duration timeout = Duration.ofMillis(500);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public String getAuthInternalUri() {
            return authInternalUri;
        }

        public void setAuthInternalUri(String authInternalUri) {
            this.authInternalUri = authInternalUri;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class RateLimit {
        private int loginQps = 50;
        private int readQps = 500;
        private int writeQps = 100;

        public int getLoginQps() {
            return loginQps;
        }

        public void setLoginQps(int loginQps) {
            this.loginQps = loginQps;
        }

        public int getReadQps() {
            return readQps;
        }

        public void setReadQps(int readQps) {
            this.readQps = readQps;
        }

        public int getWriteQps() {
            return writeQps;
        }

        public void setWriteQps(int writeQps) {
            this.writeQps = writeQps;
        }
    }

    public static class Gray {
        private boolean enabled = true;
        private List<GrayRule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<GrayRule> getRules() {
            return rules;
        }

        public void setRules(List<GrayRule> rules) {
            this.rules = rules;
        }
    }

    public static class GrayRule {
        private String routeIdPrefix;
        private String matchHeader = "X-Gray-Tag";
        private String matchValue;
        private String targetTag;

        public String getRouteIdPrefix() {
            return routeIdPrefix;
        }

        public void setRouteIdPrefix(String routeIdPrefix) {
            this.routeIdPrefix = routeIdPrefix;
        }

        public String getMatchHeader() {
            return matchHeader;
        }

        public void setMatchHeader(String matchHeader) {
            this.matchHeader = matchHeader;
        }

        public String getMatchValue() {
            return matchValue;
        }

        public void setMatchValue(String matchValue) {
            this.matchValue = matchValue;
        }

        public String getTargetTag() {
            return targetTag;
        }

        public void setTargetTag(String targetTag) {
            this.targetTag = targetTag;
        }
    }
}
