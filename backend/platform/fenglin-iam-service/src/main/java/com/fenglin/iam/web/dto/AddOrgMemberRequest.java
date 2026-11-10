package com.fenglin.iam.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 组织成员添加。
 */
public record AddOrgMemberRequest(@NotNull Long userId) {
}
