package com.fenglin.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Map;

@Mapper
public interface AuditExportTaskMapper {

    @Insert("""
            INSERT INTO hub_audit_export_task (id, tenant_id, task_no, format, event_type,
                                               filter_from, filter_to, status,
                                               created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{taskNo}, #{format}, #{eventType},
                    #{from,jdbcType=TIMESTAMP}, #{to,jdbcType=TIMESTAMP}, 'PENDING',
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("taskNo") String taskNo,
               @Param("format") String format,
               @Param("eventType") String eventType,
               @Param("from") Instant from,
               @Param("to") Instant to,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, tenant_id, task_no, format, event_type, filter_from, filter_to, status,
                   file_path, row_count, error_msg, created_at
              FROM hub_audit_export_task
             WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("id") long id);

    @Update("""
            UPDATE hub_audit_export_task
               SET status = 'RUNNING', updated_at = now()
             WHERE id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int markRunning(@Param("id") long id);

    @Update("""
            UPDATE hub_audit_export_task
               SET status = 'SUCCEEDED', file_path = #{filePath}, row_count = #{rowCount},
                   error_msg = NULL, updated_at = now()
             WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0
            """)
    int markSucceeded(@Param("id") long id,
                      @Param("filePath") String filePath,
                      @Param("rowCount") long rowCount);

    @Update("""
            UPDATE hub_audit_export_task
               SET status = 'FAILED', error_msg = #{errorMsg}, updated_at = now()
             WHERE id = #{id} AND status IN ('PENDING', 'RUNNING') AND deleted = 0
            """)
    int markFailed(@Param("id") long id, @Param("errorMsg") String errorMsg);
}
