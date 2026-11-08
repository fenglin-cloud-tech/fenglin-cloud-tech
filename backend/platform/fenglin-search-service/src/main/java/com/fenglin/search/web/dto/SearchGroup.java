package com.fenglin.search.web.dto;

import java.util.List;

public record SearchGroup(String domain, List<SearchRecord> records) {
}
