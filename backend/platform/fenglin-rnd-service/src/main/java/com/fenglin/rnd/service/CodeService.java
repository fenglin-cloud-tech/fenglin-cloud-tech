package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CodeMapper;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.RndCrypto;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.CommitIngestRequest;
import com.fenglin.rnd.web.dto.RndDtos.CommitLinkRequest;
import com.fenglin.rnd.web.dto.RndDtos.CommitView;
import com.fenglin.rnd.web.dto.RndDtos.RepoCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RepoUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RepoView;
import com.fenglin.rnd.web.dto.RndDtos.SyncResult;
import com.fenglin.rnd.web.dto.RndDtos.WebhookEventView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * E1 代码：代码库凭据加密、提交接收、提交-工作项关联与 Webhook 处理。
 */
@Service
@Transactional(readOnly = true)
public class CodeService {

    private static final String TOKEN_TABLE = "devops_repo";

    private final CodeMapper codeMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final RndCrypto crypto;
    private final RndAudit audit;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public CodeService(CodeMapper codeMapper, CommonsMapper commonsMapper, ProductService productService,
                       RndCrypto crypto, RndAudit audit, ObjectMapper objectMapper,
                       @Value("${fenglin.rnd.webhook-secret:fenglin-rnd-webhook-dev}") String webhookSecret) {
        this.codeMapper = codeMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.crypto = crypto;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public RepoView createRepo(RepoCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        String token = RndContext.requireText(request.accessToken(), "accessToken");
        long id = IdWorker.getId();
        codeMapper.insertRepo(id, tenantId, request.productId(),
                RndContext.requireText(request.repoName(), "repoName"),
                request.vcsType() == null ? "gitlab" : request.vcsType(),
                RndContext.requireText(request.repoUrl(), "repoUrl"),
                request.defaultBranch() == null ? "main" : request.defaultBranch(),
                crypto.encrypt(tenantId, TOKEN_TABLE, "access_token", token), crypto.hash(tenantId, token),
                crypto.encrypt(tenantId, TOKEN_TABLE, "webhook_secret", request.webhookSecret()), userId);
        audit.publish("REPO_CREATED", "create", "devops_repo", id, "medium");
        return getRepo(id);
    }

    public RepoView getRepo(long id) {
        Map<String, Object> row = codeMapper.findRepo(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toRepoView(row);
    }

    public List<RepoView> listRepos(long productId) {
        return codeMapper.listRepos(RndContext.tenantId(), productId).stream()
                .map(this::toRepoView).toList();
    }

    @Transactional
    public RepoView updateRepo(long id, RepoUpdateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (codeMapper.findRepo(tenantId, id) == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        String token = RndContext.text(request.accessToken());
        codeMapper.updateRepo(tenantId, id, RndContext.text(request.repoName()),
                RndContext.text(request.defaultBranch()),
                token == null ? null : crypto.encrypt(tenantId, TOKEN_TABLE, "access_token", token),
                token == null ? null : crypto.hash(tenantId, token),
                request.webhookSecret() == null ? null
                        : crypto.encrypt(tenantId, TOKEN_TABLE, "webhook_secret", request.webhookSecret()),
                userId);
        audit.publish("REPO_UPDATED", "update", "devops_repo", id, "medium");
        return getRepo(id);
    }

    @Transactional
    public void deleteRepo(long id) {
        long tenantId = RndContext.tenantId();
        if (codeMapper.deleteRepo(tenantId, id, RndContext.userId()) == 0) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        audit.publish("REPO_DELETED", "delete", "devops_repo", id, "medium");
    }

    @Transactional
    public SyncResult syncRepo(long id) {
        long tenantId = RndContext.tenantId();
        getRepo(id);
        codeMapper.markSynced(tenantId, id, RndContext.userId());
        long commits = codeMapper.countEffectiveCommitCandidates(tenantId, id);
        audit.publish("REPO_SYNCED", "sync", "devops_repo", id, "low");
        return new SyncResult(id, "ok", (int) commits, Instant.now().toString());
    }

    @Transactional
    public CommitView ingestCommit(CommitIngestRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.repoId() == null || request.commitSha() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        if (codeMapper.findRepo(tenantId, request.repoId()) == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        String sha = RndContext.requireText(request.commitSha(), "commitSha");
        Map<String, Object> existing = codeMapper.findCommitBySha(tenantId, request.repoId(), sha);
        if (existing != null) {
            return toCommitView(existing);
        }
        int add = request.addLines() == null ? 0 : request.addLines();
        int del = request.delLines() == null ? 0 : request.delLines();
        int files = request.filesChanged() == null ? 0 : request.filesChanged();
        int isMerge = request.isMerge() == null ? 0 : request.isMerge();
        boolean generated = isGenerated(request.message());
        codeMapper.insertCommit(IdWorker.getId(), tenantId, request.repoId(), sha,
                RndContext.text(request.branch()), RndContext.requireText(request.authorName(), "authorName"),
                crypto.hash(tenantId, request.authorEmail()), null, RndContext.text(request.message()),
                add, del, files, isMerge, generated ? 1 : 0, parseInstant(request.committedAt()), userId);
        Map<String, Object> created = codeMapper.findCommitBySha(tenantId, request.repoId(), sha);
        audit.publish("COMMIT_INGESTED", "create", "devops_commit", Rows.lng(created, "id"), "low");
        return toCommitView(created);
    }

    public List<CommitView> listCommits(long repoId, Long authorUserId, Integer page, Integer size) {
        return codeMapper.listCommits(RndContext.tenantId(), repoId, authorUserId,
                        RndContext.size(size), RndContext.offset(page, size))
                .stream().map(CodeService::toCommitView).toList();
    }

    @Transactional
    public CommitView linkCommit(long commitId, CommitLinkRequest request) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> commit = codeMapper.findCommit(tenantId, commitId);
        if (commit == null || request.bizId() == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        codeMapper.insertLink(IdWorker.getId(), tenantId, commitId,
                request.bizType() == null ? "requirement" : request.bizType(), request.bizId(),
                request.linkType() == null ? "manual" : request.linkType(), RndContext.userId());
        audit.publish("COMMIT_LINKED", "link", "devops_commit", commitId, "low");
        return toCommitView(commit);
    }

    @Transactional
    public WebhookEventView handleWebhook(String source, String signature, String payload) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String actual = signature == null ? "" : signature;
        if (!webhookSecret.equals(actual)) {
            throw new BizException(RndErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        String eventType = root.path("object_kind").asText(root.path("event_name").asText("unknown"));
        String eventId = firstText(root, "object_attributes.id", "after", "build_id", "id");
        if (eventId == null) {
            eventId = sha256(payload);
        }
        codeMapper.insertWebhookEvent(IdWorker.getId(), tenantId, source, eventType, eventId, payload, 1,
                "processed", userId);
        if ("push".equals(eventType)) {
            ingestPushPayload(root);
        }
        return toWebhookView(codeMapper.findWebhookEvent(tenantId, source, eventId));
    }

    private static WebhookEventView toWebhookView(Map<String, Object> row) {
        return new WebhookEventView(Rows.lng(row, "id"), Rows.str(row, "source"), Rows.str(row, "eventType"),
                Rows.str(row, "eventId"), Rows.str(row, "processStatus"), Rows.str(row, "createdAt"));
    }

    private void ingestPushPayload(JsonNode root) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String repoUrl = root.path("project").path("git_http_url").asText(null);
        if (repoUrl == null) {
            return;
        }
        JsonNode commits = root.path("commits");
        if (!commits.isArray()) {
            return;
        }
        for (JsonNode commit : commits) {
            String sha = commit.path("id").asText(null);
            if (sha == null) {
                continue;
            }
            // Webhook 仅落库事件；提交明细由同步任务按仓库补充，避免无 repoId 猜测
            audit.publish("WEBHOOK_PUSH_RECEIVED", "receive", "devops_webhook_event", sha, "low");
        }
    }

    private boolean isGenerated(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("[skip ci]") && lower.contains("generated");
    }

    private OffsetDateTime parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value.replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return OffsetDateTime.now();
            }
        }
    }

    private String firstText(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at("/" + pointer.replace('.', '/'));
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private RepoView toRepoView(Map<String, Object> row) {
        String cipherText = Rows.str(row, "tokenCipher");
        String masked = null;
        try {
            masked = crypto.maskToken(crypto.decrypt(RndContext.tenantId(), TOKEN_TABLE, "access_token", cipherText));
        } catch (RuntimeException ignored) {
            masked = "****";
        }
        return new RepoView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "repoName"),
                Rows.str(row, "vcsType"), Rows.str(row, "repoUrl"), Rows.str(row, "defaultBranch"),
                Rows.str(row, "syncStatus"), masked, Rows.str(row, "lastSyncAt"), Rows.str(row, "createdAt"));
    }

    public static CommitView toCommitView(Map<String, Object> row) {
        return new CommitView(Rows.lng(row, "id"), Rows.lng(row, "repoId"), Rows.str(row, "commitSha"),
                Rows.str(row, "branch"), Rows.str(row, "authorName"), Rows.str(row, "message"),
                Rows.intg(row, "addLines"), Rows.intg(row, "delLines"), Rows.intg(row, "filesChanged"),
                Rows.intg(row, "isMerge"), Rows.intg(row, "isExcluded"), Rows.str(row, "committedAt"));
    }
}
