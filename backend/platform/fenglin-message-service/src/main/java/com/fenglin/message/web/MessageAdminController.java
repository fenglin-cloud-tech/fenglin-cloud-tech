package com.fenglin.message.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.message.service.MessageTemplateService;
import com.fenglin.message.web.dto.TemplateRequest;
import com.fenglin.message.web.dto.TemplateView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MessageAdminController {

    private static final String MANAGE_PERMISSION = "tenant:message:template:manage";

    private final MessageTemplateService templateService;

    public MessageAdminController(MessageTemplateService templateService) {
        this.templateService = templateService;
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
}
