package com.fenglin.collab.web;

import com.fenglin.collab.service.CalendarService;
import com.fenglin.collab.web.dto.CollabDtos.CalendarCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.CalendarView;
import com.fenglin.collab.web.dto.CollabDtos.EventCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.EventUpdateRequest;
import com.fenglin.collab.web.dto.CollabDtos.EventView;
import com.fenglin.collab.web.dto.CollabDtos.FreeBusyRequest;
import com.fenglin.collab.web.dto.CollabDtos.FreeBusyView;
import com.fenglin.collab.web.dto.CollabDtos.SlotSuggestion;
import com.fenglin.collab.web.dto.CollabDtos.SuggestSlotsRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 日历接口（API-CAL-001 ~ 008）。
 */
@RestController
@RequestMapping("/api/collab/v1")
public class CollabCalendarController {

    private static final String READ = "biz:collab:calendar:read";
    private static final String SELF_READ = "biz:collab:calendar:self:read";
    private static final String MANAGE = "biz:collab:calendar:manage";

    private final CalendarService calendarService;

    public CollabCalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/calendars")
    public ApiResponse<List<CalendarView>> listCalendars(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(calendarService.listCalendars());
    }

    @PostMapping("/calendars")
    public ApiResponse<CalendarView> createCalendar(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CalendarCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(calendarService.createCalendar(request));
    }

    @PostMapping("/events")
    public ApiResponse<EventView> createEvent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EventCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(calendarService.createEvent(request));
    }

    @GetMapping("/events")
    public ApiResponse<List<EventView>> listEvents(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("startTime") OffsetDateTime startTime,
            @RequestParam("endTime") OffsetDateTime endTime,
            @RequestParam(value = "calendarId", required = false) String calendarId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(calendarService.listEvents(startTime, endTime, calendarId));
    }

    /**
     * 员工自助：只返回本人日历或本人参会的日程，不接受 calendarId 指定他人日历。
     */
    @GetMapping("/events/my")
    public ApiResponse<List<EventView>> myEvents(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("startTime") OffsetDateTime startTime,
            @RequestParam("endTime") OffsetDateTime endTime) {
        PermissionGuard.require(permissions, SELF_READ);
        return ApiResponse.success(calendarService.myEvents(startTime, endTime));
    }

    @GetMapping("/events/{eventId}")
    public ApiResponse<EventView> eventDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("eventId") String eventId) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(calendarService.detail(eventId));
    }

    @PutMapping("/events/{eventId}")
    public ApiResponse<EventView> updateEvent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("eventId") String eventId,
            @RequestBody EventUpdateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(calendarService.updateEvent(eventId, request));
    }

    @PostMapping("/events/{eventId}/cancel")
    public ApiResponse<Void> cancelEvent(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("eventId") String eventId) {
        PermissionGuard.require(permissions, MANAGE);
        calendarService.cancelEvent(eventId);
        return ApiResponse.success(null);
    }

    @PostMapping("/events/free-busy")
    public ApiResponse<FreeBusyView> freeBusy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody FreeBusyRequest request) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(calendarService.freeBusy(
                request == null ? List.of() : request.userIds(),
                request == null ? null : request.startTime(),
                request == null ? null : request.endTime()));
    }

    @PostMapping("/events/suggest-slots")
    public ApiResponse<SlotSuggestion> suggestSlots(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) SuggestSlotsRequest request) {
        PermissionGuard.require(permissions, READ);
        return ApiResponse.success(calendarService.suggestSlots(request));
    }
}
