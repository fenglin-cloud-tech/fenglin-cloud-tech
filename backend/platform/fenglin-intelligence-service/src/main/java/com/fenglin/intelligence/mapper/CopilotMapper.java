package com.fenglin.intelligence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * H2 Copilot/Agent 数据访问。
 */
@Mapper
public interface CopilotMapper {

    @Insert("INSERT INTO ai_conversation (id, tenant_id, title, user_id, type, kb_ids, model_code, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{title}, #{userId}, #{type}, "
            + "CASE WHEN CAST(#{kbCsv} AS varchar) IS NULL THEN NULL "
            + "ELSE string_to_array(#{kbCsv}, ',')::bigint[] END, "
            + "#{model}, #{userId})")
    void insertConversation(@Param("id") long id, @Param("tenantId") long tenantId, @Param("title") String title,
                            @Param("userId") long userId, @Param("type") String type,
                            @Param("kbCsv") String kbCsv, @Param("model") String model);

    @Select("SELECT id, uuid, title, user_id, type, kb_ids, model_code, message_count, status, pinned, created_at, "
            + "updated_at FROM ai_conversation WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND deleted = 0 ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listConversations(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                                @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_conversation WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = 0")
    long countConversations(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("SELECT id, uuid, title, user_id, type, kb_ids, model_code, message_count, status FROM ai_conversation "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findConversation(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT array_to_string(kb_ids, ',') FROM ai_conversation "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    String findConversationKbCsv(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE ai_conversation SET message_count = message_count + 2, updated_by = #{userId} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int increaseMessageCount(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Insert("INSERT INTO ai_message (id, tenant_id, conversation_id, role, content, content_type, token_in, "
            + "token_out, model_code, latency_ms, ttft_ms, guardrail_hits, created_by) VALUES (#{id}, #{tenantId}, "
            + "#{conversationId}, #{role}, #{content}, #{contentType}, #{tokenIn}, #{tokenOut}, #{model}, "
            + "#{latency}, #{ttft}, CAST(#{guardrailHits} AS jsonb), #{userId})")
    void insertMessage(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("conversationId") long conversationId, @Param("role") String role,
                       @Param("content") String content, @Param("contentType") String contentType,
                       @Param("tokenIn") Integer tokenIn, @Param("tokenOut") Integer tokenOut,
                       @Param("model") String model, @Param("latency") Integer latency,
                       @Param("ttft") Integer ttft, @Param("guardrailHits") String guardrailHits,
                       @Param("userId") long userId);

    @Select("SELECT id, conversation_id, role, content, content_type, token_in, token_out, model_code, latency_ms, "
            + "created_at FROM ai_message WHERE tenant_id = #{tenantId} AND conversation_id = #{conversationId} "
            + "ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listMessages(@Param("tenantId") long tenantId,
                                           @Param("conversationId") long conversationId,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_message WHERE tenant_id = #{tenantId} AND conversation_id = #{conversationId}")
    long countMessages(@Param("tenantId") long tenantId, @Param("conversationId") long conversationId);

    @Select("SELECT id, conversation_id, role, content, created_at FROM ai_message "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} LIMIT 1")
    Map<String, Object> findMessage(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("INSERT INTO ai_message_citation (id, tenant_id, message_id, document_id, chunk_id, score, snippet, "
            + "page_no, created_by) VALUES (#{id}, #{tenantId}, #{messageId}, #{documentId}, #{chunkId}, #{score}, "
            + "#{snippet}, #{pageNo}, #{userId})")
    void insertCitation(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("messageId") long messageId, @Param("documentId") long documentId,
                        @Param("chunkId") long chunkId, @Param("score") java.math.BigDecimal score,
                        @Param("snippet") String snippet, @Param("pageNo") Integer pageNo,
                        @Param("userId") long userId);

    @Select("SELECT c.id, c.document_id, c.chunk_id, c.score, c.snippet, c.page_no, d.title AS document_title "
            + "FROM ai_message_citation c LEFT JOIN ai_document d ON d.id = c.document_id "
            + "WHERE c.tenant_id = #{tenantId} AND c.message_id = #{messageId} ORDER BY c.score DESC")
    List<Map<String, Object>> listCitations(@Param("tenantId") long tenantId, @Param("messageId") long messageId);

    @Insert("INSERT INTO ai_qa_feedback (id, tenant_id, message_id, user_id, feedback_type, rating, comment, "
            + "corrected_doc_id, created_by) VALUES (#{id}, #{tenantId}, #{messageId}, #{userId}, #{type}, #{rating}, "
            + "#{comment}, #{correctedDocId}, #{userId})")
    void insertFeedback(@Param("id") long id, @Param("tenantId") long tenantId, @Param("messageId") long messageId,
                        @Param("userId") long userId, @Param("type") String type, @Param("rating") Integer rating,
                        @Param("comment") String comment, @Param("correctedDocId") Long correctedDocId);

    @Insert("INSERT INTO ai_agent_definition (id, tenant_id, code, name, description, nodes, tools, model_code, "
            + "version_no, status, max_steps, created_by) VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{description}, "
            + "CAST(#{nodes} AS jsonb), CAST(#{tools} AS jsonb), #{model}, #{version}, #{status}, #{maxSteps}, #{userId})")
    void insertAgent(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                     @Param("name") String name, @Param("description") String description,
                     @Param("nodes") String nodes, @Param("tools") String tools, @Param("model") String model,
                     @Param("version") int version, @Param("status") String status,
                     @Param("maxSteps") int maxSteps, @Param("userId") long userId);

    @Select("SELECT id, code, name, description, nodes, tools, model_code, version_no, status, max_steps, created_at "
            + "FROM ai_agent_definition WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 "
            + "ORDER BY version_no DESC LIMIT 1")
    Map<String, Object> findAgentByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("SELECT id, code, name, description, model_code, version_no, status, max_steps, created_at "
            + "FROM ai_agent_definition WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "ORDER BY code, version_no DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listAgents(@Param("tenantId") long tenantId,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM ai_agent_definition WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countAgents(@Param("tenantId") long tenantId);

    @Insert("INSERT INTO ai_agent_execution (id, tenant_id, agent_id, conversation_id, trigger_user_id, status, "
            + "input_json, output_json, steps, step_count, token_total, trace_id, created_by) VALUES (#{id}, #{tenantId}, "
            + "#{agentId}, #{conversationId}, #{userId}, #{status}, CAST(#{input} AS jsonb), CAST(#{output} AS jsonb), "
            + "CAST(#{steps} AS jsonb), #{stepCount}, #{tokens}, #{traceId}, #{userId})")
    void insertExecution(@Param("id") long id, @Param("tenantId") long tenantId, @Param("agentId") long agentId,
                         @Param("conversationId") Long conversationId, @Param("userId") long userId,
                         @Param("status") String status, @Param("input") String input,
                         @Param("output") String output, @Param("steps") String steps,
                         @Param("stepCount") int stepCount, @Param("tokens") Integer tokens,
                         @Param("traceId") String traceId);

    @Select("SELECT id, agent_id, conversation_id, status, step_count, token_total, trace_id, created_at "
            + "FROM ai_agent_execution WHERE tenant_id = #{tenantId} AND id = #{id}")
    Map<String, Object> findExecution(@Param("tenantId") long tenantId, @Param("id") long id);
}
