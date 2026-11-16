package com.fenglin.file.web.dto;

/**
 * 存储配额用量视图（PLAT-03）：配额写入口仍在文件策略，本接口只读回显用量。
 */
public record FileQuotaView(int storageQuotaGb,
                            long usedBytes,
                            String usedGb,
                            Double usageRatio,
                            boolean exceeded) {
}
