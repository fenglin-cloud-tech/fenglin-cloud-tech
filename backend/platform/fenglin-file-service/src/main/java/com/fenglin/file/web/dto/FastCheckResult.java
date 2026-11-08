package com.fenglin.file.web.dto;

public record FastCheckResult(boolean exists, Long fileId, String fileNo) {
}
