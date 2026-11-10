package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.Size;

/**
 * HUB-API-302 用户更新：字段均可选，mobile/email 传空字符串表示清空。
 */
public record UpdateUserProfileRequest(
        @Size(max = 64) String nickname,
        String mobile,
        String email,
        Long primaryOrgId,
        String status,
        @Size(max = 32) String employeeNo,
        Integer version) {
}
