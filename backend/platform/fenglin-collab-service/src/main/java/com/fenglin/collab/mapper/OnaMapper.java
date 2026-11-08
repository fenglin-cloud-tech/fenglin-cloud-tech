package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 组织网络分析（ONA）查询 Mapper。图谱与指标由离线快照任务写入，本域只读展示。
 */
@Mapper
public interface OnaMapper {

    @Select("""
            SELECT CAST(id AS varchar) AS "graphId", name AS "name", scope_type AS "scopeType",
                   scope_dept_id AS "scopeDeptId", to_char(period_start, 'YYYY-MM-DD') AS "periodStart",
                   to_char(period_end, 'YYYY-MM-DD') AS "periodEnd", node_count AS "nodeCount",
                   edge_count AS "edgeCount", status AS "status"
            FROM collab_ona_graph
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{graphId,jdbcType=VARCHAR} AS varchar) IS NULL
                   OR id = CAST(#{graphId,jdbcType=VARCHAR} AS uuid))
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Map<String, Object> findGraph(@Param("tenantId") long tenantId, @Param("graphId") String graphId);

    @Select("""
            SELECT source_type AS "sourceType", source_id AS "sourceId", target_type AS "targetType",
                   target_id AS "targetId", weight AS "weight", cross_dept AS "crossDept"
            FROM collab_ona_edge
            WHERE tenant_id = #{tenantId} AND graph_id = CAST(#{graphId} AS uuid) AND deleted = 0
            ORDER BY weight DESC
            LIMIT 5000
            """)
    List<Map<String, Object>> listEdges(@Param("tenantId") long tenantId, @Param("graphId") String graphId);

    @Select("""
            SELECT CAST(id AS varchar) AS "snapshotId", period_type AS "periodType", period_key AS "periodKey",
                   network_density AS "networkDensity", avg_degree AS "avgDegree",
                   cross_dept_ratio AS "crossDeptRatio", key_connector_count AS "keyConnectorCount",
                   silo_count AS "siloCount",
                   (EXTRACT(EPOCH FROM generated_at) * 1000)::bigint AS "generatedAt"
            FROM collab_ona_snapshot
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodType,jdbcType=VARCHAR} AS varchar) IS NULL OR period_type = #{periodType})
            ORDER BY generated_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listSnapshots(@Param("tenantId") long tenantId,
                                            @Param("periodType") String periodType,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_ona_snapshot
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodType,jdbcType=VARCHAR} AS varchar) IS NULL OR period_type = #{periodType})
            """)
    long countSnapshots(@Param("tenantId") long tenantId, @Param("periodType") String periodType);

    @Select("""
            SELECT CAST(id AS varchar) AS "snapshotId", period_type AS "periodType", period_key AS "periodKey",
                   CAST(graph_id AS varchar) AS "graphId", network_density AS "networkDensity",
                   avg_degree AS "avgDegree", cross_dept_ratio AS "crossDeptRatio",
                   key_connector_count AS "keyConnectorCount", silo_count AS "siloCount",
                   (EXTRACT(EPOCH FROM generated_at) * 1000)::bigint AS "generatedAt"
            FROM collab_ona_snapshot
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY generated_at DESC
            LIMIT 1
            """)
    Map<String, Object> findLatestSnapshot(@Param("tenantId") long tenantId);

    @Select("""
            SELECT node_type AS "nodeType", node_id AS "nodeId", degree_centrality AS "degreeCentrality",
                   betweenness AS "betweenness", closeness AS "closeness",
                   clustering_coef AS "clusteringCoef", structural_hole AS "structuralHole",
                   is_key_connector AS "keyConnector", cross_dept_ratio AS "crossDeptRatio"
            FROM collab_ona_metric
            WHERE tenant_id = #{tenantId} AND snapshot_id = CAST(#{snapshotId} AS uuid)
              AND node_type = #{nodeType} AND node_id = #{nodeId} AND deleted = 0
            """)
    Map<String, Object> findMetric(@Param("tenantId") long tenantId,
                                   @Param("snapshotId") String snapshotId,
                                   @Param("nodeType") String nodeType,
                                   @Param("nodeId") long nodeId);

    @Select("""
            SELECT node_type AS "nodeType", node_id AS "nodeId", degree_centrality AS "degreeCentrality",
                   betweenness AS "betweenness", closeness AS "closeness",
                   clustering_coef AS "clusteringCoef", structural_hole AS "structuralHole",
                   is_key_connector AS "keyConnector", cross_dept_ratio AS "crossDeptRatio"
            FROM collab_ona_metric
            WHERE tenant_id = #{tenantId} AND snapshot_id = CAST(#{snapshotId} AS uuid)
              AND node_type = 'USER' AND node_id = #{userId} AND deleted = 0
            """)
    Map<String, Object> findUserMetric(@Param("tenantId") long tenantId,
                                       @Param("snapshotId") String snapshotId,
                                       @Param("userId") long userId);

    @Select("""
            SELECT m.node_id AS "nodeId", m.node_type AS "nodeType",
                   m.degree_centrality AS "degreeCentrality", m.cross_dept_ratio AS "crossDeptRatio",
                   (SELECT count(1) FROM collab_ona_edge e
                    WHERE e.tenant_id = m.tenant_id AND e.graph_id = g.graph_id AND e.deleted = 0
                      AND ((e.source_type = m.node_type AND e.source_id = m.node_id)
                           OR (e.target_type = m.node_type AND e.target_id = m.node_id))) AS "connections"
            FROM collab_ona_metric m
            JOIN collab_ona_snapshot g ON g.id = m.snapshot_id AND g.tenant_id = m.tenant_id
            WHERE m.tenant_id = #{tenantId} AND m.snapshot_id = CAST(#{snapshotId} AS uuid)
              AND m.is_key_connector = 1 AND m.deleted = 0
            ORDER BY m.degree_centrality DESC NULLS LAST
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listKeyConnectors(@Param("tenantId") long tenantId,
                                                @Param("snapshotId") String snapshotId,
                                                @Param("limit") int limit);

    @Select("""
            SELECT e.source_type AS "sourceType", e.source_id AS "sourceId", e.target_type AS "targetType",
                   e.target_id AS "targetId", e.weight AS "weight", e.cross_dept AS "crossDept"
            FROM collab_ona_edge e
            WHERE e.tenant_id = #{tenantId} AND e.graph_id = CAST(#{graphId} AS uuid) AND e.deleted = 0
              AND e.source_type = 'USER' AND e.target_type = 'USER'
              AND (e.source_id = #{userId} OR e.target_id = #{userId})
            ORDER BY e.weight DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listUserEdges(@Param("tenantId") long tenantId,
                                            @Param("graphId") String graphId,
                                            @Param("userId") long userId,
                                            @Param("limit") int limit);

    @Select("""
            SELECT e.source_id AS "sourceDeptId", e.target_id AS "targetDeptId",
                   count(1) AS "edgeCount", sum(e.weight) AS "weight"
            FROM collab_ona_edge e
            WHERE e.tenant_id = #{tenantId} AND e.graph_id = CAST(#{graphId} AS uuid) AND e.deleted = 0
              AND e.source_type = 'DEPT' AND e.target_type = 'DEPT'
            GROUP BY e.source_id, e.target_id
            """)
    List<Map<String, Object>> listDeptLinks(@Param("tenantId") long tenantId,
                                            @Param("graphId") String graphId);

    @Select("""
            SELECT s.period_key AS "periodKey", coalesce(s.avg_degree, 0) AS "degree"
            FROM collab_ona_snapshot s
            WHERE s.tenant_id = #{tenantId} AND s.deleted = 0
            ORDER BY s.generated_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listUserTrend(@Param("tenantId") long tenantId,
                                            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT weight_im AS "weightIm", weight_doc AS "weightDoc", weight_meeting AS "weightMeeting",
                   weight_approval AS "weightApproval", weight_okr AS "weightOkr",
                   anonymity_threshold AS "anonymityThreshold",
                   key_connector_top_ratio AS "keyConnectorTopRatio",
                   profile_visible_scope AS "profileVisibleScope"
            FROM collab_ona_config
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> findConfig(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO collab_ona_config
                (id, tenant_id, weight_im, weight_doc, weight_meeting, weight_approval, weight_okr,
                 anonymity_threshold, key_connector_top_ratio, profile_visible_scope, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{weightIm}, #{weightDoc}, #{weightMeeting}, #{weightApproval},
                    #{weightOkr}, #{anonymityThreshold}, #{topRatio}, #{profileVisibleScope}, #{userId}, #{userId})
            ON CONFLICT (tenant_id) DO UPDATE
            SET weight_im = EXCLUDED.weight_im, weight_doc = EXCLUDED.weight_doc,
                weight_meeting = EXCLUDED.weight_meeting, weight_approval = EXCLUDED.weight_approval,
                weight_okr = EXCLUDED.weight_okr, anonymity_threshold = EXCLUDED.anonymity_threshold,
                key_connector_top_ratio = EXCLUDED.key_connector_top_ratio,
                profile_visible_scope = EXCLUDED.profile_visible_scope,
                updated_by = EXCLUDED.updated_by, updated_at = now(), deleted = 0
            """)
    void upsertConfig(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("weightIm") BigDecimal weightIm, @Param("weightDoc") BigDecimal weightDoc,
                      @Param("weightMeeting") BigDecimal weightMeeting,
                      @Param("weightApproval") BigDecimal weightApproval,
                      @Param("weightOkr") BigDecimal weightOkr,
                      @Param("anonymityThreshold") int anonymityThreshold,
                      @Param("topRatio") BigDecimal topRatio,
                      @Param("profileVisibleScope") String profileVisibleScope,
                      @Param("userId") long userId);

    // ---------- 会议效率 ----------

    @Select("""
            SELECT count(1) AS "meetingCount",
                   coalesce(avg(EXTRACT(EPOCH FROM (m.end_at - m.start_at)) / 60.0), 0) AS "avgMinutes"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.status = 'ENDED'
              AND m.start_at >= #{startAt} AND m.start_at < #{endAt}
            """)
    Map<String, Object> meetingStats(@Param("tenantId") long tenantId,
                                     @Param("startAt") OffsetDateTime startAt,
                                     @Param("endAt") OffsetDateTime endAt);

    @Select("""
            SELECT count(1) AS "decided"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.status = 'ENDED'
              AND m.start_at >= #{startAt} AND m.start_at < #{endAt}
              AND EXISTS (SELECT 1 FROM collab_meeting_minutes mi
                          WHERE mi.tenant_id = m.tenant_id AND mi.meeting_id = m.id AND mi.deleted = 0
                            AND mi.status = 'PUBLISHED' AND mi.summary IS NOT NULL AND mi.summary <> '')
            """)
    long decidedMeetings(@Param("tenantId") long tenantId,
                         @Param("startAt") OffsetDateTime startAt,
                         @Param("endAt") OffsetDateTime endAt);

    @Select("""
            SELECT count(1) AS "onTime"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.status = 'ENDED'
              AND m.start_at >= #{startAt} AND m.start_at < #{endAt}
              AND EXISTS (SELECT 1 FROM collab_meeting_checkin c
                          WHERE c.tenant_id = m.tenant_id AND c.meeting_id = m.id AND c.deleted = 0
                            AND c.checkin_at <= m.start_at + interval '5 minutes')
            """)
    long onTimeMeetings(@Param("tenantId") long tenantId,
                        @Param("startAt") OffsetDateTime startAt,
                        @Param("endAt") OffsetDateTime endAt);

    @Select("""
            SELECT count(1) AS "total", count(1) FILTER (WHERE a.status = 'DONE') AS "done"
            FROM collab_meeting_action a
            JOIN collab_meeting_minutes mi ON mi.id = a.minutes_id AND mi.tenant_id = a.tenant_id
            JOIN collab_meeting m ON m.id = mi.meeting_id AND m.tenant_id = mi.tenant_id
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
              AND m.deleted = 0 AND m.status = 'ENDED'
              AND m.start_at >= #{startAt} AND m.start_at < #{endAt}
            """)
    Map<String, Object> actionStats(@Param("tenantId") long tenantId,
                                    @Param("startAt") OffsetDateTime startAt,
                                    @Param("endAt") OffsetDateTime endAt);

    @Select("""
            SELECT coalesce(sum((SELECT count(1) FROM collab_meeting_checkin c
                                 WHERE c.tenant_id = m.tenant_id AND c.meeting_id = m.id AND c.deleted = 0)), 0)
                   AS "checkinTotal"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0 AND m.status = 'ENDED'
              AND m.start_at >= #{startAt} AND m.start_at < #{endAt}
            """)
    long checkinTotal(@Param("tenantId") long tenantId,
                      @Param("startAt") OffsetDateTime startAt,
                      @Param("endAt") OffsetDateTime endAt);
}
