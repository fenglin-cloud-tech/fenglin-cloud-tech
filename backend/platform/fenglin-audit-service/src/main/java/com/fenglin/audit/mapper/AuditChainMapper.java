package com.fenglin.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditChainMapper {

    /**
     * 事务级咨询锁：同租户同月份串行分配 seq_no，事务结束自动释放。
     */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockKey}, 0))) t")
    int lock(@Param("lockKey") String lockKey);

    @Select("""
            SELECT seq_no, chain_hash
              FROM hub_audit_chain
             WHERE tenant_id = #{tenantId} AND partition_key = #{partitionKey}
             ORDER BY seq_no DESC
             LIMIT 1
            """)
    Map<String, Object> findLast(@Param("tenantId") long tenantId,
                                 @Param("partitionKey") String partitionKey);

    @Select("""
            SELECT id, tenant_id, log_id, partition_key, seq_no, content_hash, prev_hash, chain_hash
              FROM hub_audit_chain
             WHERE tenant_id = #{tenantId}
               AND partition_key = #{partitionKey}
               AND seq_no >= #{fromSeq}
             ORDER BY seq_no
            """)
    List<Map<String, Object>> findFrom(@Param("tenantId") long tenantId,
                                       @Param("partitionKey") String partitionKey,
                                       @Param("fromSeq") long fromSeq);

    /**
     * 指定月份区间内该租户实际存在的链分区，用于链校验时避免逐月空查询。
     */
    @Select("""
            SELECT DISTINCT partition_key
              FROM hub_audit_chain
             WHERE tenant_id = #{tenantId}
               AND partition_key >= #{fromKey}
               AND partition_key <= #{toKey}
             ORDER BY partition_key
            """)
    List<String> findPartitionKeys(@Param("tenantId") long tenantId,
                                   @Param("fromKey") String fromKey,
                                   @Param("toKey") String toKey);

    @Insert("""
            INSERT INTO hub_audit_chain (id, tenant_id, log_id, partition_key, seq_no,
                                         content_hash, prev_hash, chain_hash,
                                         created_at, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{logId}, #{partitionKey}, #{seqNo},
                    #{contentHash}, #{prevHash}, #{chainHash},
                    now(), now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("logId") long logId,
               @Param("partitionKey") String partitionKey,
               @Param("seqNo") long seqNo,
               @Param("contentHash") String contentHash,
               @Param("prevHash") String prevHash,
               @Param("chainHash") String chainHash);
}
