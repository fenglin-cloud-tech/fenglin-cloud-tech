package com.fenglin.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 服务目录、SLA、知识库、满意度、资产、问题、变更、租户配置。
 */
@Mapper
public interface ConfigMapper {

    // ---------- 服务目录 ----------
    @Select("""
            SELECT id, name, parent_id, icon, sort_no, visible_scope::text AS visible_scope, status
            FROM svc_catalog
            WHERE deleted = 0 AND status = 'ACTIVE' AND (tenant_id = #{tenantId} OR tenant_id = 0)
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listCatalogs(@Param("tenantId") long tenantId);

    @Select("""
            <script>
            SELECT i.id, i.catalog_id, i.code, i.name, i.description, i.ticket_type, i.form_def_id,
                   i.default_group_id, i.sla_policy_id, i.need_approval, i.approval_flow_key,
                   i.visible_scope::text AS visible_scope, i.sort_no, i.status, c.name AS catalog_name
            FROM svc_catalog_item i LEFT JOIN svc_catalog c ON c.id = i.catalog_id
            WHERE i.deleted = 0 AND (i.tenant_id = #{tenantId} OR i.tenant_id = 0)
            <if test="catalogId != null">AND i.catalog_id = #{catalogId}</if>
            ORDER BY i.sort_no, i.id
            </script>
            """)
    List<Map<String, Object>> listItems(@Param("tenantId") long tenantId, @Param("catalogId") Long catalogId);

    @Select("""
            SELECT i.id, i.catalog_id, i.code, i.name, i.description, i.ticket_type, i.form_def_id,
                   i.default_group_id, i.sla_policy_id, i.need_approval, i.approval_flow_key,
                   i.visible_scope::text AS visible_scope, i.status, c.name AS catalog_name
            FROM svc_catalog_item i LEFT JOIN svc_catalog c ON c.id = i.catalog_id
            WHERE i.id = #{id} AND i.deleted = 0 AND (i.tenant_id = #{tenantId} OR i.tenant_id = 0)
            """)
    Map<String, Object> findItem(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO svc_catalog
                (id, tenant_id, name, parent_id, icon, sort_no, visible_scope, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{parentId}, #{icon}, #{sortNo}, #{visibleScope}::jsonb,
                    #{status}, #{userId}, #{userId})
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id,
                icon = EXCLUDED.icon, sort_no = EXCLUDED.sort_no, visible_scope = EXCLUDED.visible_scope,
                status = EXCLUDED.status, updated_by = EXCLUDED.updated_by, version = svc_catalog.version + 1
            """)
    void upsertCatalog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                       @Param("parentId") Long parentId, @Param("icon") String icon, @Param("sortNo") int sortNo,
                       @Param("visibleScope") String visibleScope, @Param("status") String status,
                       @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_catalog_item
                (id, tenant_id, catalog_id, code, name, description, ticket_type, sla_policy_id,
                 need_approval, approval_flow_key, visible_scope, sort_no, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{catalogId}, #{code}, #{name}, #{description}, #{ticketType}, #{slaPolicyId},
                    #{needApproval}, #{approvalFlowKey}, #{visibleScope}::jsonb, #{sortNo}, #{status}, #{userId}, #{userId})
            ON CONFLICT (id) DO UPDATE SET catalog_id = EXCLUDED.catalog_id, name = EXCLUDED.name,
                description = EXCLUDED.description, ticket_type = EXCLUDED.ticket_type,
                sla_policy_id = EXCLUDED.sla_policy_id, need_approval = EXCLUDED.need_approval,
                approval_flow_key = EXCLUDED.approval_flow_key, visible_scope = EXCLUDED.visible_scope,
                sort_no = EXCLUDED.sort_no, status = EXCLUDED.status, updated_by = EXCLUDED.updated_by,
                version = svc_catalog_item.version + 1
            """)
    void upsertCatalogItem(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("catalogId") long catalogId, @Param("code") String code,
                           @Param("name") String name, @Param("description") String description,
                           @Param("ticketType") String ticketType, @Param("slaPolicyId") Long slaPolicyId,
                           @Param("needApproval") boolean needApproval, @Param("approvalFlowKey") String approvalFlowKey,
                           @Param("visibleScope") String visibleScope, @Param("sortNo") int sortNo,
                           @Param("status") String status, @Param("userId") long userId);

    @Update("""
            UPDATE svc_catalog_item SET status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateItemStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("status") String status, @Param("userId") long userId);

    // ---------- SLA 策略 ----------
    @Select("""
            SELECT id, name, priority, ticket_type, catalog_item_id, calendar_id, response_minutes,
                   resolve_minutes, pause_on_status::text AS pause_on_status, warning_threshold,
                   escalation_matrix::text AS escalation_matrix, status
            FROM svc_sla_policy
            WHERE deleted = 0 AND (tenant_id = #{tenantId} OR tenant_id = 0)
            ORDER BY CASE WHEN tenant_id = #{tenantId} THEN 0 ELSE 1 END, id
            """)
    List<Map<String, Object>> listPolicies(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO svc_sla_policy
                (id, tenant_id, name, priority, ticket_type, catalog_item_id, calendar_id,
                 response_minutes, resolve_minutes, pause_on_status, warning_threshold, escalation_matrix,
                 status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{priority}, #{ticketType}, #{catalogItemId}, #{calendarId},
                    #{responseMinutes}, #{resolveMinutes}, #{pauseOnStatus}::jsonb, #{warningThreshold},
                    #{escalationMatrix}::jsonb, #{status}, #{userId}, #{userId})
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, priority = EXCLUDED.priority,
                ticket_type = EXCLUDED.ticket_type, catalog_item_id = EXCLUDED.catalog_item_id,
                calendar_id = EXCLUDED.calendar_id, response_minutes = EXCLUDED.response_minutes,
                resolve_minutes = EXCLUDED.resolve_minutes, pause_on_status = EXCLUDED.pause_on_status,
                warning_threshold = EXCLUDED.warning_threshold, escalation_matrix = EXCLUDED.escalation_matrix,
                status = EXCLUDED.status, updated_by = EXCLUDED.updated_by, version = svc_sla_policy.version + 1
            """)
    void upsertPolicy(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                      @Param("priority") String priority, @Param("ticketType") String ticketType,
                      @Param("catalogItemId") Long catalogItemId, @Param("calendarId") String calendarId,
                      @Param("responseMinutes") int responseMinutes, @Param("resolveMinutes") int resolveMinutes,
                      @Param("pauseOnStatus") String pauseOnStatus, @Param("warningThreshold") int warningThreshold,
                      @Param("escalationMatrix") String escalationMatrix, @Param("status") String status,
                      @Param("userId") long userId);

    // ---------- 知识库 ----------
    @Select("""
            SELECT id, name, parent_id, sort_no FROM svc_kb_category
            WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listKbCategories(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO svc_kb_category (id, tenant_id, name, parent_id, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{parentId}, #{sortNo}, #{userId})
            """)
    void insertKbCategory(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                          @Param("parentId") Long parentId, @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT a.id, a.category_id, a.title, a.summary, a.version_no, a.visibility, a.status,
                   a.author_id, a.published_at, a.view_count, a.helpful_count, a.created_at, a.updated_at
            FROM svc_kb_article a
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
            <if test="status != null">AND a.status = #{status}</if>
            <if test="categoryId != null">AND a.category_id = #{categoryId}</if>
            <if test="keyword != null">AND (a.title ILIKE '%' || #{keyword} || '%'
                 OR a.summary ILIKE '%' || #{keyword} || '%' OR a.content ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY a.updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listArticles(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_kb_article a
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
            <if test="status != null">AND a.status = #{status}</if>
            <if test="categoryId != null">AND a.category_id = #{categoryId}</if>
            <if test="keyword != null">AND (a.title ILIKE '%' || #{keyword} || '%'
                 OR a.summary ILIKE '%' || #{keyword} || '%' OR a.content ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long countArticles(@Param("tenantId") long tenantId, @Param("status") String status,
                       @Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    @Select("""
            SELECT a.id, a.category_id, a.title, a.summary, a.content, a.keywords::text AS keywords,
                   a.version_no, a.visibility, a.status, a.author_id, a.published_at,
                   a.view_count, a.helpful_count, a.created_at, a.updated_at
            FROM svc_kb_article a
            WHERE a.id = #{id} AND a.tenant_id = #{tenantId} AND a.deleted = 0
            """)
    Map<String, Object> findArticle(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO svc_kb_article
                (id, tenant_id, category_id, title, summary, content, keywords, version_no, visibility,
                 status, author_id, published_at, es_doc_id, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{categoryId}, #{title}, #{summary}, #{content}, #{keywords}::jsonb,
                    #{versionNo}, #{visibility}, #{status}, #{authorId}, #{publishedAt}, #{esDocId},
                    #{authorId}, #{authorId})
            """)
    void insertArticle(@Param("id") long id, @Param("tenantId") long tenantId, @Param("categoryId") Long categoryId,
                       @Param("title") String title, @Param("summary") String summary, @Param("content") String content,
                       @Param("keywords") String keywords, @Param("versionNo") int versionNo,
                       @Param("visibility") String visibility, @Param("status") String status,
                       @Param("authorId") long authorId, @Param("publishedAt") java.sql.Timestamp publishedAt,
                       @Param("esDocId") String esDocId);

    @Update("""
            UPDATE svc_kb_article SET view_count = view_count + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    void increaseView(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO svc_kb_feedback (id, tenant_id, article_id, user_id, helpful, reason, ticket_id, created_by)
            VALUES (#{id}, #{tenantId}, #{articleId}, #{userId}, #{helpful}, #{reason}, #{ticketId}, #{userId})
            """)
    void insertKbFeedback(@Param("id") long id, @Param("tenantId") long tenantId, @Param("articleId") long articleId,
                          @Param("userId") long userId, @Param("helpful") boolean helpful,
                          @Param("reason") String reason, @Param("ticketId") Long ticketId);

    @Update("""
            UPDATE svc_kb_article SET helpful_count = helpful_count + 1
            WHERE id = #{articleId} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    void increaseHelpful(@Param("tenantId") long tenantId, @Param("articleId") long articleId);

    @Select("""
            SELECT id, title, summary, COALESCE(author_id, 0) AS author_id, helpful_count
            FROM svc_kb_article
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 'PUBLISHED'
              AND (title ILIKE '%' || #{keyword} || '%' OR summary ILIKE '%' || #{keyword} || '%'
                   OR content ILIKE '%' || #{keyword} || '%')
            ORDER BY helpful_count DESC, view_count DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> suggestArticles(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                              @Param("limit") int limit);

    // ---------- 满意度 ----------
    @Select("""
            SELECT count(1) FROM svc_csat_survey
            WHERE tenant_id = #{tenantId} AND ticket_id = #{ticketId} AND deleted = 0
            """)
    long countCsat(@Param("tenantId") long tenantId, @Param("ticketId") long ticketId);

    @Insert("""
            INSERT INTO svc_csat_survey
                (id, tenant_id, ticket_id, score, tags, comment, respondent_id, channel, submitted_at, created_by)
            VALUES (#{id}, #{tenantId}, #{ticketId}, #{score}, #{tags}::jsonb, #{comment}, #{respondentId},
                    #{channel}, now(), #{respondentId})
            """)
    void insertCsat(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ticketId") long ticketId,
                    @Param("score") int score, @Param("tags") String tags, @Param("comment") String comment,
                    @Param("respondentId") long respondentId, @Param("channel") String channel);

    @Select("""
            SELECT count(1) AS total, COALESCE(avg(score), 0) AS avg_score,
                   count(1) FILTER (WHERE score >= 4) AS positive,
                   count(1) FILTER (WHERE score <= 2) AS negative
            FROM svc_csat_survey WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> csatReport(@Param("tenantId") long tenantId);

    @Select("""
            SELECT score, count(1) AS cnt FROM svc_csat_survey
            WHERE tenant_id = #{tenantId} AND deleted = 0 GROUP BY score ORDER BY score
            """)
    List<Map<String, Object>> csatDistribution(@Param("tenantId") long tenantId);

    // ---------- IT 资产 ----------
    @Select("""
            <script>
            SELECT id, asset_no, name, asset_type, owner_id, dept_id, status, borrow_due_at, ext_ref,
                   created_at, updated_at
            FROM svc_it_asset
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            <if test="assetType != null">AND asset_type = #{assetType}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listAssets(@Param("tenantId") long tenantId, @Param("status") String status,
                                         @Param("ownerId") Long ownerId, @Param("assetType") String assetType,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_it_asset
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            <if test="assetType != null">AND asset_type = #{assetType}</if>
            </script>
            """)
    long countAssets(@Param("tenantId") long tenantId, @Param("status") String status,
                     @Param("ownerId") Long ownerId, @Param("assetType") String assetType);

    @Select("""
            SELECT id, asset_no, name, asset_type, owner_id, dept_id, status, borrow_due_at, ext_ref
            FROM svc_it_asset WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findAsset(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO svc_it_asset
                (id, tenant_id, asset_no, name, asset_type, owner_id, dept_id, status, borrow_due_at, ext_ref, created_by)
            VALUES (#{id}, #{tenantId}, #{assetNo}, #{name}, #{assetType}, #{ownerId}, #{deptId}, #{status},
                    #{borrowDueAt}, #{extRef}, #{userId})
            """)
    void insertAsset(@Param("id") long id, @Param("tenantId") long tenantId, @Param("assetNo") String assetNo,
                     @Param("name") String name, @Param("assetType") String assetType,
                     @Param("ownerId") Long ownerId, @Param("deptId") Long deptId, @Param("status") String status,
                     @Param("borrowDueAt") java.sql.Timestamp borrowDueAt, @Param("extRef") String extRef,
                     @Param("userId") long userId);

    @Update("""
            UPDATE svc_it_asset SET status = #{status}, owner_id = #{ownerId}, borrow_due_at = #{borrowDueAt},
                   updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateAsset(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                    @Param("ownerId") Long ownerId, @Param("borrowDueAt") java.sql.Timestamp borrowDueAt,
                    @Param("userId") long userId);

    @Select("""
            <script>
            SELECT count(1) AS total, count(1) FILTER (WHERE status = 'BORROWED') AS borrowed,
                   count(1) FILTER (WHERE borrow_due_at IS NOT NULL AND borrow_due_at &lt; now()) AS overdue
            FROM svc_it_asset WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            </script>
            """)
    Map<String, Object> assetSummary(@Param("tenantId") long tenantId, @Param("ownerId") Long ownerId);

    // ---------- 问题 / 变更 ----------
    @Insert("""
            INSERT INTO svc_problem
                (id, tenant_id, problem_no, title, description, root_cause, workaround, known_error,
                 status, owner_id, created_by)
            VALUES (#{id}, #{tenantId}, #{problemNo}, #{title}, #{description}, #{rootCause}, #{workaround},
                    #{knownError}, #{status}, #{ownerId}, #{userId})
            """)
    void insertProblem(@Param("id") long id, @Param("tenantId") long tenantId, @Param("problemNo") String problemNo,
                       @Param("title") String title, @Param("description") String description,
                       @Param("rootCause") String rootCause, @Param("workaround") String workaround,
                       @Param("knownError") boolean knownError, @Param("status") String status,
                       @Param("ownerId") Long ownerId, @Param("userId") long userId);

    @Select("""
            SELECT id, problem_no, title, description, root_cause, workaround, known_error, status, owner_id,
                   created_at, updated_at
            FROM svc_problem WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findProblem(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, problem_no, title, description, root_cause, workaround, known_error, status, owner_id,
                   created_at, updated_at
            FROM svc_problem
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (title ILIKE '%' || #{keyword} || '%'
                 OR problem_no ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listProblems(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_problem
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (title ILIKE '%' || #{keyword} || '%'
                 OR problem_no ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long countProblems(@Param("tenantId") long tenantId, @Param("status") String status,
                       @Param("keyword") String keyword);

    @Update("""
            UPDATE svc_problem SET root_cause = COALESCE(#{rootCause}, root_cause),
                   workaround = COALESCE(#{workaround}, workaround), known_error = #{knownError},
                   status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateProblem(@Param("tenantId") long tenantId, @Param("id") long id, @Param("rootCause") String rootCause,
                      @Param("workaround") String workaround, @Param("knownError") boolean knownError,
                      @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO svc_change_request
                (id, tenant_id, change_no, title, change_type, plan, rollback_plan, risk_level,
                 planned_start, planned_end, approval_flow_key, status, owner_id, created_by)
            VALUES (#{id}, #{tenantId}, #{changeNo}, #{title}, #{changeType}, #{plan}, #{rollbackPlan}, #{riskLevel},
                    #{plannedStart}, #{plannedEnd}, #{approvalFlowKey}, #{status}, #{ownerId}, #{userId})
            """)
    void insertChange(@Param("id") long id, @Param("tenantId") long tenantId, @Param("changeNo") String changeNo,
                      @Param("title") String title, @Param("changeType") String changeType,
                      @Param("plan") String plan, @Param("rollbackPlan") String rollbackPlan,
                      @Param("riskLevel") String riskLevel, @Param("plannedStart") java.sql.Timestamp plannedStart,
                      @Param("plannedEnd") java.sql.Timestamp plannedEnd, @Param("approvalFlowKey") String approvalFlowKey,
                      @Param("status") String status, @Param("ownerId") Long ownerId, @Param("userId") long userId);

    @Select("""
            SELECT id, change_no, title, change_type, plan, rollback_plan, risk_level, planned_start, planned_end,
                   approval_flow_key, status, owner_id, created_at, updated_at
            FROM svc_change_request WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findChange(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, change_no, title, change_type, plan, rollback_plan, risk_level, planned_start, planned_end,
                   approval_flow_key, status, owner_id, created_at, updated_at
            FROM svc_change_request
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (title ILIKE '%' || #{keyword} || '%'
                 OR change_no ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listChanges(@Param("tenantId") long tenantId, @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM svc_change_request
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null">AND (title ILIKE '%' || #{keyword} || '%'
                 OR change_no ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long countChanges(@Param("tenantId") long tenantId, @Param("status") String status,
                      @Param("keyword") String keyword);

    @Update("""
            UPDATE svc_change_request SET status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0
            """)
    int updateChangeStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("status") String status, @Param("userId") long userId);

    // ---------- 租户配置 ----------
    @Insert("""
            INSERT INTO svc_tenant_config (id, tenant_id, config_type, config_key, config_json, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{configType}, #{configKey}, #{configJson}::jsonb, #{status}, #{userId}, #{userId})
            ON CONFLICT (tenant_id, config_type, config_key) WHERE deleted = 0
            DO UPDATE SET config_json = EXCLUDED.config_json, status = EXCLUDED.status,
                updated_by = EXCLUDED.updated_by, version = svc_tenant_config.version + 1
            """)
    void upsertConfig(@Param("id") long id, @Param("tenantId") long tenantId, @Param("configType") String configType,
                      @Param("configKey") String configKey, @Param("configJson") String configJson,
                      @Param("status") String status, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, config_type, config_key, config_json::text AS config_json, status, created_at, updated_at
            FROM svc_tenant_config
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="configType != null">AND config_type = #{configType}</if>
            ORDER BY config_type, config_key
            </script>
            """)
    List<Map<String, Object>> listConfigs(@Param("tenantId") long tenantId,
                                          @Param("configType") String configType);
}
