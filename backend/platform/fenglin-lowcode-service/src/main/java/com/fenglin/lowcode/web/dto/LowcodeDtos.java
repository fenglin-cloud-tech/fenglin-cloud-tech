package com.fenglin.lowcode.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LowcodeDtos {

    private LowcodeDtos() {
    }

    /** 数据模型字段；文档仅示例 name/type，其余为约束扩展。 */
    public record ModelField(String name, String type, Boolean required, Integer maxLength, String defaultValue) {
    }

    /** dataModel.publishProcessKey 为发布审批流程键（1202 processInstanceId 的兜底约定）。 */
    public record DataModel(List<ModelField> fields, String publishProcessKey) {
    }

    public record AppCreateRequest(String code, String name, String appType,
                                   JsonNode schema, DataModel dataModel, String env) {
    }

    public record AppView(long id, String code, String name, String appType, JsonNode schema,
                          DataModel dataModel, String env, String status, Instant publishedAt,
                          int latestVersion) {
    }

    public record PublishResult(long appId, String env, String status, String processInstanceId,
                                int versionNo) {
    }

    public record SubmitRequest(Map<String, Object> data) {
    }

    public record SubmitResult(long recordId, String recordNo, String status) {
    }

    public record VersionView(long id, int versionNo, String status, String processInstanceId,
                              Instant publishedAt, JsonNode schema) {
    }

    /** 扩展接口：版本回滚（验收 3 无接口编号，按总纲兜底）。 */
    public record RollbackRequest(Integer versionNo) {
    }

    /** 扩展接口：运行时数据查询（验收 3 无接口编号，按总纲兜底）。 */
    public record RecordView(long id, String recordNo, JsonNode data, String status,
                             Long submittedBy, Instant createdAt) {
    }
}
