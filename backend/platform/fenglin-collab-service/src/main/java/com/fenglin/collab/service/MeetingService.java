package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.MeetingMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.CollabCrypto;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.ActionCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ActionItemRequest;
import com.fenglin.collab.web.dto.CollabDtos.ActionResult;
import com.fenglin.collab.web.dto.CollabDtos.ActionView;
import com.fenglin.collab.web.dto.CollabDtos.CheckinResult;
import com.fenglin.collab.web.dto.CollabDtos.CheckinStats;
import com.fenglin.collab.web.dto.CollabDtos.InviteView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.MeetingDetailView;
import com.fenglin.collab.web.dto.CollabDtos.MeetingView;
import com.fenglin.collab.web.dto.CollabDtos.MinutesCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.MinutesResult;
import com.fenglin.collab.web.dto.CollabDtos.MinutesView;
import com.fenglin.collab.web.dto.CollabDtos.ReserveRequest;
import com.fenglin.collab.web.dto.CollabDtos.ReserveResult;
import com.fenglin.collab.web.dto.CollabDtos.RoomCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.RoomView;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 会议主链：会议室、会议、签到、纪要、行动项转待办；会议室并发预定采用事务级咨询锁 + 时段重叠校验。
 */
@Service
public class MeetingService {

    private static final Set<String> MODES = Set.of("ONLINE", "OFFLINE", "HYBRID");

    private final MeetingMapper meetingMapper;
    private final WorkbenchService workbenchService;
    private final CollabCrypto crypto;
    private final CollabAudit audit;

