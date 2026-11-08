package com.fenglin.message.web.dto;

import java.util.List;

public record SendMessageResult(List<Long> messageIds, String status, int suppressed, int failed) {
}
