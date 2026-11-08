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
 * 日历域 Mapper：日程冲突检测按（用户, 时段）查询，未依赖数据库排他约束（RRULE 未展开）。
 */
@Mapper
public interface CalendarMapper {

    @Insert("""
            INSERT INTO collab_calendar (id, tenant_id, name, cal_type, owner_id, dept_id, color, visible_scope, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{name}, #{calType}, #{ownerId}, #{deptId,jdbcType=BIGINT},
                    #{color,jdbcType=VARCHAR}, #{visibleScope}, #{ownerId})
            """)
    void insertCalendar(@Param("id") String id, @Param("tenantId") long tenantId, @Param("name") String name,
                        @Param("calType") String calType, @Param("ownerId") Long ownerId, @Param("deptId") Long deptId,
                        @Param("color") String color, @Param("visibleScope") String visibleScope);

    @Select("""
            SELECT id AS "calendarId", name AS "name", cal_type AS "calType", owner_id AS "ownerId",
                   color AS "color", visible_scope AS "visibleScope"
            FROM collab_calendar
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (owner_id = #{userId} OR cal_type IN ('PUBLIC', 'HOLIDAY'))
            ORDER BY cal_type, name
            """)
    List<Map<String, Object>> listCalendars(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "calendarId", name AS "name", cal_type AS "calType", owner_id AS "ownerId",
                   visible_scope AS "visibleScope"
            FROM collab_calendar
            WHERE tenant_id = #{tenantId} AND id = CAST(#{calendarId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findCalendar(@Param("tenantId") long tenantId, @Param("calendarId") String calendarId);

    @Insert("""
            INSERT INTO collab_event
                (id, tenant_id, calendar_id, title, description_cipher, location, start_at, end_at, all_day,
                 rrule, reminder_minutes, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, CAST(#{calendarId} AS uuid), #{title},
                    #{descriptionCipher,jdbcType=VARCHAR}, #{location,jdbcType=VARCHAR}, #{startAt}, #{endAt},
                    #{allDay}, #{rrule,jdbcType=VARCHAR}, CAST(#{reminderMinutes,jdbcType=VARCHAR} AS int[]), #{userId})
            """)
    void insertEvent(@Param("id") String id, @Param("tenantId") long tenantId,
                     @Param("calendarId") String calendarId, @Param("title") String title,
                     @Param("descriptionCipher") String descriptionCipher, @Param("location") String location,
                     @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                     @Param("allDay") int allDay, @Param("rrule") String rrule,
                     @Param("reminderMinutes") String reminderMinutes, @Param("userId") long userId);

    @Select("""
            SELECT e.id AS "eventId", e.calendar_id AS "calendarId", e.title AS "title",
                   e.description_cipher AS "descriptionCipher", e.location AS "location",
                   (EXTRACT(EPOCH FROM e.start_at) * 1000)::bigint AS "startAt",
                   (EXTRACT(EPOCH FROM e.end_at) * 1000)::bigint AS "endAt",
                   e.all_day AS "allDay", e.status AS "status", CAST(e.meeting_id AS varchar) AS "meetingId",
                   c.owner_id AS "ownerId"
            FROM collab_event e
            JOIN collab_calendar c ON c.id = e.calendar_id AND c.tenant_id = e.tenant_id
            WHERE e.tenant_id = #{tenantId} AND e.id = CAST(#{eventId} AS uuid) AND e.deleted = 0
            """)
    Map<String, Object> findEvent(@Param("tenantId") long tenantId, @Param("eventId") String eventId);

    @Select("""
            SELECT e.id AS "eventId", e.calendar_id AS "calendarId", e.title AS "title",
                   (EXTRACT(EPOCH FROM e.start_at) * 1000)::bigint AS "startAt",
                   (EXTRACT(EPOCH FROM e.end_at) * 1000)::bigint AS "endAt",
                   e.all_day AS "allDay", e.status AS "status"
            FROM collab_event e
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0
              AND (e.calendar_id = CAST(#{calendarId,jdbcType=VARCHAR} AS uuid)
                   OR (CAST(#{calendarId,jdbcType=VARCHAR} AS uuid) IS NULL
                       AND (e.calendar_id IN (SELECT id FROM collab_calendar
                                              WHERE tenant_id = #{tenantId} AND owner_id = #{userId} AND deleted = 0)
                            OR EXISTS (SELECT 1 FROM collab_event_attendee a
                                       WHERE a.tenant_id = e.tenant_id AND a.event_id = e.id
                                         AND a.attendee_id = #{userId} AND a.deleted = 0))))
              AND e.start_at >= #{startAt} AND e.end_at <= #{endAt}
            ORDER BY e.start_at
            """)
    List<Map<String, Object>> listEvents(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                         @Param("calendarId") String calendarId,
                                         @Param("startAt") OffsetDateTime startAt,
                                         @Param("endAt") OffsetDateTime endAt);

    @Update("""
            UPDATE collab_event
            SET title = COALESCE(#{title,jdbcType=VARCHAR}, title),
                description_cipher = COALESCE(#{descriptionCipher,jdbcType=VARCHAR}, description_cipher),
                location = COALESCE(#{location,jdbcType=VARCHAR}, location),
                start_at = COALESCE(#{startAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, start_at),
                end_at = COALESCE(#{endAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, end_at),
                all_day = COALESCE(#{allDay,jdbcType=SMALLINT}, all_day),
                updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{eventId} AS uuid) AND deleted = 0
            """)
    void updateEvent(@Param("tenantId") long tenantId, @Param("eventId") String eventId,
                     @Param("title") String title, @Param("descriptionCipher") String descriptionCipher,
                     @Param("location") String location, @Param("startAt") OffsetDateTime startAt,
                     @Param("endAt") OffsetDateTime endAt, @Param("allDay") Integer allDay,
                     @Param("userId") long userId);

    @Update("""
            UPDATE collab_event SET status = 'CANCELLED', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{eventId} AS uuid) AND deleted = 0
              AND status <> 'CANCELLED'
            """)
    int cancelEvent(@Param("tenantId") long tenantId, @Param("eventId") String eventId,
                    @Param("userId") long userId);

    @Update("""
            UPDATE collab_event SET meeting_id = CAST(#{meetingId} AS uuid), updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{eventId} AS uuid)
            """)
    void linkMeeting(@Param("tenantId") long tenantId, @Param("eventId") String eventId,
                     @Param("meetingId") String meetingId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_event_attendee
                (id, tenant_id, event_id, attendee_id, response, is_organizer, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{eventId} AS uuid), #{attendeeId}, #{response}, #{organizer}, #{userId})
            ON CONFLICT (tenant_id, event_id, attendee_id) DO NOTHING
            """)
    void insertAttendee(@Param("id") long id, @Param("tenantId") long tenantId, @Param("eventId") String eventId,
                        @Param("attendeeId") long attendeeId, @Param("response") String response,
                        @Param("organizer") int organizer, @Param("userId") long userId);

    @Select("""
            SELECT attendee_id AS "attendeeId", response AS "response", is_organizer AS "isOrganizer"
            FROM collab_event_attendee
            WHERE tenant_id = #{tenantId} AND event_id = CAST(#{eventId} AS uuid) AND deleted = 0
            ORDER BY is_organizer DESC, attendee_id
            """)
    List<Map<String, Object>> listAttendees(@Param("tenantId") long tenantId, @Param("eventId") String eventId);

    @Select("""
            SELECT count(1)
            FROM collab_event e
            JOIN collab_calendar c ON c.id = e.calendar_id AND c.tenant_id = e.tenant_id
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0 AND e.status = 'CONFIRMED'
              AND c.owner_id = #{userId}
              AND e.start_at < #{endAt} AND e.end_at > #{startAt}
              AND (CAST(#{excludeEventId,jdbcType=VARCHAR} AS uuid) IS NULL
                   OR e.id <> CAST(#{excludeEventId} AS uuid))
            """)
    long countOverlap(@Param("tenantId") long tenantId, @Param("userId") long userId,
                      @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                      @Param("excludeEventId") String excludeEventId);

    @Select("""
            SELECT e.id AS "eventId", e.title AS "title",
                   (EXTRACT(EPOCH FROM e.start_at) * 1000)::bigint AS "startAt",
                   (EXTRACT(EPOCH FROM e.end_at) * 1000)::bigint AS "endAt"
            FROM collab_event e
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0 AND e.status = 'CONFIRMED'
              AND e.start_at < #{endAt} AND e.end_at > #{startAt}
              AND (e.calendar_id IN (SELECT id FROM collab_calendar
                                     WHERE tenant_id = #{tenantId} AND owner_id = #{userId} AND deleted = 0)
                   OR EXISTS (SELECT 1 FROM collab_event_attendee a
                              WHERE a.tenant_id = e.tenant_id AND a.event_id = e.id
                                AND a.attendee_id = #{userId} AND a.deleted = 0))
            ORDER BY e.start_at
            """)
    List<Map<String, Object>> listUserBusy(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                           @Param("startAt") OffsetDateTime startAt,
                                           @Param("endAt") OffsetDateTime endAt);
}
