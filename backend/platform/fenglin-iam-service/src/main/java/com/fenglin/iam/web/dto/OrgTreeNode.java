package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * HUB-API-305 组织树节点。
 *
 * <p>雪花 ID 超出 JS Number 安全整数范围，统一按字符串序列化，避免前端精度丢失。</p>
 */
public record OrgTreeNode(
        @JsonSerialize(using = ToStringSerializer.class) long id,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        String name,
        String orgType,
        @JsonSerialize(using = ToStringSerializer.class) Long leaderId,
        int sort,
        String status,
        String pathCode,
        long memberCount,
        List<OrgTreeNode> children) {
}
