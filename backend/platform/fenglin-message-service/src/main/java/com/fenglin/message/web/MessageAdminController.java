package com.fenglin.message.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.message.service.MessageChannelService;
import com.fenglin.message.service.MessageTemplateService;
import com.fenglin.message.web.dto.ChannelDtos.ChannelRequest;
import com.fenglin.message.web.dto.ChannelDtos.ChannelView;
import com.fenglin.message.web.dto.TemplateRequest;
import com.fenglin.message.web.dto.TemplateView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MessageAdminController {

    private static final String MANAGE_PERMISSION = "tenant:message:template:manage";
    private static final String CHANNEL_MANAGE = "tenant:message:channel:manage";

    private final MessageTemplateService templateService;
    private final MessageChannelService channelService;

    public MessageAdminController(MessageTemplateService templateService, MessageChannelService channelService) {
        this.templateService = templateService;
        this.channelService = channelService;
    }

    @GetMapping("/api/hub/v1/admin/message/templates")
    public ApiResponse<List<TemplateView>> list() {
        return ApiResponse.success(templateService.list());
    }

    @PostMapping("/api/hub/v1/admin/message/templates")
    public ApiResponse<TemplateView> upsert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody TemplateRequest request) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        return ApiResponse.success(templateService.upsert(request));
    }

    @GetMapping("/api/hub/v1/admin/message/channels")
    public ApiResponse<List<ChannelView>> listChannels(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, CHANNEL_MANAGE);
        return ApiResponse.success(channelService.list());
    }

    @PostMapping("/api/hub/v1/admin/message/channels")
    public ApiResponse<ChannelView> saveChannel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ChannelRequest request) {
        PermissionGuard.require(permissions, CHANNEL_MANAGE);
        return ApiResponse.success(channelService.save(request));
    }

    @PostMapping("/api/hub/v1/admin/message/channels/{id}/toggle")
    public ApiResponse<ChannelView> toggleChannel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestParam(value = "enabled", required = false, defaultValue = "true") boolean enabled) {
        PermissionGuard.require(permissions, CHANNEL_MANAGE);
        return ApiResponse.success(channelService.toggle(id, enabled));
    }

    @DeleteMapping("/api/hub/v1/admin/message/channels/{id}")
    public ApiResponse<Void> deleteChannel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CHANNEL_MANAGE);
        channelService.delete(id);
        return ApiResponse.success(null);
    }
}
