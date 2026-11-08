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
 * 会议域 Mapper。会议室并发预定使用 pg_advisory_xact_lock + 时段重叠校验，
 * 有 btree_gist 时数据库排他约束作为第二道防线。
 */
@Mapper
public interface MeetingMapper {

    @Insert("""
            INSERT INTO collab_meeting_room
                (id, tenant_id, name, location, capacity, equipments, dept_id, bookable, status, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{location,jdbcType=VARCHAR}, #{capacity},
                    CAST(#{equipments,jdbcType=VARCHAR} AS varchar[]), #{deptId,jdbcType=BIGINT}, #{bookable},
                    'AVAILABLE', #{userId})
            """)
    void insertRoom(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                    @Param("location") String location, @Param("capacity") int capacity,
                    @Param("equipments") String equipments, @Param("deptId") Long deptId,
                    @Param("bookable") int bookable, @Param("userId") long userId);

    @Select("""
            SELECT id AS "roomId", name AS "name", location AS "location", capacity AS "capacity",
                   equipments AS "equipments", bookable AS "bookable", status AS "status", dept_id AS "deptId"
            FROM collab_meeting_room
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{onlyBookable,jdbcType=BOOLEAN} AS boolean) IS NOT TRUE OR (bookable = 1 AND status = 'AVAILABLE'))
            ORDER BY id
            """)
    List<Map<String, Object>> listRooms(@Param("tenantId") long tenantId, @Param("onlyBookable") Boolean onlyBookable);

    @Select("""
            SELECT id AS "roomId", name AS "name", capacity AS "capacity", bookable AS "bookable",
                   status AS "status"
            FROM collab_meeting_room
            WHERE tenant_id = #{tenantId} AND id = #{roomId} AND deleted = 0
            """)
    Map<String, Object> findRoom(@Param("tenantId") long tenantId, @Param("roomId") long roomId);

    @Select("""
            SELECT r.id AS "roomId", r.name AS "name", r.location AS "location", r.capacity AS "capacity",
                   r.equipments AS "equipments"
            FROM collab_meeting_room r
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0 AND r.bookable = 1 AND r.status = 'AVAILABLE'
              AND NOT EXISTS (SELECT 1 FROM collab_room_reservation rv
                              WHERE rv.tenant_id = r.tenant_id AND rv.room_id = r.id
                                AND rv.deleted = 0 AND rv.status = 'RESERVED'
                                AND rv.slot && tstzrange(#{startAt}, #{endAt}, '[)'))
            ORDER BY r.capacity
            """)
    List<Map<String, Object>> availableRooms(@Param("tenantId") long tenantId,
                                             @Param("startAt") OffsetDateTime startAt,
                                             @Param("endAt") OffsetDateTime endAt);

