package com.fenglin.message.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.message.service.MessageService;
import com.fenglin.message.web.dto.SendMessageRequest;
import com.fenglin.message.web.dto.SendMessageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalMessageController {

    private final MessageService messageService;

    public InternalMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/internal/v1/message/send")
    public ApiResponse<SendMessageResult> send(@Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(messageService.send(request));
    }
}