    public MeetingService(MeetingMapper meetingMapper, WorkbenchService workbenchService,
                          CollabCrypto crypto, CollabAudit audit) {
        this.meetingMapper = meetingMapper;
        this.workbenchService = workbenchService;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public RoomView createRoom(RoomCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "会议室名称不能为空");
        }
        long roomId = IdWorker.getId();
        String equipments = request.equipments() == null || request.equipments().isEmpty()
                ? null : "{" + String.join(",", request.equipments()) + "}";
        meetingMapper.insertRoom(roomId, tenantId, request.name(), request.location(),
                request.capacity() == null ? 0 : request.capacity(), equipments, request.deptId(),
                request.bookable() == null || request.bookable() ? 1 : 0, userId);
        audit.publish("COLLAB_ROOM_CREATED", "CREATE", "collab_meeting_room", roomId, "LOW");
        return new RoomView(roomId, request.name(), request.location(),
                request.capacity() == null ? 0 : request.capacity(), request.equipments(), true, "AVAILABLE");
    }

    @Transactional(readOnly = true)
    public List<RoomView> listRooms(Boolean onlyBookable) {
        long tenantId = CollabContext.tenantId();
        return meetingMapper.listRooms(tenantId, onlyBookable).stream()
                .map(row -> new RoomView(Rows.lngVal(row, "roomId"), Rows.str(row, "name"),
                        Rows.str(row, "location"), Rows.intVal(row, "capacity"), null,
                        Rows.intVal(row, "bookable") == 1, Rows.str(row, "status")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomView> availableRooms(OffsetDateTime startAt, OffsetDateTime endAt) {
        long tenantId = CollabContext.tenantId();
        validateRange(startAt, endAt);
        return meetingMapper.availableRooms(tenantId, startAt, endAt).stream()
                .map(row -> new RoomView(Rows.lngVal(row, "roomId"), Rows.str(row, "name"),
                        Rows.str(row, "location"), Rows.intVal(row, "capacity"), null, true, "AVAILABLE"))
                .toList();
    }

    @Transactional
    public MeetingView create(MeetingCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        return createInternal(tenantId, userId, request.title(), request.startAt(), request.endAt(),
                request.meetingMode(), request.roomId(), request.agenda(), request.externalAllowed(), null);
    }

    /**
     * 供日历域复用：创建会议并按需占用会议室；eventId 非空时写入日程关联。
     */
    @Transactional
    public MeetingView createInternal(long tenantId, long userId, String title, OffsetDateTime startAt,
                                      OffsetDateTime endAt, String meetingMode, Long roomId, String agenda,
                                      Boolean externalAllowed, String eventId) {
        if (title == null || title.isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "会议主题不能为空");
        }
        validateRange(startAt, endAt);
        String mode = meetingMode == null ? "ONLINE" : meetingMode;
        if (!MODES.contains(mode)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "会议模式必须为 ONLINE/OFFLINE/HYBRID");
        }
        if (roomId != null) {
            Map<String, Object> room = meetingMapper.findRoom(tenantId, roomId);
            if (room == null) {
                throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "会议室不存在");
            }
            if (Rows.intVal(room, "bookable") != 1 || !"AVAILABLE".equals(Rows.str(room, "status"))) {
                throw new BizException(CollabErrorCode.ROOM_MAINTENANCE);
            }
        }
        String meetingId = UUID.randomUUID().toString();
        if (roomId != null) {
            meetingMapper.acquireAdvisoryLock("collab-room:" + tenantId + ":" + roomId);
            if (meetingMapper.countRoomOverlap(tenantId, roomId, startAt, endAt) > 0) {
                throw new BizException(CollabErrorCode.ROOM_OCCUPIED);
            }
        }
        String joinCode = String.format("%06d", Math.floorMod(IdWorker.getId(), 1_000_000L));
        String agendaCipher = crypto.encrypt(tenantId, "collab_meeting", "agenda", agenda);
        meetingMapper.insertMeeting(meetingId, tenantId, title, userId, eventId, roomId, startAt, endAt, mode,
                joinCode, "rtc-" + meetingId.substring(0, 8),
                agendaCipher, externalAllowed != null && externalAllowed ? 1 : 0);
        if (roomId != null) {
            meetingMapper.insertReservation(UUID.randomUUID().toString(), tenantId, roomId, meetingId,
                    startAt, endAt, userId);
        }
        audit.publish("COLLAB_MEETING_CREATED", "CREATE", "collab_meeting", meetingId, "LOW");
        return toMeetingView(meetingMapper.findMeeting(tenantId, meetingId));
    }

    @Transactional(readOnly = true)
    public PageResult<MeetingView> list(Integer page, Integer size, String status,
                                        OffsetDateTime from, OffsetDateTime to, boolean invitedOnly) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase();
        List<MeetingView> records = meetingMapper
                .listMeetings(tenantId, userId, invitedOnly, normalized, from, to,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toMeetingView).toList();
        return PageResult.of(records,
                meetingMapper.countMeetings(tenantId, userId, invitedOnly, normalized, from, to),
                safePage, safeSize);
    }

    /** 我受邀的会议（排除本人组织），带自己的参会响应。 */
    @Transactional(readOnly = true)
    public PageResult<InviteView> invites(String response, Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        String normalized = response == null || response.isBlank() ? null : response.toUpperCase();
        List<InviteView> records = meetingMapper
                .listInvites(tenantId, userId, normalized, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new InviteView(Rows.str(row, "meetingId"), Rows.str(row, "title"),
                        Rows.lngVal(row, "organizerId"), Rows.instant(row, "startAt"),
                        Rows.instant(row, "endAt"), Rows.str(row, "meetingMode"), Rows.str(row, "status"),
                        Rows.str(row, "response")))
                .toList();
        return PageResult.of(records, meetingMapper.countInvites(tenantId, userId, normalized),
                safePage, safeSize);
    }

    /** 接受/拒绝邀请：只改本人参会响应，且仅限我确实被邀请的会议。 */
    @Transactional
    public InviteView respondInvite(String meetingId, String response) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        String normalized = response == null ? "" : response.toUpperCase();
        if (!Set.of("ACCEPTED", "DECLINED", "TENTATIVE").contains(normalized)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "参会响应必须为 ACCEPTED/DECLINED/TENTATIVE");
        }
        Map<String, Object> meeting = requireMeeting(tenantId, meetingId);
        int updated = meetingMapper.updateInviteResponse(tenantId, meetingId, userId, normalized);
        if (updated == 0) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅被邀请人可响应邀请");
        }
        audit.publish("COLLAB_MEETING_INVITE_RESPONDED", "UPDATE", "collab_meeting", meetingId, "LOW");
        return new InviteView(meetingId, Rows.str(meeting, "title"), Rows.lngVal(meeting, "organizerId"),
                Rows.instant(meeting, "startAt"), Rows.instant(meeting, "endAt"),
                Rows.str(meeting, "meetingMode"), Rows.str(meeting, "status"), normalized);
    }

    @Transactional(readOnly = true)
    public MeetingDetailView detail(String meetingId) {
        long tenantId = CollabContext.tenantId();
        Map<String, Object> meeting = requireMeeting(tenantId, meetingId);
        List<Map<String, Object>> checkins = meetingMapper.listCheckins(tenantId, meetingId);
        CheckinStats stats = new CheckinStats(checkins.size(), checkins.stream()
                .map(row -> Rows.lngVal(row, "userId")).toList());
        Map<String, Object> minutes = meetingMapper.findLatestMinutes(tenantId, meetingId);
        MinutesView minutesView = minutes == null ? null
                : toMinutesView(tenantId, minutes);
        List<ActionView> actions = minutes == null ? List.of()
                : meetingMapper.listActions(tenantId, Rows.str(minutes, "minutesId")).stream()
                .map(row -> new ActionView(Rows.lngVal(row, "actionId"), Rows.str(row, "content"),
                        Rows.lng(row, "assigneeId"), Rows.instant(row, "dueAt"), Rows.lng(row, "todoId"),
                        Rows.str(row, "status")))
                .toList();
        return new MeetingDetailView(toMeetingView(meeting), stats, minutesView, actions);
    }

    /** 纪要历史：每次保存都会新增版本，按创建时间倒序返回，含解密后的正文。 */
    @Transactional(readOnly = true)
    public List<MinutesView> minutesHistory(String meetingId) {
        long tenantId = CollabContext.tenantId();
        requireMeeting(tenantId, meetingId);
        return meetingMapper.listMinutes(tenantId, meetingId).stream()
                .map(row -> toMinutesView(tenantId, row)).toList();
    }

    private MinutesView toMinutesView(long tenantId, Map<String, Object> row) {
        return new MinutesView(Rows.str(row, "minutesId"), Rows.str(row, "status"),
                Rows.instant(row, "publishedAt"), Rows.str(row, "summary"),
                crypto.decrypt(tenantId, "collab_meeting_minutes", "content", Rows.str(row, "contentCipher")),
                Rows.instant(row, "createdAt"));
    }

    @Transactional
    public CheckinResult checkin(String meetingId, String method) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        requireMeeting(tenantId, meetingId);
        meetingMapper.insertCheckin(IdWorker.getId(), tenantId, meetingId, userId,
                method == null || method.isBlank() ? "SCAN" : method);
        audit.publish("COLLAB_MEETING_CHECKIN", "CREATE", "collab_meeting", meetingId, "LOW");
        return new CheckinResult(true, OffsetDateTime.now(ZoneOffset.UTC).toInstant());
    }

    @Transactional
    public MinutesResult upsertMinutes(String meetingId, MinutesCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> meeting = requireMeeting(tenantId, meetingId);
        requireOrganizer(meeting, userId);
        List<String> parts = new ArrayList<>();
        if (request != null && request.topics() != null) {
            request.topics().forEach(topic -> parts.add("议题：" + topic));
        }
        if (request != null && request.conclusions() != null) {
            request.conclusions().forEach(item -> parts.add("结论：" + item));
        }
        if (request != null && request.content() != null && !request.content().isBlank()) {
            parts.add(request.content());
        }
        String plain = String.join("\n", parts);
        boolean publish = request != null && Boolean.TRUE.equals(request.publish());
        String minutesId = UUID.randomUUID().toString();
        meetingMapper.insertMinutes(minutesId, tenantId, meetingId, request == null ? null : request.templateId(),
                crypto.encrypt(tenantId, "collab_meeting_minutes", "content", plain),
                crypto.summary("TEXT", plain), publish ? "PUBLISHED" : "DRAFT", userId);
        audit.publish("COLLAB_MEETING_MINUTES_SAVED", "CREATE", "collab_meeting_minutes", minutesId, "LOW");
        return new MinutesResult(minutesId, publish ? "PUBLISHED" : "DRAFT");
    }

    /**
     * 行动项转统一待办（来源唯一去重），并把待办 ID 回写行动项。
     */
    @Transactional
    public ActionResult addActions(String meetingId, ActionCreateRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> meeting = requireMeeting(tenantId, meetingId);
        requireOrganizer(meeting, userId);
        if (request == null || request.minutesId() == null || request.actions() == null
                || request.actions().isEmpty()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "行动项不能为空");
        }
        if (meetingMapper.findMinutes(tenantId, request.minutesId()) == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "会议纪要不存在");
        }
        List<Long> todoIds = new ArrayList<>();
        for (ActionItemRequest item : request.actions()) {
            if (item.content() == null || item.content().isBlank()) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "行动项内容不能为空");
            }
            long actionId = IdWorker.getId();
            long assigneeId = item.assigneeId() == null ? userId : item.assigneeId();
            meetingMapper.insertAction(actionId, tenantId, request.minutesId(), item.content(), assigneeId,
                    item.dueAt(), userId);
            long todoId = workbenchService.createTodo(tenantId, assigneeId, "MEETING_ACTION",
                    String.valueOf(actionId), item.content(), item.dueAt(), "NORMAL", userId);
            meetingMapper.linkActionTodo(tenantId, actionId, todoId, userId);
            todoIds.add(todoId);
        }
        audit.publish("COLLAB_MEETING_ACTIONS_CREATED", "CREATE", "collab_meeting_action", meetingId, "LOW");
        return new ActionResult(todoIds);
    }

    @Transactional
    public void publishMinutes(String minutesId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> minutes = meetingMapper.findMinutes(tenantId, minutesId);
        if (minutes == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "会议纪要不存在");
        }
        meetingMapper.publishMinutes(tenantId, minutesId, userId);
        audit.publish("COLLAB_MEETING_MINUTES_PUBLISHED", "UPDATE", "collab_meeting_minutes", minutesId, "LOW");
    }

    @Transactional
    public void cancel(String meetingId) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        Map<String, Object> meeting = requireMeeting(tenantId, meetingId);
        requireOrganizer(meeting, userId);
        meetingMapper.updateMeetingStatus(tenantId, meetingId, "CANCELLED", userId);
        meetingMapper.releaseReservation(tenantId, meetingId, userId);
        audit.publish("COLLAB_MEETING_CANCELLED", "UPDATE", "collab_meeting", meetingId, "MEDIUM");
    }

    /**
     * 会议室独立预定（API-MTG-007）：未绑定会议时也占用时段，返回 reservationId。
     */
    @Transactional
    public ReserveResult reserve(Long roomId, ReserveRequest request) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (roomId == null || request == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "会议室与预定时间不能为空");
        }
        validateRange(request.startAt(), request.endAt());
        Map<String, Object> room = meetingMapper.findRoom(tenantId, roomId);
        if (room == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "会议室不存在");
        }
        if (Rows.intVal(room, "bookable") != 1 || !"AVAILABLE".equals(Rows.str(room, "status"))) {
            throw new BizException(CollabErrorCode.ROOM_MAINTENANCE);
        }
        meetingMapper.acquireAdvisoryLock("collab-room:" + tenantId + ":" + roomId);
        if (meetingMapper.countRoomOverlap(tenantId, roomId, request.startAt(), request.endAt()) > 0) {
            throw new BizException(CollabErrorCode.ROOM_OCCUPIED);
        }
        String reservationId = UUID.randomUUID().toString();
        meetingMapper.insertReservation(reservationId, tenantId, roomId, request.meetingId(), request.startAt(),
                request.endAt(), userId);
        audit.publish("COLLAB_ROOM_RESERVED", "CREATE", "collab_room_reservation", reservationId, "LOW");
        return new ReserveResult(reservationId, "RESERVED");
    }

    private void validateRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private Map<String, Object> requireMeeting(long tenantId, String meetingId) {
        Map<String, Object> meeting = meetingMapper.findMeeting(tenantId, meetingId);
        if (meeting == null) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "会议不存在");
        }
        return meeting;
    }

    private void requireOrganizer(Map<String, Object> meeting, long userId) {
        if (Rows.lngVal(meeting, "organizerId") != userId) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "仅会议组织者可操作");
        }
    }

    private MeetingView toMeetingView(Map<String, Object> row) {
        return new MeetingView(Rows.str(row, "meetingId"), Rows.str(row, "title"),
                Rows.lngVal(row, "organizerId"), Rows.lng(row, "roomId"), Rows.instant(row, "startAt"),
                Rows.instant(row, "endAt"), Rows.str(row, "meetingMode"), Rows.str(row, "joinCode"),
                Rows.str(row, "status"));
    }
}
