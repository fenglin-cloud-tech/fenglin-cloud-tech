package com.fenglin.file.web.dto;

import java.time.Instant;

public record VersionView(int verNo, long size, String md5, Long createdBy, Instant createdAt) {
}
