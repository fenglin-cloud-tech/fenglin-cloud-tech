package com.fenglin.auth.web.dto;

/**
 * 自助修改口令：必须提供旧口令；新口令走 PasswordPolicy 强度校验且不得与旧口令相同。
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
