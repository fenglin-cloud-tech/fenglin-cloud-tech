package com.fenglin.message.web.dto;

import java.util.List;

public record PreferenceView(String eventType, List<String> channels,
                             String quietHoursFrom, String quietHoursTo) {
}
