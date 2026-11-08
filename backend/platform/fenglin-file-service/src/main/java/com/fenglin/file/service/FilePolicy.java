package com.fenglin.file.service;

import java.math.BigDecimal;
import java.util.List;

public record FilePolicy(
        List<String> allowedExt,
        int maxSizeMb,
        boolean watermarkEnabled,
        int watermarkSecLevelFrom,
        BigDecimal storageQuotaGb) {
}
