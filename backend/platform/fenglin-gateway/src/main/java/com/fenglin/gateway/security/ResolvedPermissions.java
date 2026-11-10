package com.fenglin.gateway.security;

import java.util.List;

/**
 * 网关解析出的权威权限快照。permissionVersion 来自 auth，可能与令牌内 pv 不同（权限已变更）。
 */
public record ResolvedPermissions(long permissionVersion, List<String> roles,
                                  List<String> permissions, long resolvedAt) {
}
