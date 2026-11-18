package com.fenglin.collab.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 富文本正文中的图片块解析：只读取 blocks[type=image].fileId，不把正文当明文做其他用途。
 */
public final class RichImageBlocks {

    private RichImageBlocks() {
    }

    public static List<Long> fileIds(String content, String format, ObjectMapper objectMapper) {
        if (!"RICH_JSON".equals(format) || content == null || content.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode blocks = root == null ? null : root.get("blocks");
            if (blocks == null || !blocks.isArray()) {
                return List.of();
            }
            List<Long> result = new ArrayList<>();
            for (JsonNode block : blocks) {
                if (!"image".equals(block.path("type").asText())) {
                    continue;
                }
                String fileId = block.path("fileId").asText(null);
                if (fileId == null || fileId.isBlank()) {
                    fileId = block.path("file_id").asText(null);
                }
                if (fileId == null || fileId.isBlank()) {
                    continue;
                }
                try {
                    result.add(Long.parseLong(fileId.trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 fileId 不参与签发，渲染端也无法拼出访问 URL
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
