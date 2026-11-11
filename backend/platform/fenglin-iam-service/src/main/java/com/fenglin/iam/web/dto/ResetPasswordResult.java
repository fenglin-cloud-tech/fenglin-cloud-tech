package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 重置口令结果：仅当服务端生成口令时返回 newPassword（响应中只出现一次）。
 */
public record ResetPasswordResult(
        @JsonSerialize(using = ToStringSerializer.class) long userId,
        String newPassword) {
}
