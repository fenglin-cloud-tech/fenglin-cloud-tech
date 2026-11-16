package com.fenglin.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.entity.AccountEntity;
import com.fenglin.auth.entity.PasswordResetTokenEntity;
import com.fenglin.auth.entity.TenantEntity;
import com.fenglin.auth.entity.UserEntity;
import com.fenglin.auth.mapper.AccountMapper;
import com.fenglin.auth.mapper.PasswordResetTokenMapper;
import com.fenglin.auth.mapper.TenantMapper;
import com.fenglin.auth.mapper.UserMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLAT-22：忘记密码 / 一次性重置令牌。
 *
 * <ul>
 *   <li>令牌原文 32 字节：前 8 字节为 tenantId（匿名 reset 时据此进入租户上下文，满足 RLS），后 24 字节随机；</li>
 *   <li>库内只存 SHA-256；30 分钟有效、单次使用、使用即写 used_at；</li>
 *   <li>SMTP 未配置时 forgot 统一返回“通道不可用”，不暴露账号是否存在；管理员可生成链接线下转交。</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String SOURCE_SELF = "SELF";
    private static final String SOURCE_ADMIN = "ADMIN";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_UNAVAILABLE = "UNAVAILABLE";
    private static final String MSG_UNAVAILABLE = "当前未配置邮件通道，请联系管理员生成一次性重置链接";
    private static final String MSG_EMAIL = "如账号存在且已绑定邮箱，重置链接将发送至该邮箱";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int TOKEN_BYTES = 32;
    private static final long REQUEST_INTERVAL_MS = 60_000L;
    private static final int MAX_REQUESTS_PER_IP = 20;
    private static final long IP_WINDOW_MS = 10 * 60_000L;

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final AuthService authService;
    private final ResetMailSender mailSender;
    private final ObjectProvider<AesGcmCipher> cipherProvider;
    private final AuthProperties properties;
    private final AuditSink auditSink;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Long> lastRequestAt = new ConcurrentHashMap<>();
    private final Map<String, IpWindow> ipWindows = new ConcurrentHashMap<>();

    public PasswordResetService(TenantMapper tenantMapper, UserMapper userMapper, AccountMapper accountMapper,
                                PasswordResetTokenMapper tokenMapper, AuthService authService,
                                ResetMailSender mailSender, ObjectProvider<AesGcmCipher> cipherProvider,
                                AuthProperties properties, AuditSink auditSink) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.tokenMapper = tokenMapper;
        this.authService = authService;
        this.mailSender = mailSender;
        this.cipherProvider = cipherProvider;
        this.properties = properties;
        this.auditSink = auditSink;
    }

    /**
     * 自助找回：恒返回同一语义，防枚举。SMTP 未配置时返回 UNAVAILABLE（联系管理员），
     * 配置后无论账号是否存在都返回 EMAIL，仅对真实且可解密的邮箱实际发信。
     */
    @Transactional
    public ForgotResult forgot(String tenantCode, String username, String ip) {
        if (!mailSender.enabled()) {
            return new ForgotResult(CHANNEL_UNAVAILABLE, MSG_UNAVAILABLE);
        }
        // 按 IP 粗粒度限频，避免被当作邮件轰炸入口
        if (!ipAllowed(ip)) {
            return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
        }
        TenantEntity tenant = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getCode, tenantCode)
                .eq(TenantEntity::getDeleted, 0));
        if (tenant == null || !STATUS_ACTIVE.equals(tenant.getStatus())) {
            return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
        }
        TenantContext.set(tenant.getId(), null, "SYSTEM", null, false);
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenant.getId())
                .eq(UserEntity::getUsername, username)
                .eq(UserEntity::getDeleted, 0));
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
        }
        String email = decryptEmail(tenant.getId(), user.getEmailEnc());
        if (email == null) {
            return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
        }
        long now = System.currentTimeMillis();
        Long last = lastRequestAt.get(user.getId());
        if (last != null && now - last < REQUEST_INTERVAL_MS) {
            return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
        }
        lastRequestAt.put(user.getId(), now);

        String raw = issue(tenant.getId(), user.getId(), ip, SOURCE_SELF);
        try {
            mailSender.send(email, resetUrl(raw), properties.getPasswordReset().getTokenTtl());
        } catch (RuntimeException ex) {
            log.warn("口令重置邮件发送失败 tenant={} user={} 原因={}", tenant.getId(), user.getId(), ex.toString());
        }
        audit(tenant.getId(), user.getId(), "PASSWORD_RESET_REQUESTED", "SELF");
        return new ForgotResult(CHANNEL_EMAIL, MSG_EMAIL);
    }

    /** 消费一次性令牌并重置口令：改密、令牌版本 +1、吊销全部会话/刷新令牌。 */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        long tenantId = parseTenant(rawToken);
        TenantContext.set(tenantId, null, "SYSTEM", null, false);
        PasswordPolicy.validate(newPassword);
        String hash = sha256(rawToken);
        PasswordResetTokenEntity row = tokenMapper.selectOne(Wrappers.<PasswordResetTokenEntity>lambdaQuery()
                .eq(PasswordResetTokenEntity::getTenantId, tenantId)
                .eq(PasswordResetTokenEntity::getTokenHash, hash)
                .eq(PasswordResetTokenEntity::getDeleted, 0));
        Instant now = Instant.now();
        if (row == null || row.getUsedAt() != null || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(now)) {
            throw invalidToken();
        }
        UserEntity user = userMapper.selectById(row.getUserId());
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw invalidToken();
        }
        AccountEntity account = accountMapper.selectOne(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getTenantId, tenantId)
                .eq(AccountEntity::getUserId, row.getUserId())
                .eq(AccountEntity::getDeleted, 0));
        if (account == null) {
            throw invalidToken();
        }
        if (tokenMapper.consume(tenantId, hash) != 1) {
            throw invalidToken();
        }
        if (accountMapper.changePassword(tenantId, row.getUserId(), PasswordPolicy.encode(newPassword)) == 0) {
            throw invalidToken();
        }
        userMapper.incrementTokenVersion(tenantId, row.getUserId());
        authService.revokeAllSessions(tenantId, row.getUserId());
        audit(tenantId, row.getUserId(), "PASSWORD_RESET_COMPLETED", row.getRequestSource());
    }

    /** 管理员生成一次性链接（无 SMTP 时的兜底，链接由管理员线下转交）。 */
    @Transactional
    public AdminResetLink createAdminLink(long tenantId, long userId, long operatorId, String ip) {
        TenantContext.set(tenantId, operatorId, "ADMIN", null, false);
        UserEntity user = userMapper.selectById(userId);
        if (user == null || tenantId != user.getTenantId()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        String raw = issue(tenantId, userId, ip, SOURCE_ADMIN);
        audit(tenantId, userId, "PASSWORD_RESET_LINK_GENERATED", "ADMIN:" + operatorId);
        return new AdminResetLink(raw, resetUrl(raw),
                Instant.now().plus(properties.getPasswordReset().getTokenTtl()));
    }

    private String issue(long tenantId, long userId, String ip, String source) {
        byte[] payload = new byte[TOKEN_BYTES];
        ByteBuffer.wrap(payload).putLong(tenantId);
        byte[] tail = new byte[TOKEN_BYTES - Long.BYTES];
        random.nextBytes(tail);
        System.arraycopy(tail, 0, payload, Long.BYTES, tail.length);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(IdWorker.getId());
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setTokenHash(sha256(raw));
        entity.setRequestIp(ip);
        entity.setRequestSource(source);
        entity.setExpiresAt(Instant.now().plus(properties.getPasswordReset().getTokenTtl()));
        entity.setCreatedAt(Instant.now());
        entity.setDeleted(0);
        tokenMapper.insert(entity);
        return raw;
    }

    private long parseTenant(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(rawToken);
            if (payload.length != TOKEN_BYTES) {
                throw invalidToken();
            }
            long tenantId = ByteBuffer.wrap(payload).getLong();
            if (tenantId <= 0) {
                throw invalidToken();
            }
            return tenantId;
        } catch (IllegalArgumentException ex) {
            throw invalidToken();
        }
    }

    private String resetUrl(String rawToken) {
        String base = properties.getPasswordReset().getPortalBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/#/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String decryptEmail(long tenantId, String emailEnc) {
        AesGcmCipher cipher = cipherProvider.getIfAvailable();
        if (cipher == null || emailEnc == null || emailEnc.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(emailEnc, tenantId + ":hub_user:email_enc");
        } catch (RuntimeException ex) {
            log.warn("邮箱解密失败 tenant={} 原因={}", tenantId, ex.toString());
            return null;
        }
    }

    private boolean ipAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        IpWindow window = ipWindows.compute(ip, (key, old) -> {
            if (old == null || now - old.start > IP_WINDOW_MS) {
                return new IpWindow(now, 1);
            }
            return new IpWindow(old.start, old.count + 1);
        });
        return window.count <= MAX_REQUESTS_PER_IP;
    }

    private BizException invalidToken() {
        return new BizException(CommonErrorCode.BAD_REQUEST, "链接无效、已过期或已使用，请重新申请");
    }

    private void audit(long tenantId, long userId, String action, String reason) {
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "AUTH_" + action,
                "auth",
                action,
                "PASSWORD_RESET_COMPLETED".equals(action) ? "MEDIUM" : "LOW",
                tenantId,
                userId,
                "SYSTEM",
                null,
                "AUTH",
                reason,
                Instant.now()));
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record ForgotResult(String channel, String message) {
    }

    public record AdminResetLink(String token, String resetUrl, Instant expiresAt) {
    }

    private record IpWindow(long start, int count) {
    }
}
