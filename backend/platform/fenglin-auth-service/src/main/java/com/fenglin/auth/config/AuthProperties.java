package com.fenglin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "fenglin.auth")
public class AuthProperties {

    /** OIDC issuer，需与网关/前端配置一致 */
    private String issuer = "https://hub.fenglincloud.com";
    /** JWKS kid；P1 本地开发使用启动时生成的 RSA 密钥，生产由 KMS/密钥文件提供 */
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
}
