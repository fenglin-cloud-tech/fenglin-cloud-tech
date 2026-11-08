package com.fenglin.collab.web;

import com.fenglin.collab.service.MeetingService;
import com.fenglin.collab.web.dto.CollabDtos.ActionCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ActionResult;
import com.fenglin.collab.web.dto.CollabDtos.CheckinRequest;
import com.fenglin.collab.web.dto.CollabDtos.CheckinResult;
import com.fenglin.collab.web.dto.CollabDtos.MeetingCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.MeetingDetailView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingView;
import com.fenglin.collab.web.dto.CollabDtos.MinutesCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.MinutesResult;
import com.fenglin.collab.web.dto.CollabDtos.ReserveRequest;
import com.fenglin.collab.web.dto.CollabDtos.ReserveResult;
import com.fenglin.collab.web.dto.CollabDtos.RoomCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.RoomView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会议接口（API-MTG-001 ~ 009）。
 */
@RestController
@RequestMapping("/api/collab/v1")
public class CollabMeetingController {

    private static final String JOIN = "biz:collab:meeting:join";
    private static final String MANAGE = "biz:collab:meeting:manage";
    private static final String ROOM_ADMIN = "tenant:collab:meeting:room:manage";

    private final MeetingService meetingService;

    public CollabMeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping("/meetings")
    public ApiResponse<MeetingView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody MeetingCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(meetingService.create(request));
    }

    @GetMapping("/meetings")
    public ApiResponse<PageResult<MeetingView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, JOIN);
        return ApiResponse.success(meetingService.list(page, size));
    }

    @GetMapping("/meetings/{meetingId}")
    public ApiResponse<MeetingDetailView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("meetingId") String meetingId) {
        PermissionGuard.require(permissions, JOIN);
        return ApiResponse.success(meetingService.detail(meetingId));
    }

    @PostMapping("/meetings/{meetingId}/checkin")
    public ApiResponse<CheckinResult> checkin(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("meetingId") String meetingId,
            @RequestBody(required = false) CheckinRequest request) {
        PermissionGuard.require(permissions, JOIN);
        return ApiResponse.success(meetingService.checkin(meetingId, request == null ? null : request.method()));
    }

    @PostMapping("/meetings/{meetingId}/minutes")
    public ApiResponse<MinutesResult> minutes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("meetingId") String meetingId,
            @RequestBody(required = false) MinutesCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(meetingService.upsertMinutes(meetingId, request));
    }

    @PostMapping("/meetings/{meetingId}/actions")
    public ApiResponse<ActionResult> actions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("meetingId") String meetingId,
            @RequestBody ActionCreateRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(meetingService.addActions(meetingId, request));
    }

    @PostMapping("/meetings/{meetingId}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("meetingId") String meetingId) {
        PermissionGuard.require(permissions, MANAGE);
        meetingService.cancel(meetingId);
        return ApiResponse.success(null);
    }

    @GetMapping("/meeting-rooms")
    public ApiResponse<List<RoomView>> rooms(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "bookable", required = false) Boolean bookable) {
        PermissionGuard.require(permissions, JOIN);
        return ApiResponse.success(meetingService.listRooms(bookable));
    }

    @GetMapping("/meeting-rooms/available")
    public ApiResponse<List<RoomView>> available(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("startTime") OffsetDateTime startTime,
            @RequestParam("endTime") OffsetDateTime endTime) {
        PermissionGuard.require(permissions, JOIN);
        return ApiResponse.success(meetingService.availableRooms(startTime, endTime));
    }

    @PostMapping("/meeting-rooms")
    public ApiResponse<RoomView> createRoom(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RoomCreateRequest request) {
        PermissionGuard.require(permissions, ROOM_ADMIN);
        return ApiResponse.success(meetingService.createRoom(request));
    }

    @PostMapping("/meeting-rooms/{roomId}/reserve")
    public ApiResponse<ReserveResult> reserve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("roomId") long roomId,
            @RequestBody ReserveRequest request) {
        PermissionGuard.require(permissions, MANAGE);
        return ApiResponse.success(meetingService.reserve(roomId, request));
    }
}
