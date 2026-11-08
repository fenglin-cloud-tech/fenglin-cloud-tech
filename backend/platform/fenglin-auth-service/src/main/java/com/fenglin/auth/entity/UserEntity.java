package com.fenglin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("hub_user")
public class UserEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private String employeeNo;
    private String username;
    private String nickname;
    private String realName;
    private String mobileEnc;
    private String emailEnc;
    private Long primaryOrgId;
    private Long primaryPositionId;
    private String userType;
    private String status;
    private Integer mfaEnabled;
    private Instant lastLoginAt;
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

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getMobileEnc() {
        return mobileEnc;
    }

    public void setMobileEnc(String mobileEnc) {
        this.mobileEnc = mobileEnc;
    }

    public String getEmailEnc() {
        return emailEnc;
    }

    public void setEmailEnc(String emailEnc) {
        this.emailEnc = emailEnc;
    }

    public Long getPrimaryOrgId() {
        return primaryOrgId;
    }

    public void setPrimaryOrgId(Long primaryOrgId) {
        this.primaryOrgId = primaryOrgId;
    }

    public Long getPrimaryPositionId() {
        return primaryPositionId;
    }

    public void setPrimaryPositionId(Long primaryPositionId) {
        this.primaryPositionId = primaryPositionId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(Integer mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
