package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.CodeService;
import com.fenglin.rnd.web.dto.RndDtos.CommitIngestRequest;
import com.fenglin.rnd.web.dto.RndDtos.CommitLinkRequest;
import com.fenglin.rnd.web.dto.RndDtos.CommitView;
import com.fenglin.rnd.web.dto.RndDtos.RepoCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RepoUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RepoView;
import com.fenglin.rnd.web.dto.RndDtos.SyncResult;
import com.fenglin.rnd.web.dto.RndDtos.WebhookEventView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * E1 代码：代码库、提交、关联与 Webhook 接收接口。
 */
@RestController
@RequestMapping("/api/rnd/v1")
public class RndCodeController {

    private static final String REPO_MANAGE = "biz:devops:repo:manage";
    private static final String REPO_SYNC = "biz:devops:repo:sync";
    private static final String REPO_VIEW = "biz:devops:repo:view";
    private static final String COMMIT_VIEW = "biz:devops:commit:view";
    private static final String COMMIT_LINK = "biz:devops:commit:link";
    private static final String WEBHOOK = "biz:devops:webhook:receive";

    private final CodeService codeService;

    public RndCodeController(CodeService codeService) {
        this.codeService = codeService;
    }

    @PostMapping("/repos")
    public ApiResponse<RepoView> createRepo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody RepoCreateRequest request) {
        PermissionGuard.require(permissions, REPO_MANAGE);
        return ApiResponse.success(codeService.createRepo(request));
    }

    @GetMapping("/repos")
    public ApiResponse<List<RepoView>> listRepos(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId) {
        PermissionGuard.require(permissions, REPO_VIEW);
        return ApiResponse.success(codeService.listRepos(productId));
    }

    @GetMapping("/repos/{id}")
    public ApiResponse<RepoView> getRepo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REPO_VIEW);
        return ApiResponse.success(codeService.getRepo(id));
    }

    @PutMapping("/repos/{id}")
    public ApiResponse<RepoView> updateRepo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody RepoUpdateRequest request) {
        PermissionGuard.require(permissions, REPO_MANAGE);
        return ApiResponse.success(codeService.updateRepo(id, request));
    }

    @DeleteMapping("/repos/{id}")
    public ApiResponse<Void> deleteRepo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REPO_MANAGE);
        codeService.deleteRepo(id);
        return ApiResponse.success();
    }

    @PostMapping("/repos/{id}/sync")
    public ApiResponse<SyncResult> syncRepo(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REPO_SYNC);
        return ApiResponse.success(codeService.syncRepo(id));
    }

    @PostMapping("/commits")
    public ApiResponse<CommitView> ingestCommit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CommitIngestRequest request) {
        PermissionGuard.require(permissions, REPO_SYNC);
        return ApiResponse.success(codeService.ingestCommit(request));
    }

    @GetMapping("/commits")
    public ApiResponse<List<CommitView>> listCommits(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("repoId") long repoId,
            @RequestParam(value = "authorUserId", required = false) Long authorUserId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, COMMIT_VIEW);
        return ApiResponse.success(codeService.listCommits(repoId, authorUserId, page, size));
    }

    @PostMapping("/commits/{id}/links")
    public ApiResponse<CommitView> linkCommit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody CommitLinkRequest request) {
        PermissionGuard.require(permissions, COMMIT_LINK);
        return ApiResponse.success(codeService.linkCommit(id, request));
    }

    @PostMapping(value = "/webhooks/{source}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<WebhookEventView> receiveWebhook(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @PathVariable("source") String source,
            @RequestBody String payload) {
        PermissionGuard.require(permissions, WEBHOOK);
        return ApiResponse.success(codeService.handleWebhook(source, signature, payload));
    }
}
