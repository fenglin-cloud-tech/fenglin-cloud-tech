package com.fenglin.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