    @Select("""
            SELECT 1 AS "acquired" FROM (
                SELECT pg_advisory_xact_lock(CAST(hashtext(#{key}) AS bigint))
            ) AS lock
            """)
    Integer acquireAdvisoryLock(@Param("key") String key);
    @Select("""
            SELECT count(1) FROM collab_room_reservation
            WHERE tenant_id = #{tenantId} AND room_id = #{roomId} AND deleted = 0 AND status = 'RESERVED'
              AND slot && tstzrange(#{startAt}, #{endAt}, '[)')
            """)
    long countRoomOverlap(@Param("tenantId") long tenantId, @Param("roomId") long roomId,
                          @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Insert("""
            INSERT INTO collab_room_reservation
                (id, tenant_id, room_id, meeting_id, slot, status, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{roomId}, CAST(#{meetingId} AS uuid),
                    tstzrange(#{startAt}, #{endAt}, '[)'), 'RESERVED', #{userId})
            """)
    void insertReservation(@Param("id") String id, @Param("tenantId") long tenantId, @Param("roomId") long roomId,
                           @Param("meetingId") String meetingId, @Param("startAt") OffsetDateTime startAt,
                           @Param("endAt") OffsetDateTime endAt, @Param("userId") long userId);

    @Select("""
            SELECT CAST(id AS varchar) AS "reservationId", status AS "status" FROM collab_room_reservation
            WHERE tenant_id = #{tenantId} AND meeting_id = CAST(#{meetingId} AS uuid) AND deleted = 0
              AND status = 'RESERVED'
            """)
    Map<String, Object> findReservationByMeeting(@Param("tenantId") long tenantId,
                                                 @Param("meetingId") String meetingId);

    @Update("""
            UPDATE collab_room_reservation SET status = 'RELEASED', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND meeting_id = CAST(#{meetingId} AS uuid)
              AND status = 'RESERVED' AND deleted = 0
            """)
    void releaseReservation(@Param("tenantId") long tenantId, @Param("meetingId") String meetingId,
                            @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_meeting
                (id, tenant_id, title, organizer_id, event_id, room_id, room_slot, start_at, end_at,
                 meeting_mode, join_code, rtc_room_id, agenda_cipher, external_allowed, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{title}, #{organizerId}, CAST(#{eventId,jdbcType=VARCHAR} AS uuid),
                    #{roomId,jdbcType=BIGINT},
                    CASE WHEN #{roomId,jdbcType=BIGINT} IS NULL THEN NULL
                         ELSE tstzrange(#{startAt}, #{endAt}, '[)') END,
                    #{startAt}, #{endAt}, #{meetingMode}, #{joinCode}, #{rtcRoomId},
                    #{agendaCipher,jdbcType=VARCHAR}, #{externalAllowed}, #{organizerId})
            """)
    void insertMeeting(@Param("id") String id, @Param("tenantId") long tenantId, @Param("title") String title,
                       @Param("organizerId") long organizerId, @Param("eventId") String eventId,
                       @Param("roomId") Long roomId, @Param("startAt") OffsetDateTime startAt,
                       @Param("endAt") OffsetDateTime endAt, @Param("meetingMode") String meetingMode,
                       @Param("joinCode") String joinCode, @Param("rtcRoomId") String rtcRoomId,
                       @Param("agendaCipher") String agendaCipher, @Param("externalAllowed") int externalAllowed);

    @Select("""
            SELECT m.id AS "meetingId", m.title AS "title", m.organizer_id AS "organizerId", m.room_id AS "roomId",
                   (EXTRACT(EPOCH FROM m.start_at) * 1000)::bigint AS "startAt",
                   (EXTRACT(EPOCH FROM m.end_at) * 1000)::bigint AS "endAt",
                   m.meeting_mode AS "meetingMode", m.join_code AS "joinCode", m.status AS "status",
                   m.rtc_room_id AS "rtcRoomId"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.id = CAST(#{meetingId} AS uuid) AND m.deleted = 0
            """)
    Map<String, Object> findMeeting(@Param("tenantId") long tenantId, @Param("meetingId") String meetingId);

    @Select("""
            SELECT m.id AS "meetingId", m.title AS "title", m.organizer_id AS "organizerId", m.room_id AS "roomId",
                   (EXTRACT(EPOCH FROM m.start_at) * 1000)::bigint AS "startAt",
                   (EXTRACT(EPOCH FROM m.end_at) * 1000)::bigint AS "endAt",
                   m.meeting_mode AS "meetingMode", m.join_code AS "joinCode", m.status AS "status"
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0
              AND (m.organizer_id = #{userId}
                   OR EXISTS (SELECT 1 FROM collab_event_attendee a
                              JOIN collab_event e ON e.id = a.event_id AND e.tenant_id = a.tenant_id
                              WHERE a.tenant_id = m.tenant_id AND e.meeting_id = m.id
                                AND a.attendee_id = #{userId} AND a.deleted = 0))
            ORDER BY m.start_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listMeetings(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                           @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_meeting m
            WHERE m.tenant_id = #{tenantId} AND m.deleted = 0
              AND (m.organizer_id = #{userId}
                   OR EXISTS (SELECT 1 FROM collab_event_attendee a
                              JOIN collab_event e ON e.id = a.event_id AND e.tenant_id = a.tenant_id
                              WHERE a.tenant_id = m.tenant_id AND e.meeting_id = m.id
                                AND a.attendee_id = #{userId} AND a.deleted = 0))
            """)
    long countMeetings(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Update("""
            UPDATE collab_meeting SET status = #{status}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{meetingId} AS uuid) AND deleted = 0
            """)
    void updateMeetingStatus(@Param("tenantId") long tenantId, @Param("meetingId") String meetingId,
                             @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_meeting_minutes
                (id, tenant_id, meeting_id, template_id, content_cipher, summary, status, published_at, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, CAST(#{meetingId} AS uuid), #{templateId,jdbcType=BIGINT},
                    #{contentCipher,jdbcType=VARCHAR}, #{summary,jdbcType=VARCHAR}, #{status},
                    CASE WHEN #{status} = 'PUBLISHED' THEN now() ELSE NULL END, #{userId})
            """)
    void insertMinutes(@Param("id") String id, @Param("tenantId") long tenantId, @Param("meetingId") String meetingId,
                       @Param("templateId") Long templateId, @Param("contentCipher") String contentCipher,
                       @Param("summary") String summary, @Param("status") String status,
                       @Param("userId") long userId);

    @Select("""
            SELECT CAST(id AS varchar) AS "minutesId", status AS "status", summary AS "summary",
                   (EXTRACT(EPOCH FROM published_at) * 1000)::bigint AS "publishedAt"
            FROM collab_meeting_minutes
            WHERE tenant_id = #{tenantId} AND meeting_id = CAST(#{meetingId} AS uuid) AND deleted = 0
            ORDER BY created_at DESC LIMIT 1
            """)
    Map<String, Object> findLatestMinutes(@Param("tenantId") long tenantId, @Param("meetingId") String meetingId);

    @Select("""
            SELECT CAST(id AS varchar) AS "minutesId", status AS "status" FROM collab_meeting_minutes
            WHERE tenant_id = #{tenantId} AND id = CAST(#{minutesId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findMinutes(@Param("tenantId") long tenantId, @Param("minutesId") String minutesId);

    @Update("""
            UPDATE collab_meeting_minutes SET status = 'PUBLISHED', published_at = now(), updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{minutesId} AS uuid) AND deleted = 0
            """)
    void publishMinutes(@Param("tenantId") long tenantId, @Param("minutesId") String minutesId,
                        @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_meeting_action
                (id, tenant_id, minutes_id, content, assignee_id, due_at, status, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{minutesId} AS uuid), #{content}, #{assigneeId,jdbcType=BIGINT},
                    #{dueAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, 'OPEN', #{userId})
            """)
    void insertAction(@Param("id") long id, @Param("tenantId") long tenantId, @Param("minutesId") String minutesId,
                      @Param("content") String content, @Param("assigneeId") Long assigneeId,
                      @Param("dueAt") OffsetDateTime dueAt, @Param("userId") long userId);

    @Update("""
            UPDATE collab_meeting_action SET todo_id = #{todoId}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{actionId}
            """)
    void linkActionTodo(@Param("tenantId") long tenantId, @Param("actionId") long actionId,
                        @Param("todoId") long todoId, @Param("userId") long userId);

    @Update("""
            UPDATE collab_meeting_action SET status = 'DONE', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = #{actionId}
            """)
    void completeAction(@Param("tenantId") long tenantId, @Param("actionId") long actionId,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "actionId", content AS "content", assignee_id AS "assigneeId",
                   (EXTRACT(EPOCH FROM due_at) * 1000)::bigint AS "dueAt", todo_id AS "todoId", status AS "status"
            FROM collab_meeting_action
            WHERE tenant_id = #{tenantId} AND minutes_id = CAST(#{minutesId} AS uuid) AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listActions(@Param("tenantId") long tenantId, @Param("minutesId") String minutesId);

    @Insert("""
            INSERT INTO collab_meeting_checkin (id, tenant_id, meeting_id, user_id, method, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{meetingId} AS uuid), #{userId}, #{method}, #{userId})
            ON CONFLICT (tenant_id, meeting_id, user_id) DO NOTHING
            """)
    void insertCheckin(@Param("id") long id, @Param("tenantId") long tenantId, @Param("meetingId") String meetingId,
                       @Param("userId") long userId, @Param("method") String method);

    @Select("""
            SELECT user_id AS "userId", (EXTRACT(EPOCH FROM checkin_at) * 1000)::bigint AS "checkinAt"
            FROM collab_meeting_checkin
            WHERE tenant_id = #{tenantId} AND meeting_id = CAST(#{meetingId} AS uuid) AND deleted = 0
            ORDER BY checkin_at
            """)
    List<Map<String, Object>> listCheckins(@Param("tenantId") long tenantId, @Param("meetingId") String meetingId);
}
