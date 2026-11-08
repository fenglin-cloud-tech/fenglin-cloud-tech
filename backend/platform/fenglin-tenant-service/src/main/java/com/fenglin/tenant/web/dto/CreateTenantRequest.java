package com.fenglin.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * HUB-API-201 租户开通。契约缺口：文档未定义 code/adminPassword，
 * 实现上 code 可缺省自动生成，adminPassword 可缺省由平台生成并仅返回一次。
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 128) String name,
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{3,31}$",
                message = "租户编码需为 4~32 位小写字母/数字/中划线") String code,
        @Size(max = 64) String contactName,
        Instant expireAt,
        @Size(max = 64) String packageCode,
        @Positive Integer trialDays,
        @NotBlank @Size(min = 3, max = 64) String adminUsername,
        @Size(max = 32) String adminMobile,
        String adminPassword) {
}
