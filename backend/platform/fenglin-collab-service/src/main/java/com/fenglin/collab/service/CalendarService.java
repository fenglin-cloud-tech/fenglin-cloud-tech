package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.CalendarMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.AttendeeView;
import com.fenglin.collab.web.dto.CollabDtos.BusySlot;
import com.fenglin.collab.web.dto.CollabDtos.CalendarCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.CalendarView;
import com.fenglin.collab.web.dto.CollabDtos.EventCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.EventUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.EventView;
import com.fenglin.collab.web.dto.CollabDtos.FreeBusyView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingView;
import com.fenglin.collab.web.dto.CollabDtos.SlotSuggestion;
import com.fenglin.collab.web.dto.CollabDtos.SlotView;
import com.fenglin.collab.web.dto.CollabDtos.SuggestSlotsRequest;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 日历主链：日历、日程、参与人、忙闲查询、推荐时段；可选联动会议室预定（复用会议域）。
 */
@Service
public class CalendarService {

    private static final Pattern RRULE = Pattern.compile(
            "^FREQ=(DAILY|WEEKLY|MONTHLY|YEARLY)(;.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CAL_TYPES = Set.of("PERSONAL", "TEAM", "PUBLIC", "HOLIDAY");

    private final CalendarMapper calendarMapper;
    private final MeetingService meetingService;
    private final CollabCrypto crypto;
    private final CollabAudit audit;

    public CalendarService(CalendarMapper calendarMapper, MeetingService meetingService,
                           CollabCrypto crypto, CollabAudit audit) {
        this.calendarMapper = calendarMapper;
        this.meetingService = meetingService;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public CalendarView createCalendar(CalendarCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "日历名称不能为空");
        }
        String calType = request.calType() == null ? "PERSONAL" : request.calType();
        if (!CAL_TYPES.contains(calType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "日历类型必须为 PERSONAL/TEAM/PUBLIC/HOLIDAY");
        }
        String calendarId = UUID.randomUUID().toString();
        calendarMapper.insertCalendar(calendarId, tenantId, request.name(), calType, userId, request.deptId(),
                request.color(), request.visibleScope() == null ? "DEPT" : request.visibleScope());
        audit.publish("COLLAB_CALENDAR_CREATED", "CREATE", "collab_calendar", calendarId, "LOW");
        return new CalendarView(calendarId, request.name(), calType, userId, request.color(),
                request.visibleScope() == null ? "DEPT" : request.visibleScope());
    }

    @Transactional(readOnly = true)
    public List<CalendarView> listCalendars() {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        return calendarMapper.listCalendars(tenantId, userId).stream()
                .map(row -> new CalendarView(Rows.str(row, "calendarId"), Rows.str(row, "name"),
                        Rows.str(row, "calType"), Rows.lng(row, "ownerId"), Rows.str(row, "color"),
                        Rows.str(row, "visibleScope")))
                .toList();
    }

