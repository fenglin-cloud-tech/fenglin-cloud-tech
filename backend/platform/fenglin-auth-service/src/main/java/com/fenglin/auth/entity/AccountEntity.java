package com.fenglin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("hub_account")
public class AccountEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** BCrypt(成本 12) 哈希，裁决 C-06：不可解密 */
    private String passwordHash;
    private Instant passwordUpdatedAt;
    private Integer failCount;
    private Instant lockedUntil;
    private String mfaSecretEnc;
    private Integer pwdResetRequired;
    private Integer deleted;
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public void setPasswordUpdatedAt(Instant passwordUpdatedAt) {
        this.passwordUpdatedAt = passwordUpdatedAt;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getMfaSecretEnc() {
        return mfaSecretEnc;
    }

    public void setMfaSecretEnc(String mfaSecretEnc) {
        this.mfaSecretEnc = mfaSecretEnc;
    }

    public Integer getPwdResetRequired() {
        return pwdResetRequired;
    }

    public void setPwdResetRequired(Integer pwdResetRequired) {
        this.pwdResetRequired = pwdResetRequired;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
