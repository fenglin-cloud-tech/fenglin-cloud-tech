package com.fenglin.file.web.dto;

import java.util.List;

public record FilePolicyRequest(
        List<String> allowedExt,
        Integer maxSizeMb,
        Watermark watermark,
        Integer storageQuotaGb) {

    public record Watermark(Boolean enabled, Integer secLevelFrom) {
    }
}
