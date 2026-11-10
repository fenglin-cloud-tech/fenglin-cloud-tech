package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** PLAT-08 内部响应：账号绑定的员工 ID，未绑定时字段不返回。 */
public record EmployeeBindingView(
        @JsonSerialize(using = ToStringSerializer.class) Long employeeId) {
}
