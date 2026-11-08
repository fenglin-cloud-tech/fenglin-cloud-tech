package com.fenglin.iam.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * HUB-API-405 用户角色分配。
 */
public record AssignRolesRequest(List<Item> items) {

    public record Item(long roleId, Instant expireAt) {
    }
}