    @Transactional
    public EventView createEvent(EventCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.calendarId() == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "日历不能为空");
        }
        validateRange(request.startAt(), request.endAt());
        Map<String, Object> calendar = calendarMapper.findCalendar(tenantId, request.calendarId());
        if (calendar == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "日历不存在");
        }
        if (Rows.lngVal(calendar, "ownerId") != userId && !"PUBLIC".equals(Rows.str(calendar, "calType"))) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "无权在该日历创建日程");
        }
        if (request.rrule() != null && !request.rrule().isBlank() && !RRULE.matcher(request.rrule()).matches()) {
            throw new BizException(CollabErrorCode.RRULE_INVALID);
        }
        if (calendarMapper.countOverlap(tenantId, userId, request.startAt(), request.endAt(), null) > 0) {
            throw new BizException(CollabErrorCode.EVENT_TIME_CONFLICT);
        }
        String eventId = UUID.randomUUID().toString();
        calendarMapper.insertEvent(eventId, tenantId, request.calendarId(), request.title(),
                crypto.encrypt(tenantId, "collab_event", "description", request.description()),
                request.location(), request.startAt(), request.endAt(),
                Boolean.TRUE.equals(request.allDay()) ? 1 : 0, request.rrule(),
                toIntArray(request.reminderMinutes()), userId);
        calendarMapper.insertAttendee(IdWorker.getId(), tenantId, eventId, userId, "ACCEPTED", 1, userId);
        Set<Long> attendees = new LinkedHashSet<>();
        if (request.attendeeIds() != null) {
            request.attendeeIds().stream().filter(id -> id != null && id > 0).forEach(attendees::add);
        }
        attendees.remove(userId);
        for (Long attendeeId : attendees) {
            calendarMapper.insertAttendee(IdWorker.getId(), tenantId, eventId, attendeeId, "PENDING", 0, userId);
        }
        if (request.roomId() != null) {
            MeetingView meeting = meetingService.createInternal(tenantId, userId, request.title(),
                    request.startAt(), request.endAt(), "OFFLINE", request.roomId(),
                    request.description(), false, eventId);
            calendarMapper.linkMeeting(tenantId, eventId, meeting.meetingId(), userId);
        }
        audit.publish("COLLAB_EVENT_CREATED", "CREATE", "collab_event", eventId, "LOW");
        return detail(eventId);
    }

    @Transactional(readOnly = true)
    public EventView detail(String eventId) {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> event = requireEvent(tenantId, eventId);
        List<AttendeeView> attendees = calendarMapper.listAttendees(tenantId, eventId).stream()
                .map(row -> new AttendeeView(Rows.lngVal(row, "attendeeId"), Rows.str(row, "response"),
                        Rows.intVal(row, "isOrganizer") == 1))
                .toList();
        return new EventView(eventId, Rows.str(event, "calendarId"), Rows.str(event, "title"),
                Rows.instant(event, "startAt"), Rows.instant(event, "endAt"),
                Rows.intVal(event, "allDay") == 1, Rows.str(event, "status"),
                Rows.str(event, "meetingId"), attendees);
    }

    @Transactional(readOnly = true)
    public List<EventView> listEvents(OffsetDateTime startAt, OffsetDateTime endAt, String calendarId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        validateRange(startAt, endAt);
        return calendarMapper.listEvents(tenantId, userId, calendarId, startAt, endAt).stream()
                .map(row -> new EventView(Rows.str(row, "eventId"), Rows.str(row, "calendarId"),
                        Rows.str(row, "title"), Rows.instant(row, "startAt"), Rows.instant(row, "endAt"),
                        Rows.intVal(row, "allDay") == 1, Rows.str(row, "status"), null, List.of()))
                .toList();
    }

    @Transactional
    public EventView updateEvent(String eventId, EventUpdateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> event = requireEvent(tenantId, eventId);
        if (Rows.lngVal(event, "ownerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅日程创建人可修改");
        }
        OffsetDateTime startAt = request == null || request.startAt() == null
                ? Rows.instant(event, "startAt").atOffset(ZoneOffset.UTC) : request.startAt();
        OffsetDateTime endAt = request == null || request.endAt() == null
                ? Rows.instant(event, "endAt").atOffset(ZoneOffset.UTC) : request.endAt();
        validateRange(startAt, endAt);
        if (calendarMapper.countOverlap(tenantId, userId, startAt, endAt, eventId) > 0) {
            throw new BizException(CollabErrorCode.EVENT_TIME_CONFLICT);
        }
        calendarMapper.updateEvent(tenantId, eventId,
                request == null ? null : request.title(),
                request == null ? null : crypto.encrypt(tenantId, "collab_event", "description",
                        request.description()),
                request == null ? null : request.location(), startAt, endAt,
                request == null || request.allDay() == null ? null : (request.allDay() ? 1 : 0), userId);
        audit.publish("COLLAB_EVENT_UPDATED", "UPDATE", "collab_event", eventId, "LOW");
        return detail(eventId);
    }

    /**
     * 缺口补齐 API-CAL-008：取消日程；若已联动会议，同步取消会议并释放会议室。
     */
    @Transactional
    public void cancelEvent(String eventId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> event = requireEvent(tenantId, eventId);
        if (Rows.lngVal(event, "ownerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅日程创建人可取消");
        }
        if (calendarMapper.cancelEvent(tenantId, eventId, userId) == 0) {
            throw new BizException(CollabErrorCode.STATE_CONFLICT, "日程已取消");
        }
        String meetingId = Rows.str(event, "meetingId");
        if (meetingId != null) {
            meetingService.cancel(meetingId);
        }
        audit.publish("COLLAB_EVENT_CANCELLED", "UPDATE", "collab_event", eventId, "MEDIUM");
    }

    @Transactional(readOnly = true)
    public FreeBusyView freeBusy(List<Long> userIds, OffsetDateTime startTime, OffsetDateTime endTime) {
        long tenantId = CollabContext.tenantId();
        validateRange(startTime, endTime);
        Map<Long, List<BusySlot>> busy = new HashMap<>();
        for (Long userId : userIds == null ? List.<Long>of() : userIds) {
            busy.put(userId, calendarMapper.listUserBusy(tenantId, userId, startTime, endTime).stream()
                    .map(row -> new BusySlot(Rows.instant(row, "startAt"), Rows.instant(row, "endAt"), "BUSY"))
                    .toList());
        }
        return new FreeBusyView(busy);
    }

    /**
     * 推荐时段：未来 N 天工作时段 9:00-18:00，30 分钟步进，按全员可用数排序取前 5。
     */
    @Transactional(readOnly = true)
    public SlotSuggestion suggestSlots(SuggestSlotsRequest request) {
        long tenantId = CollabContext.tenantId();
        List<Long> userIds = request == null || request.userIds() == null || request.userIds().isEmpty()
                ? List.of(CollabContext.userId()) : request.userIds();
        int duration = request == null || request.durationMinutes() == null
                ? 30 : Math.min(Math.max(request.durationMinutes(), 15), 480);
        int searchDays = request == null || request.searchDays() == null
                ? 5 : Math.min(Math.max(request.searchDays(), 1), 14);
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atTime(LocalTime.of(9, 0))
                .atOffset(ZoneOffset.UTC);
        List<Map<Long, List<Map<String, Object>>>> busyByDay = new ArrayList<>();
        for (int day = 0; day < searchDays; day++) {
            OffsetDateTime dayStart = base.plusDays(day);
            OffsetDateTime dayEnd = dayStart.withHour(18);
            Map<Long, List<Map<String, Object>>> perUser = new HashMap<>();
            for (Long userId : userIds) {
                perUser.put(userId, calendarMapper.listUserBusy(tenantId, userId, dayStart, dayEnd));
            }
            busyByDay.add(perUser);
        }
        List<SlotView> slots = new ArrayList<>();
        for (int day = 0; day < searchDays; day++) {
            OffsetDateTime dayStart = base.plusDays(day);
            OffsetDateTime dayEnd = dayStart.withHour(18);
            for (OffsetDateTime cursor = dayStart; !cursor.plusMinutes(duration).isAfter(dayEnd);
                 cursor = cursor.plusMinutes(30)) {
                OffsetDateTime slotStart = cursor;
                OffsetDateTime slotEnd = cursor.plusMinutes(duration);
                int available = 0;
                for (Long userId : userIds) {
                    boolean conflict = busyByDay.get(day).get(userId).stream().anyMatch(row -> {
                        Instant busyStart = Rows.instant(row, "startAt");
                        Instant busyEnd = Rows.instant(row, "endAt");
                        return busyStart.isBefore(slotEnd.toInstant()) && busyEnd.isAfter(slotStart.toInstant());
                    });
                    available += conflict ? 0 : 1;
                }
                slots.add(new SlotView(slotStart.toInstant(), slotEnd.toInstant(), available,
                        available * 100 - slotStart.getHour()));
            }
        }
        slots.sort((left, right) -> {
            int byAvailable = Integer.compare(right.availableCount(), left.availableCount());
            return byAvailable != 0 ? byAvailable : left.start().compareTo(right.start());
        });
        return new SlotSuggestion(slots.stream().limit(5).toList());
    }

    private Map<String, Object> requireEvent(long tenantId, String eventId) {
        Map<String, Object> event = calendarMapper.findEvent(tenantId, eventId);
        if (event == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "日程不存在");
        }
        return event;
    }

    private void validateRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private String toIntArray(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return "{" + values.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "}";
    }
}
