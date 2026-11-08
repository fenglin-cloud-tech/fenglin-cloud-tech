package com.fenglin.message.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.message.service.MessageService;
import com.fenglin.message.web.dto.MessageListResult;
import com.fenglin.message.web.dto.PreferenceRequest;
import com.fenglin.message.web.dto.PreferenceView;
import com.fenglin.message.web.dto.ReadResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MessageController {

    private static final String PREFERENCE_PERMISSION = "biz:message:preference:manage";

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/api/hub/v1/messages")
    public ApiResponse<MessageListResult> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String channel,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.success(messageService.list(status, channel, page, size));
    }

    @PostMapping("/api/hub/v1/messages/{id}:read")
    public ApiResponse<ReadResult> read(@PathVariable long id) {
        return ApiResponse.success(messageService.markRead(id));
    }

    @GetMapping("/api/hub/v1/messages/preferences")
    public ApiResponse<List<PreferenceView>> preferences() {
        return ApiResponse.success(messageService.preferences());
    }

    @PutMapping("/api/hub/v1/messages/preferences")
    public ApiResponse<List<PreferenceView>> updatePreferences(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody PreferenceRequest request) {
        PermissionGuard.require(permissions, PREFERENCE_PERMISSION);
        return ApiResponse.success(messageService.updatePreferences(request));
    }
}
