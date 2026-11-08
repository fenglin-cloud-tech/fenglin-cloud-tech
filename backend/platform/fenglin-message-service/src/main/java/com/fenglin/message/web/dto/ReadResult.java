package com.fenglin.message.web.dto;

import java.time.Instant;

public record ReadResult(long messageId, String status, Instant readAt) {
}
