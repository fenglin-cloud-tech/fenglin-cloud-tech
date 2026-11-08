package com.fenglin.search.web.dto;

/**
 * HUB-API-903 索引任务创建请求。taskType：FULL（不存在则建索引）/INCREMENTAL（索引必须已存在）。
 */
public record IndexTaskRequest(String indexName, String taskType) {
}
