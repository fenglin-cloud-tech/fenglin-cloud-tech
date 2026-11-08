package com.fenglin.iam.web.dto;

import java.util.List;

/**
 * HUB-API-404 角色授权。version 为文档中的乐观锁字段，当前表结构未落版本列，暂忽略（契约缺口）。
 */
public record GrantPermissionsRequest(List<String> permissionCodes, Integer version) {
}
