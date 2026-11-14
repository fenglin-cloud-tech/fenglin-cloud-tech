package com.fenglin.collab.web;

import com.fenglin.collab.service.ImService;
import com.fenglin.collab.web.dto.CollabDtos.ConversationCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.ConversationDetailView;
import com.fenglin.collab.web.dto.CollabDtos.ConversationView;
import com.fenglin.collab.web.dto.CollabDtos.MemberAddRequest;
import com.fenglin.collab.web.dto.CollabDtos.MemberView;
import com.fenglin.collab.web.dto.CollabDtos.MessagePage;
import com.fenglin.collab.web.dto.CollabDtos.MessageSendRequest;
import com.fenglin.collab.web.dto.CollabDtos.MessageView;
import com.fenglin.collab.web.dto.CollabDtos.ReactionRequest;
import com.fenglin.collab.web.dto.CollabDtos.ReadResult;
import com.fenglin.collab.web.dto.CollabDtos.RevokeRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IM 接口（API-IM-001/002/003/004/005/006/007/008/009/010/011/012/013）。
 */
@RestController
@RequestMapping("/api/collab/v1/im")
public class CollabImController {

    private static final String CHAT = "biz:collab:im:chat";
    private static final String SELF_CHAT = "biz:collab:im:self:chat";
    private static final String GROUP = "biz:collab:im:group:manage";

    private final ImService imService;

    public CollabImController(ImService imService) {
        this.imService = imService;
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationDetailView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ConversationCreateRequest request) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.createConversation(request));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResult<ConversationView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.listConversations(keyword, page, size));
    }

    /**
     * 员工自助：只返回当前登录用户所在的会话，成员范围由服务端收敛。
     */
    @GetMapping("/conversations/my")
    public ApiResponse<PageResult<ConversationView>> myConversations(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_CHAT);
        return ApiResponse.success(imService.listConversations(keyword, page, size));
    }

    @GetMapping("/conversations/{convId}")
    public ApiResponse<ConversationDetailView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.detail(convId));
    }

    @PostMapping("/conversations/{convId}/members")
    public ApiResponse<List<MemberView>> addMembers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId,
            @RequestBody MemberAddRequest request) {
        PermissionGuard.require(permissions, GROUP);
        return ApiResponse.success(imService.addMembers(convId, request));
    }

    @DeleteMapping("/conversations/{convId}/members/{memberId}")
    public ApiResponse<Void> removeMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId,
            @PathVariable("memberId") long memberId) {
        PermissionGuard.require(permissions, GROUP);
        imService.removeMember(convId, memberId);
        return ApiResponse.success(null);
    }

    @PostMapping("/conversations/{convId}/leave")
    public ApiResponse<Void> leave(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId) {
        PermissionGuard.require(permissions, CHAT);
        imService.leave(convId);
        return ApiResponse.success(null);
    }

    @PostMapping("/conversations/{convId}/dissolve")
    public ApiResponse<Void> dissolve(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId) {
        PermissionGuard.require(permissions, CHAT);
        imService.dissolve(convId);
        return ApiResponse.success(null);
    }

    @PostMapping("/conversations/{convId}/messages")
    public ApiResponse<MessageView> send(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId,
            @RequestBody MessageSendRequest request) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.sendMessage(convId, request));
    }

    @GetMapping("/conversations/{convId}/messages")
    public ApiResponse<MessagePage> messages(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("convId") String convId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.listMessages(convId, cursor, size));
    }

    @PostMapping("/messages/{messageId}/revoke")
    public ApiResponse<Void> revoke(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("messageId") long messageId,
            @RequestBody(required = false) RevokeRequest request) {
        PermissionGuard.require(permissions, CHAT);
        imService.revokeMessage(messageId, request == null ? null : request.reason());
        return ApiResponse.success(null);
    }

    @PostMapping("/messages/{messageId}/read")
    public ApiResponse<ReadResult> read(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("messageId") long messageId,
            @RequestParam("conversationId") String conversationId) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.markRead(conversationId, messageId));
    }

    @PostMapping("/messages/{messageId}/reactions")
    public ApiResponse<Void> react(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("messageId") long messageId,
            @RequestBody ReactionRequest request) {
        PermissionGuard.require(permissions, CHAT);
        imService.react(messageId, request == null ? null : request.emoji());
        return ApiResponse.success(null);
    }

    @GetMapping("/messages/search")
    public ApiResponse<PageResult<MessageView>> search(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "senderId", required = false) Long senderId,
            @RequestParam(value = "msgType", required = false) String msgType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startMs", required = false) Long startMs,
            @RequestParam(value = "endMs", required = false) Long endMs,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CHAT);
        return ApiResponse.success(imService.search(conversationId, senderId, msgType, keyword, startMs, endMs,
                page, size));
    }
}
