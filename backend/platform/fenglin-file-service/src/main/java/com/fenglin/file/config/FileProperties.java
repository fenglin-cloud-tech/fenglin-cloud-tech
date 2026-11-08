package com.fenglin.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fenglin.file")
public record FileProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        int presignMinutes,
        int maxSizeMb) {
}
