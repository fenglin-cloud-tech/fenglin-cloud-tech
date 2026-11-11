package com.fenglin.common.mybatis.partition;

import java.util.List;

/**
 * 月分区维护目标描述。父表、默认分区、分区键与授权角色均显式列出，避免各服务各写一套。
 *
 * @param schema           所属 schema（如 fenglin_hub）
 * @param table            父表名（不含 schema）
 * @param defaultPartition 默认分区名（不含 schema）
 * @param keyColumn        分区键列
 * @param keyType          分区键类型，决定边界字面量与绑定参数
 * @param lockKey          advisory lock 第二参数，配合分区命名空间 2100 使用
 * @param grantees         新分区需要授权的角色
 */
public record PartitionSpec(String schema,
                            String table,
                            String defaultPartition,
                            String keyColumn,
                            KeyType keyType,
                            int lockKey,
                            List<String> grantees) {

    public PartitionSpec {
        grantees = List.copyOf(grantees);
    }

    public String qualifiedParent() {
        return schema + "." + table;
    }

    public String qualifiedDefault() {
        return schema + "." + defaultPartition;
    }

    public String childName(String suffix) {
        return table + "_" + suffix;
    }

    public enum KeyType {
        TIMESTAMPTZ,
        DATE
    }
}
