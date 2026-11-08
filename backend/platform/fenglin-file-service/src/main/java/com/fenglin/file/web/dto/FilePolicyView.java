package com.fenglin.file.web.dto;

import java.util.List;

public record FilePolicyView(
        List<String> allowedExt,
        int maxSizeMb,
        Watermark watermark,
        int storageQuotaGb) {

    public record Watermark(boolean enabled, int secLevelFrom) {
    }
}
