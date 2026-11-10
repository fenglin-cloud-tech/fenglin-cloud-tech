package com.fenglin.collab.web;

import com.fenglin.collab.service.CollabAdminService;
import com.fenglin.collab.service.OnaService;
import com.fenglin.collab.web.dto.CollabDtos.CalendarCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.CalendarView;
import com.fenglin.collab.web.dto.CollabDtos.ConversationAuditPage;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyView;
import com.fenglin.collab.web.dto.CollabDtos.OnaConfigRequest;
import com.fenglin.collab.web.dto.CollabDtos.OnaConfigView;
import com.fenglin.collab.web.dto.CollabDtos.PeriodUpsertRequest;
import com.fenglin.collab.web.dto.CollabDtos.PeriodView;
import com.fenglin.collab.web.dto.CollabDtos.RoomCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.RoomView;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyView;
import com.fenglin.collab.web.dto.CollabDtos.TemplateCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.TemplateView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 云协管理端（接口文档 API-IM-050/051、API-DOC-050、API-CAL-050、API-MTG-050、
 * API-OKR-050、API-APV-001 管理侧、API-ONA-050）。
 */
@RestController
@RequestMapping("/api/collab/v1/admin")
public class CollabAdminController {

    private static final String IM_SENSITIVE = "tenant:collab:im:sensitive:manage";
    private static final String IM_GOVERN = "tenant:collab:im:govern";
    private static final String DOC_POLICY = "tenant:collab:doc:policy";
    private static final String CAL_PUBLIC = "tenant:collab:calendar:public:manage";
    private static final String ROOM_MANAGE = "tenant:collab:meeting:room:manage";
    private static final String OKR_PERIOD = "tenant:collab:okr:period:manage";
    private static final String APV_TEMPLATE = "tenant:collab:approval:template:manage";
    private static final String ONA_CONFIG = "tenant:collab:ona:config";

    private final CollabAdminService adminService;
    private final OnaService onaService;

    public CollabAdminController(CollabAdminService adminService, OnaService onaService) {
        this.adminService = adminService;
        this.onaService = onaService;
    }

    @GetMapping("/im/sensitive-words")
    public ApiResponse<SensitiveWordPolicyView> sensitiveWords(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, IM_SENSITIVE);
        return ApiResponse.success(adminService.sensitivePolicy());
    }

    @PostMapping("/im/sensitive-words")
    public ApiResponse<SensitiveWordPolicyView> saveSensitiveWords(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody SensitiveWordPolicyRequest request) {
        PermissionGuard.require(permissions, IM_SENSITIVE);
        return ApiResponse.success(adminService.saveSensitivePolicy(request));
    }

    @GetMapping("/im/audit/conversations")
    public ApiResponse<ConversationAuditPage> auditConversations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, IM_GOVERN);
        return ApiResponse.success(adminService.auditConversations(keyword, startTime, endTime, page, size));
    }

    @PutMapping("/docs/policy")
    public ApiResponse<DocPolicyView> saveDocPolicy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DocPolicyRequest request) {
        PermissionGuard.require(permissions, DOC_POLICY);
        return ApiResponse.success(adminService.saveDocPolicy(request));
    }

    @GetMapping("/docs/policy")
    public ApiResponse<DocPolicyView> docPolicy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, DOC_POLICY);
        return ApiResponse.success(adminService.docPolicy());
    }

    @PostMapping("/calendars")
    public ApiResponse<CalendarView> createPublicCalendar(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CalendarCreateRequest request) {
        PermissionGuard.require(permissions, CAL_PUBLIC);
        return ApiResponse.success(adminService.createPublicCalendar(request));
    }

    @PostMapping("/meeting-rooms")
    public ApiResponse<RoomView> createMeetingRoom(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RoomCreateRequest request) {
        PermissionGuard.require(permissions, ROOM_MANAGE);
        return ApiResponse.success(adminService.createMeetingRoom(request));
    }

    @PutMapping("/okr/periods")
    public ApiResponse<PeriodView> upsertPeriod(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PeriodUpsertRequest request) {
        PermissionGuard.require(permissions, OKR_PERIOD);
        return ApiResponse.success(adminService.upsertPeriod(request));
    }

    @PostMapping("/approvals/templates")
    public ApiResponse<TemplateView> createApprovalTemplate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TemplateCreateRequest request) {
        PermissionGuard.require(permissions, APV_TEMPLATE);
        return ApiResponse.success(adminService.createApprovalTemplate(request));
    }

    @GetMapping("/ona/config")
    public ApiResponse<OnaConfigView> onaConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, ONA_CONFIG);
        return ApiResponse.success(onaService.config());
    }

    @PutMapping("/ona/config")
    public ApiResponse<OnaConfigView> saveOnaConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody OnaConfigRequest request) {
        PermissionGuard.require(permissions, ONA_CONFIG);
        return ApiResponse.success(onaService.saveConfig(request));
    }
}
