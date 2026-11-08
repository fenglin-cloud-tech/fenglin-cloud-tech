package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作台 / 待办 / 通知 Mapper。
 */
@Mapper
public interface WorkbenchMapper {

    @Select("""
            SELECT id AS "layoutId", layout_type AS "layoutType", CAST(widgets AS varchar) AS "widgets",
                   is_default AS "isDefault"
            FROM collab_workbench_layout
            WHERE tenant_id = #{tenantId} AND owner_id = #{userId} AND layout_type = 'PERSONAL' AND deleted = 0
            """)
    Map<String, Object> findPersonalLayout(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "layoutId", layout_type AS "layoutType", CAST(widgets AS varchar) AS "widgets",
                   is_default AS "isDefault"
            FROM collab_workbench_layout
            WHERE tenant_id = #{tenantId} AND layout_type = 'TEMPLATE' AND is_default = 1 AND deleted = 0
            ORDER BY updated_at DESC LIMIT 1
            """)
    Map<String, Object> findDefaultLayout(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO collab_workbench_layout (id, tenant_id, owner_id, layout_type, widgets, is_default, created_by)
            VALUES (#{id}, #{tenantId}, #{userId}, 'PERSONAL', CAST(#{widgets} AS jsonb), 0, #{userId})
            """)
    void insertPersonalLayout(@Param("id") long id, @Param("tenantId") long tenantId, @Param("userId") long userId,
                              @Param("widgets") String widgets);

    @Update("""
            UPDATE collab_workbench_layout SET widgets = CAST(#{widgets} AS jsonb), updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{layoutId} AND deleted = 0
            """)
    void updateLayout(@Param("tenantId") long tenantId, @Param("layoutId") long layoutId,
                      @Param("widgets") String widgets, @Param("userId") long userId);

    @Select("""
            SELECT id AS "widgetId", code AS "code", name AS "name", widget_type AS "widgetType",
                   default_size AS "defaultSize", enabled AS "enabled"
            FROM collab_workbench_widget
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listWidgets(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO collab_workbench_widget (id, tenant_id, code, name, widget_type, default_size, enabled, created_by)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{widgetType}, #{defaultSize}, #{enabled}, #{userId})
            """)
    void insertWidget(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
                      @Param("name") String name, @Param("widgetType") String widgetType,
                      @Param("defaultSize") String defaultSize, @Param("enabled") int enabled,
                      @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_todo
                (id, tenant_id, owner_id, source_type, source_id, title, due_at, priority, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ownerId}, #{sourceType}, #{sourceId,jdbcType=VARCHAR}, #{title},
                    #{dueAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, #{priority}, 'OPEN', #{userId})
            ON CONFLICT (tenant_id, owner_id, source_type, source_id) WHERE deleted = 0 DO NOTHING
            """)
    void insertTodo(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ownerId") long ownerId,
                    @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                    @Param("title") String title, @Param("dueAt") OffsetDateTime dueAt,
                    @Param("priority") String priority, @Param("userId") long userId);

    @Select("""
            SELECT id AS "todoId", owner_id AS "ownerId", source_type AS "sourceType", source_id AS "sourceId",
                   title AS "title", (EXTRACT(EPOCH FROM due_at) * 1000)::bigint AS "dueAt", priority AS "priority",
                   status AS "status"
            FROM collab_todo
            WHERE tenant_id = #{tenantId} AND owner_id = #{userId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR status = #{status})
            ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END, due_at NULLS LAST
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listTodos(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                        @Param("status") String status, @Param("offset") int offset,
                                        @Param("size") int size);

    @Select("""
            SELECT id AS "todoId", source_type AS "sourceType", source_id AS "sourceId", status AS "status"
            FROM collab_todo
            WHERE tenant_id = #{tenantId} AND id = #{todoId} AND owner_id = #{userId} AND deleted = 0
            """)
    Map<String, Object> findTodo(@Param("tenantId") long tenantId, @Param("todoId") long todoId,
                                 @Param("userId") long userId);

    @Select("""
            SELECT id AS "todoId" FROM collab_todo
            WHERE tenant_id = #{tenantId} AND owner_id = #{ownerId} AND source_type = #{sourceType}
              AND source_id = #{sourceId} AND deleted = 0
            """)
    Long findTodoIdBySource(@Param("tenantId") long tenantId, @Param("ownerId") long ownerId,
                            @Param("sourceType") String sourceType, @Param("sourceId") String sourceId);

    @Update("""
            UPDATE collab_todo SET status = 'DONE', done_at = now(), updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{todoId} AND owner_id = #{userId} AND deleted = 0
              AND status = 'OPEN'
            """)
    int markTodoDone(@Param("tenantId") long tenantId, @Param("todoId") long todoId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_notification
                (id, tenant_id, receiver_id, category, title, content, biz_type, biz_id, channel, created_by)
            VALUES (#{id}, #{tenantId}, #{receiverId}, #{category}, #{title}, #{content,jdbcType=VARCHAR},
                    #{bizType,jdbcType=VARCHAR}, #{bizId,jdbcType=VARCHAR}, 'INBOX', #{userId})
            """)
    void insertNotification(@Param("id") long id, @Param("tenantId") long tenantId, @Param("receiverId") long receiverId,
                            @Param("category") String category, @Param("title") String title,
                            @Param("content") String content, @Param("bizType") String bizType,
                            @Param("bizId") String bizId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "notificationId", category AS "category", title AS "title", content AS "content",
                   read_flag AS "readFlag", (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_notification
            WHERE tenant_id = #{tenantId} AND receiver_id = #{userId} AND deleted = 0
              AND (CAST(#{unreadOnly,jdbcType=BOOLEAN} AS boolean) IS NOT TRUE OR read_flag = 0)
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listNotifications(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                                @Param("unreadOnly") Boolean unreadOnly, @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("""
            SELECT count(1) FROM collab_notification
            WHERE tenant_id = #{tenantId} AND receiver_id = #{userId} AND deleted = 0
              AND (CAST(#{unreadOnly,jdbcType=BOOLEAN} AS boolean) IS NOT TRUE OR read_flag = 0)
            """)
    long countNotifications(@Param("tenantId") long tenantId, @Param("userId") long userId,
                            @Param("unreadOnly") Boolean unreadOnly);

    @Update("""
            UPDATE collab_notification SET read_flag = 1, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{notificationId} AND receiver_id = #{userId} AND deleted = 0
            """)
    int markNotificationRead(@Param("tenantId") long tenantId, @Param("notificationId") long notificationId,
                             @Param("userId") long userId);
}
