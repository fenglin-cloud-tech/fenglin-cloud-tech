package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * E1 看板与看板列 Mapper。
 */
@Mapper
public interface BoardMapper {

    @Insert("""
            INSERT INTO devops_board (id, tenant_id, product_id, name, board_type, sprint_id, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{name}, #{boardType}, #{sprintId,jdbcType=BIGINT}, #{userId})
            """)
    void insertBoard(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                     @Param("name") String name, @Param("boardType") String boardType,
                     @Param("sprintId") Long sprintId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_board_column
                (id, tenant_id, board_id, column_name, status_codes, wip_limit, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{boardId}, #{columnName}, CAST(#{statusCodes} AS jsonb),
                    #{wipLimit,jdbcType=INTEGER}, #{sortNo}, #{userId})
            """)
    void insertColumn(@Param("id") long id, @Param("tenantId") long tenantId, @Param("boardId") long boardId,
                      @Param("columnName") String columnName, @Param("statusCodes") String statusCodes,
                      @Param("wipLimit") Integer wipLimit, @Param("sortNo") int sortNo,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", name AS "name", board_type AS "boardType",
                   sprint_id AS "sprintId", created_at::text AS "createdAt"
            FROM devops_board
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findBoard(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT c.id AS "id", c.column_name AS "columnName", c.status_codes::text AS "statusCodes",
                   c.wip_limit AS "wipLimit", c.sort_no AS "sortNo",
                   (SELECT count(1) FROM devops_task t WHERE t.tenant_id = c.tenant_id
                      AND t.product_id = #{productId}
                      AND jsonb_exists(c.status_codes, t.status)
                      AND t.deleted = 0
                      AND (#{sprintId,jdbcType=BIGINT} IS NULL OR t.sprint_id = #{sprintId,jdbcType=BIGINT}))
                      AS "itemCount"
            FROM devops_board_column c
            WHERE c.tenant_id = #{tenantId} AND c.board_id = #{boardId} AND c.deleted = 0
            ORDER BY c.sort_no
            """)
    List<Map<String, Object>> listColumns(@Param("tenantId") long tenantId, @Param("boardId") long boardId,
                                          @Param("productId") long productId, @Param("sprintId") Long sprintId);

    @Select("""
            SELECT id AS "id", column_name AS "columnName", status_codes::text AS "statusCodes",
                   wip_limit AS "wipLimit", sort_no AS "sortNo"
            FROM devops_board_column
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findColumn(@Param("tenantId") long tenantId, @Param("id") long id);
}
