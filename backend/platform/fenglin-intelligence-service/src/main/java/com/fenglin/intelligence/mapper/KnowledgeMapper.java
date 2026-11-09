package com.fenglin.intelligence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * H1 知识域数据访问：知识库/ACL/数据源/文档/分块/索引任务/图谱/检索日志。
 */
@Mapper
public interface KnowledgeMapper {

    @Insert("INSERT INTO ai_knowledge_base (id, tenant_id, code, name, description, scope_type, default_sec_level, "
            + "embedding_model, vector_collection, es_index, status, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{description}, #{scopeType}, #{secLevel}, "
            + "#{embeddingModel}, #{collection}, #{esIndex}, 'ENABLED', #{userId})")
    void insertKnowledgeBase(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                             @Param("name") String name, @Param("description") String description,
                             @Param("scopeType") String scopeType, @Param("secLevel") int secLevel,
                             @Param("embeddingModel") String embeddingModel, @Param("collection") String collection,
                             @Param("esIndex") String esIndex, @Param("userId") long userId);

    @Select("SELECT id, uuid, code, name, description, scope_type, default_sec_level, embedding_model, "
            + "vector_collection, es_index, status, created_at, updated_at FROM ai_knowledge_base "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listKnowledgeBases(@Param("tenantId") long tenantId,
                                                 @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_knowledge_base WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countKnowledgeBases(@Param("tenantId") long tenantId);

    @Select("SELECT id, uuid, code, name, description, scope_type, default_sec_level, embedding_model, "
            + "vector_collection, es_index, status, created_at FROM ai_knowledge_base "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findKnowledgeBase(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT count(1) FROM ai_knowledge_base WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0")
    long countKnowledgeBaseCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("INSERT INTO ai_knowledge_acl (id, tenant_id, knowledge_base_id, subject_type, subject_id, permission, "
            + "sec_level_max, created_by) VALUES (#{id}, #{tenantId}, #{kbId}, #{subjectType}, #{subjectId}, "
            + "#{permission}, #{secLevelMax}, #{userId})")
    void insertAcl(@Param("id") long id, @Param("tenantId") long tenantId, @Param("kbId") long kbId,
                   @Param("subjectType") String subjectType, @Param("subjectId") Long subjectId,
                   @Param("permission") String permission, @Param("secLevelMax") int secLevelMax,
                   @Param("userId") long userId);

    @Insert("INSERT INTO ai_data_source (id, tenant_id, code, name, category, connector_type, endpoint, config_json, "
            + "sync_mode, sync_cron, status, created_by) VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{category}, "
            + "#{connectorType}, #{endpoint}, CAST(#{configJson} AS jsonb), #{syncMode}, #{syncCron}, 'ENABLED', #{userId})")
    void insertDataSource(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                          @Param("name") String name, @Param("category") String category,
                          @Param("connectorType") String connectorType, @Param("endpoint") String endpoint,
                          @Param("configJson") String configJson, @Param("syncMode") String syncMode,
                          @Param("syncCron") String syncCron, @Param("userId") long userId);

    @Insert("INSERT INTO ai_data_source_credential (id, tenant_id, data_source_id, credential_cipher, credential_hash, "
            + "key_version, created_by) VALUES (#{id}, #{tenantId}, #{dataSourceId}, #{cipher}, #{hash}, 1, #{userId})")
    void insertDataSourceCredential(@Param("id") long id, @Param("tenantId") long tenantId,
                                    @Param("dataSourceId") long dataSourceId, @Param("cipher") String cipher,
                                    @Param("hash") String hash, @Param("userId") long userId);

    @Select("SELECT count(1) FROM ai_data_source WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0")
    long countDataSourceCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("SELECT d.id, d.code, d.name, d.category, d.connector_type, d.endpoint, d.sync_mode, d.sync_cron, "
            + "d.status, d.created_at, CASE WHEN c.id IS NULL THEN 0 ELSE 1 END AS has_credential "
            + "FROM ai_data_source d LEFT JOIN ai_data_source_credential c "
            + "ON c.data_source_id = d.id AND c.deleted = 0 "
            + "WHERE d.tenant_id = #{tenantId} AND d.deleted = 0 ORDER BY d.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listDataSources(@Param("tenantId") long tenantId,
                                              @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_data_source WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countDataSources(@Param("tenantId") long tenantId);

    @Select("SELECT id, code, name, category, status FROM ai_data_source "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findDataSource(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("INSERT INTO ai_document (id, tenant_id, knowledge_base_id, data_source_id, title, source_uri, file_id, "
            + "doc_type, size_bytes, sec_level, owner_dept_id, tags, review_status, index_status, checksum, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{kbId}, #{dataSourceId}, #{title}, #{sourceUri}, #{fileId}, #{docType}, "
            + "#{sizeBytes}, #{secLevel}, #{deptId}, #{tags}, 'DRAFT', 'PENDING', #{checksum}, #{userId})")
    void insertDocument(@Param("id") long id, @Param("tenantId") long tenantId, @Param("kbId") long kbId,
                        @Param("dataSourceId") Long dataSourceId, @Param("title") String title,
                        @Param("sourceUri") String sourceUri, @Param("fileId") String fileId,
                        @Param("docType") String docType, @Param("sizeBytes") Long sizeBytes,
                        @Param("secLevel") int secLevel, @Param("deptId") Long deptId, @Param("tags") String tags,
                        @Param("checksum") String checksum, @Param("userId") long userId);

    @Select("SELECT id, uuid, knowledge_base_id, data_source_id, title, source_uri, file_id, doc_type, size_bytes, "
            + "sec_level, owner_dept_id, tags, review_status, index_status, chunk_count, checksum, published_at, "
            + "created_at, updated_at FROM ai_document "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findDocument(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT id, knowledge_base_id, title, doc_type, sec_level, review_status, index_status, chunk_count, "
            + "created_at FROM ai_document WHERE tenant_id = #{tenantId} AND knowledge_base_id = #{kbId} "
            + "AND deleted = 0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listDocuments(@Param("tenantId") long tenantId, @Param("kbId") long kbId,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_document WHERE tenant_id = #{tenantId} AND knowledge_base_id = #{kbId} "
            + "AND deleted = 0")
    long countDocuments(@Param("tenantId") long tenantId, @Param("kbId") long kbId);

    @Update("UPDATE ai_document SET review_status = #{status}, "
            + "published_at = CASE WHEN #{status} = 'APPROVED' THEN now() ELSE published_at END, "
            + "updated_by = #{userId} WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int updateDocumentReview(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("status") String status, @Param("userId") long userId);

    @Update("UPDATE ai_document SET index_status = #{status}, chunk_count = #{chunks}, updated_by = #{userId} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int updateDocumentIndex(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("status") String status, @Param("chunks") int chunks, @Param("userId") long userId);

    @Insert("INSERT INTO ai_document_chunk (id, tenant_id, document_id, knowledge_base_id, seq_no, content, "
            + "token_count, page_no, section_path, char_offset, vector_id, es_doc_id, sec_level, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{docId}, #{kbId}, #{seqNo}, #{content}, #{tokens}, #{pageNo}, "
            + "#{sectionPath}, #{charOffset}, CAST(#{id} AS varchar), CAST(#{id} AS varchar), #{secLevel}, #{userId})")
    void insertChunk(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") long docId,
                     @Param("kbId") long kbId, @Param("seqNo") int seqNo, @Param("content") String content,
                     @Param("tokens") int tokens, @Param("pageNo") Integer pageNo,
                     @Param("sectionPath") String sectionPath, @Param("charOffset") int charOffset,
                     @Param("secLevel") int secLevel, @Param("userId") long userId);

    @Select("SELECT id, document_id, knowledge_base_id, seq_no, content, token_count, page_no, section_path, "
            + "char_offset, vector_id, es_doc_id, sec_level FROM ai_document_chunk "
            + "WHERE tenant_id = #{tenantId} AND document_id = #{docId} AND deleted = 0 ORDER BY seq_no")
    List<Map<String, Object>> listChunks(@Param("tenantId") long tenantId, @Param("docId") long docId);

    @Update("UPDATE ai_document_chunk SET deleted = 1, updated_by = #{userId} "
            + "WHERE tenant_id = #{tenantId} AND document_id = #{docId} AND deleted = 0")
    int deleteChunks(@Param("tenantId") long tenantId, @Param("docId") long docId, @Param("userId") long userId);

    @Insert("INSERT INTO ai_index_task (id, tenant_id, document_id, knowledge_base_id, task_type, status, progress, "
            + "priority, created_by) VALUES (#{id}, #{tenantId}, #{docId}, #{kbId}, #{taskType}, 'PENDING', 0, 5, #{userId})")
    void insertIndexTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") long docId,
                         @Param("kbId") long kbId, @Param("taskType") String taskType, @Param("userId") long userId);

    @Update("UPDATE ai_index_task SET status = #{status}, progress = #{progress}, error_msg = #{error}, "
            + "started_at = CASE WHEN #{status} = 'RUNNING' AND started_at IS NULL THEN now() ELSE started_at END, "
            + "finished_at = CASE WHEN #{status} IN ('SUCCESS','FAILED') THEN now() ELSE finished_at END, "
            + "updated_by = #{userId} WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateIndexTask(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                        @Param("progress") int progress, @Param("error") String error, @Param("userId") long userId);

    @Select("SELECT id, document_id, knowledge_base_id, task_type, status, progress, retry_count, error_msg, "
            + "started_at, finished_at, created_at FROM ai_index_task WHERE tenant_id = #{tenantId} "
            + "AND document_id = #{docId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    Map<String, Object> findLatestIndexTask(@Param("tenantId") long tenantId, @Param("docId") long docId);

    @Select("SELECT c.id AS chunk_id, c.document_id, c.knowledge_base_id, c.seq_no, c.content, c.page_no, "
            + "c.section_path, c.sec_level, d.title AS document_title, d.doc_type, "
            + "public.similarity(c.content, CAST(#{query} AS text)) AS score FROM ai_document_chunk c "
            + "JOIN ai_document d ON d.id = c.document_id AND d.deleted = 0 "
            + "WHERE c.tenant_id = #{tenantId} AND c.deleted = 0 AND d.index_status = 'INDEXED' "
            + "AND c.sec_level <= #{secLevel} "
            + "AND (CAST(#{kbCsv} AS varchar) IS NULL "
            + "     OR c.knowledge_base_id = ANY(string_to_array(#{kbCsv}, ',')::bigint[])) "
            + "AND (c.content ILIKE '%' || #{query} || '%' OR d.title ILIKE '%' || #{query} || '%') "
            + "ORDER BY score DESC, c.id LIMIT #{limit}")
    List<Map<String, Object>> searchChunks(@Param("tenantId") long tenantId, @Param("kbCsv") String kbCsv,
                                           @Param("query") String query, @Param("secLevel") int secLevel,
                                           @Param("limit") int limit);

    @Select("SELECT count(1) FROM ai_document_chunk WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countChunks(@Param("tenantId") long tenantId);

    @Insert("INSERT INTO ai_search_log (id, tenant_id, user_id, query_text, query_type, knowledge_base_ids, "
            + "result_count, top_score, latency_ms, client_type, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{userId}, #{query}, #{queryType}, "
            + "CASE WHEN CAST(#{kbCsv} AS varchar) IS NULL THEN NULL "
            + "ELSE string_to_array(#{kbCsv}, ',')::bigint[] END, "
            + "#{resultCount}, #{topScore}, #{latency}, #{clientType}, #{userId})")
    void insertSearchLog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("userId") long userId,
                         @Param("query") String query, @Param("queryType") String queryType,
                         @Param("kbCsv") String kbCsv, @Param("resultCount") int resultCount,
                         @Param("topScore") BigDecimal topScore, @Param("latency") int latency,
                         @Param("clientType") String clientType);

    @Select("SELECT query_text AS query, count(1) AS total, "
            + "count(1) FILTER (WHERE result_count = 0) AS zero_result, "
            + "COALESCE(avg(latency_ms), 0)::int AS avg_latency FROM ai_search_log "
            + "WHERE tenant_id = #{tenantId} GROUP BY query_text ORDER BY total DESC LIMIT #{limit}")
    List<Map<String, Object>> topQueries(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Select("SELECT count(1) AS total, COALESCE(avg(latency_ms), 0)::int AS avg_latency, "
            + "count(1) FILTER (WHERE result_count = 0) AS zero_result FROM ai_search_log "
            + "WHERE tenant_id = #{tenantId}")
    Map<String, Object> searchSummary(@Param("tenantId") long tenantId);

    @Insert("INSERT INTO ai_kg_entity (id, tenant_id, knowledge_base_id, entity_type, name, normalized_name, "
            + "aliases, source_document_id, confidence, created_by) VALUES (#{id}, #{tenantId}, #{kbId}, #{type}, "
            + "#{name}, #{normalized}, #{aliases}, #{docId}, #{confidence}, #{userId})")
    void insertKgEntity(@Param("id") long id, @Param("tenantId") long tenantId, @Param("kbId") long kbId,
                        @Param("type") String type, @Param("name") String name, @Param("normalized") String normalized,
                        @Param("aliases") String aliases, @Param("docId") Long docId,
                        @Param("confidence") BigDecimal confidence, @Param("userId") long userId);

    @Insert("INSERT INTO ai_kg_relation (id, tenant_id, knowledge_base_id, source_entity_id, target_entity_id, "
            + "relation_type, weight, source_document_id, confidence, created_by) VALUES (#{id}, #{tenantId}, "
            + "#{kbId}, #{sourceId}, #{targetId}, #{type}, #{weight}, #{docId}, #{confidence}, #{userId})")
    void insertKgRelation(@Param("id") long id, @Param("tenantId") long tenantId, @Param("kbId") long kbId,
                          @Param("sourceId") long sourceId, @Param("targetId") long targetId,
                          @Param("type") String type, @Param("weight") BigDecimal weight,
                          @Param("docId") Long docId, @Param("confidence") BigDecimal confidence,
                          @Param("userId") long userId);

    @Select("SELECT id, knowledge_base_id, entity_type, name, normalized_name, aliases, confidence, created_at "
            + "FROM ai_kg_entity WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND (CAST(#{kbId} AS bigint) IS NULL OR knowledge_base_id = #{kbId}) "
            + "AND (CAST(#{keyword} AS varchar) IS NULL OR name ILIKE '%' || #{keyword} || '%') "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> listKgEntities(@Param("tenantId") long tenantId, @Param("kbId") Long kbId,
                                             @Param("keyword") String keyword, @Param("limit") int limit);

    @Select("WITH RECURSIVE path AS ("
            + " SELECT r.source_entity_id, r.target_entity_id, r.relation_type, "
            + "        ARRAY[r.source_entity_id, r.target_entity_id] AS nodes, 1 AS depth "
            + " FROM ai_kg_relation r WHERE r.tenant_id = #{tenantId} AND r.deleted = 0 AND r.source_entity_id = #{fromId} "
            + " UNION ALL "
            + " SELECT r.source_entity_id, r.target_entity_id, r.relation_type, "
            + "        p.nodes || r.target_entity_id, p.depth + 1 "
            + " FROM ai_kg_relation r JOIN path p ON r.source_entity_id = p.target_entity_id "
            + " WHERE r.tenant_id = #{tenantId} AND r.deleted = 0 AND p.depth < #{maxDepth} "
            + "   AND NOT r.target_entity_id = ANY(p.nodes)) "
            + "SELECT nodes, depth, relation_type FROM path WHERE target_entity_id = #{toId} "
            + "ORDER BY depth LIMIT 1")
    Map<String, Object> findKgPath(@Param("tenantId") long tenantId, @Param("fromId") long fromId,
                                   @Param("toId") long toId, @Param("maxDepth") int maxDepth);
}
