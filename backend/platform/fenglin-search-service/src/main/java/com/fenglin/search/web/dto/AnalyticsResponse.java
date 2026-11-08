package com.fenglin.search.web.dto;

import java.util.List;

public record AnalyticsResponse(String type, List<AnalyticsItem> items) {
}
