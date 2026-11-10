package com.fenglin.openapi.service;

import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.web.dto.OpenApiDtos.SandboxCallRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.SandboxCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 门户在线联调沙箱：服务端用应用自身密钥构造签名并真实调用开放接口。
 * <p>
 * 安全边界：
 * <ul>
 *   <li>只能调用 hub_api_doc 中已发布且 sandbox_enabled 的接口路径，不接受任意 URL，避免 SSRF；</li>
 *   <li>前置校验应用归属、审核状态、启用状态、权限订阅与密钥有效性（见 {@link OpenApiSandboxPlanner}）；</li>
 *   <li>AppSecret 仅在服务端解密用于签名，响应只回显签名串，不回显密钥；</li>
 *   <li>走真实签名链路，因此会计入配额与调用日志。</li>
 * </ul>
 */
@Service
public class OpenApiSandboxService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSandboxService.class);
    private static final int MAX_BODY_LENGTH = 32 * 1024;
    private static final int MAX_RESPONSE_LENGTH = 8 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OpenApiSandboxPlanner planner;
    private final AuditSink auditSink;
    private final Environment environment;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public OpenApiSandboxService(OpenApiSandboxPlanner planner,
                                 AuditSink auditSink,
                                 Environment environment) {
        this.planner = planner;
        this.auditSink = auditSink;
        this.environment = environment;
    }

    public SandboxCallResponse call(long appId, long ownerId, SandboxCallRequest request) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null || request.apiCode() == null || request.apiCode().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "api_code 不能为空");
        }
        OpenApiSandboxPlanner.SandboxPlan plan = planner.plan(tenantId, appId, ownerId, request.apiCode().trim());

        String body = normalizeBody(request.body());
        byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String signature = OpenApiSignatureService.hmacSha256Hex(plan.secret(),
                plan.method() + plan.path() + timestamp + nonce + OpenApiSignatureService.sha256Hex(bodyBytes));
        String requestId = UUID.randomUUID().toString();

        SandboxHttpResult response = send(plan, timestamp, nonce, signature, requestId, body);
        String responseBody = response.body() == null ? "" : response.body();
        boolean truncated = responseBody.length() > MAX_RESPONSE_LENGTH;
        if (truncated) {
            responseBody = responseBody.substring(0, MAX_RESPONSE_LENGTH);
        }

        audit(tenantId, ownerId, appId, plan.path());
        return new SandboxCallResponse(plan.code(), plan.method(), plan.path(), plan.appKey(), signature, nonce,
                timestamp, requestId, response.status(), response.latencyMs(), responseBody, truncated);
    }

    private SandboxHttpResult send(OpenApiSandboxPlanner.SandboxPlan plan, long timestamp, String nonce,
                                   String signature, String requestId, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + plan.path()))
                .timeout(TIMEOUT)
                .header("X-App-Key", plan.appKey())
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .header("X-Request-Id", requestId)
                .header("Content-Type", "application/json;charset=UTF-8");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(plan.method(), publisher);
        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new SandboxHttpResult(response.statusCode(), response.body(),
                    System.currentTimeMillis() - start);
        } catch (IOException ex) {
            log.error("沙箱调用失败：path={}", plan.path(), ex);
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "沙箱调用失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "沙箱调用被中断");
        }
    }

    private record SandboxHttpResult(int status, String body, long latencyMs) {
    }

    private String normalizeBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请求体不能超过 32KB");
        }
        return trimmed;
    }

    private String baseUrl() {
        String override = environment.getProperty("fenglin.openapi.sandbox-base-url");
        if (override != null && !override.isBlank()) {
            return trimTrailingSlash(override.trim());
        }
        String port = environment.getProperty("local.server.port");
        if (port == null || port.isBlank() || "0".equals(port)) {
            port = environment.getProperty("server.port", "18091");
        }
        return "http://127.0.0.1:" + port;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void audit(long tenantId, long ownerId, long appId, String path) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "OPENAPI_SANDBOX_CALLED", "openapi",
                "EXECUTE", "MEDIUM", tenantId, ownerId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "OPEN_APP", String.valueOf(appId),
                Instant.now()));
        log.info("沙箱调用：tenantId={} appId={} path={}", tenantId, appId, path);
    }
}
