package com.fenglin.search.web.dto;

import java.util.List;

public record SearchQueryResponse(List<SearchGroup> groups, long latencyMs) {
}
