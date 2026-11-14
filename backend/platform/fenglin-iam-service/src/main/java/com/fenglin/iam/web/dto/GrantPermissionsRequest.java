package com.fenglin.iam.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * HUB-API-404 角色授权。version 为文档中的乐观锁字段，当前表结构未落版本列，暂忽略（契约缺口）。
 *
 * <p>请求体只在本端点内严格解析，不依赖全局 Jackson 的未知字段策略：全局放开会波及其它接口，
 * 全局收紧又会让既有调用方因多余字段失败。解析规则：
 * <ul>
 *   <li>缺少 {@code permission_codes}（或为 null）直接 400，绝不按空数组处理；</li>
 *   <li>显式传 {@code []} 才表示清空权限；</li>
 *   <li>出现未知字段（如驼峰 {@code permissionCodes}）直接 400 并指出字段名。</li>
 * </ul>
 */
public record GrantPermissionsRequest(List<String> permissionCodes, Integer version) {

    private static final String PERMISSION_CODES = "permission_codes";
    private static final String VERSION = "version";
    private static final Set<String> ALLOWED_FIELDS = Set.of(PERMISSION_CODES, VERSION);

    public static GrantPermissionsRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw badRequest("请求体必须是 JSON 对象");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                throw badRequest("未知字段: " + name + "（仅支持 " + PERMISSION_CODES + " 与 " + VERSION + "）");
            }
        }
        JsonNode codesNode = body.get(PERMISSION_CODES);
        if (codesNode == null || codesNode.isNull()) {
            throw badRequest("缺少必填字段: " + PERMISSION_CODES + "（显式传空数组才表示清空权限）");
        }
        if (!codesNode.isArray()) {
            throw badRequest("字段 " + PERMISSION_CODES + " 必须是字符串数组");
        }
        List<String> codes = new ArrayList<>(codesNode.size());
        for (JsonNode item : codesNode) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw badRequest("字段 " + PERMISSION_CODES + " 只能包含非空字符串");
            }
            codes.add(item.asText());
        }
        Integer version = null;
        JsonNode versionNode = body.get(VERSION);
        if (versionNode != null && !versionNode.isNull()) {
            if (!versionNode.isIntegralNumber()) {
                throw badRequest("字段 " + VERSION + " 必须是整数");
            }
            version = versionNode.asInt();
        }
        return new GrantPermissionsRequest(codes, version);
    }

    private static BizException badRequest(String message) {
        return new BizException(CommonErrorCode.BAD_REQUEST, message);
    }
}
