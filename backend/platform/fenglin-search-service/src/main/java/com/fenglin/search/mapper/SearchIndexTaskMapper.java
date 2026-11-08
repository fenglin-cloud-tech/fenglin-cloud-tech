package com.fenglin.search.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface SearchIndexTaskMapper {

    @Insert("""
            INSERT INTO hub_search_index_task (id, tenant_id, index_name, task_type, status,
                                               created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{indexName}, #{taskType}, 'PENDING',
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("indexName") String indexName,
               @Param("taskType") String taskType,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, index_name, task_type, status, doc_count, error_msg
              FROM hub_search_index_task
             WHERE tenant_id = #{tenantId} AND index_name = #{indexName} AND task_type = #{taskType}
               AND status IN ('PENDING', 'RUNNING') AND deleted = 0
             ORDER BY created_at DESC LIMIT 1
            """)
    Map<String, Object> findActiveTask(@Param("tenantId") long tenantId,
                                       @Param("indexName") String indexName,
                                       @Param("taskType") String taskType);

    @Select("""
            SELECT id, index_name, task_type, status, doc_count, error_msg
              FROM hub_search_index_task
             WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("id") long id);

    @Update("""
            UPDATE hub_search_index_task SET status = 'RUNNING', updated_at = now()
             WHERE id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int markRunning(@Param("id") long id);

    @Update("""
            UPDATE hub_search_index_task
               SET status = 'SUCCEEDED', doc_count = #{docCount}, error_msg = NULL, updated_at = now()
             WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0
            """)
    int markSucceeded(@Param("id") long id, @Param("docCount") long docCount);

    @Update("""
            UPDATE hub_search_index_task
               SET status = 'FAILED', error_msg = #{errorMsg}, updated_at = now()
             WHERE id = #{id} AND status IN ('PENDING', 'RUNNING') AND deleted = 0
            """)
    int markFailed(@Param("id") long id, @Param("errorMsg") String errorMsg);
}
