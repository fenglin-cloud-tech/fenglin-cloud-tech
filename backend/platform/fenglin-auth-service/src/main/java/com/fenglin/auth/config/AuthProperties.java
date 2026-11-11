package com.fenglin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "fenglin.auth")
public class AuthProperties {

    /** OIDC issuer，需与网关/前端配置一致 */
    private String issuer = "https://hub.fenglincloud.com";
    /** 兼容旧配置的默认 kid，未显式配置 key.active-kid 时使用 */
    private String keyId = "hub-dev-1";
    private Duration accessTokenTtl = Duration.ofHours(2);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private int maxFailCount = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    private int passwordMaxAgeDays = 90;
    /** 过期 refresh token 保留期，超过该时长即物理删除 */
    private Duration expiredTokenRetention = Duration.ofDays(7);
    /** 清理任务单批删除条数 */
    private int tokenJanitorBatchSize = 5000;
    /** refresh token 并发轮换宽限期：宽限期内复用视为并发刷新，不终止会话 */
    private Duration refreshGrace = Duration.ofSeconds(10);
    /** PLAT-10 灰度开关：true 时 access token 继续携带 permissions（回滚路径），默认 false 走网关 resolver */
    private final Jwt jwt = new Jwt();
    /** PLAT-15 签名密钥来源与轮换配置 */
    private final Key key = new Key();

    public Jwt getJwt() {
        return jwt;
    }

    public Key getKey() {
        return key;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public int getMaxFailCount() {
        return maxFailCount;
    }

    public void setMaxFailCount(int maxFailCount) {
        this.maxFailCount = maxFailCount;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public int getPasswordMaxAgeDays() {
        return passwordMaxAgeDays;
    }

    public void setPasswordMaxAgeDays(int passwordMaxAgeDays) {
        this.passwordMaxAgeDays = passwordMaxAgeDays;
    }

    public Duration getExpiredTokenRetention() {
        return expiredTokenRetention;
    }

    public void setExpiredTokenRetention(Duration expiredTokenRetention) {
        this.expiredTokenRetention = expiredTokenRetention;
    }

    public int getTokenJanitorBatchSize() {
        return tokenJanitorBatchSize;
    }

    public void setTokenJanitorBatchSize(int tokenJanitorBatchSize) {
        this.tokenJanitorBatchSize = tokenJanitorBatchSize;
    }

    public Duration getRefreshGrace() {
        return refreshGrace;
    }

    public void setRefreshGrace(Duration refreshGrace) {
        this.refreshGrace = refreshGrace;
    }

    public static class Jwt {
        private boolean includePermissions = false;

        public boolean isIncludePermissions() {
            return includePermissions;
        }

        public void setIncludePermissions(boolean includePermissions) {
            this.includePermissions = includePermissions;
        }
    }

    /**
     * PLAT-15：签名密钥来源与轮换。
     *
     * <p>{@code provider} 决定用哪个 JwtKeySource 实现（默认 default）；
     * {@code source} 决定默认实现从哪读：auto / jwk / pem / file。
     * {@code activeKid} 指定签发使用的 kid，其余 kid 仅用于验签。
     */
    public static class Key {
        private String provider = "default";
        private String source = "auto";
        private String file = ".keys/jwt-signing-key.json";
        private String activeKid;
        private String jwkSet = "";
        private String privateKeyPem = "";
        private List<String> publicKeysPem = new ArrayList<>();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getActiveKid() {
            return activeKid;
        }

        public void setActiveKid(String activeKid) {
            this.activeKid = activeKid;
        }

        public String getJwkSet() {
            return jwkSet;
        }

        public void setJwkSet(String jwkSet) {
            this.jwkSet = jwkSet;
        }

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public List<String> getPublicKeysPem() {
            return publicKeysPem;
        }

        public void setPublicKeysPem(List<String> publicKeysPem) {
            this.publicKeysPem = publicKeysPem;
        }
    }
}
