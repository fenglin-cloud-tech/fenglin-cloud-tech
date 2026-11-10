package com.fenglin.iam.web.dto;

/**
 * 组织移动（变更父组织）。parentId 为 null 表示移动到根节点。
 */
public record MoveOrgRequest(Long parentId) {
}
