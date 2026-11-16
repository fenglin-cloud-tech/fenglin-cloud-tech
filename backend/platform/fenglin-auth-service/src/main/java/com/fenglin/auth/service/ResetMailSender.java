package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

/**
 * PLAT-22：口令重置邮件通道。SMTP 全部来自配置/环境变量（FENGLIN_SMTP_*），不硬编码。
 * 未启用或未配置 host 时 {@link #enabled()} 为 false，forgot 接口走“管理员生成链接”降级路径。
 */
@Component
public class ResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(ResetMailSender.class);

    private final AuthProperties properties;
    private final JavaMailSenderImpl sender;

    public ResetMailSender(AuthProperties properties) {
        this.properties = properties;
        AuthProperties.PasswordReset config = properties.getPasswordReset();
        boolean configured = config.isMailEnabled()
                && config.getMailHost() != null && !config.getMailHost().isBlank();
        if (!configured) {
            this.sender = null;
            return;
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(config.getMailHost());
        impl.setPort(config.getMailPort());
        if (config.getMailUsername() != null && !config.getMailUsername().isBlank()) {
            impl.setUsername(config.getMailUsername());
            impl.setPassword(config.getMailPassword());
        }
        impl.setDefaultEncoding("UTF-8");
        Properties mail = impl.getJavaMailProperties();
        mail.put("mail.transport.protocol", config.isMailSsl() ? "smtps" : "smtp");
        mail.put("mail.smtp.auth", String.valueOf(config.getMailUsername() != null
                && !config.getMailUsername().isBlank()));
        mail.put("mail.smtp.ssl.enable", String.valueOf(config.isMailSsl()));
        mail.put("mail.smtp.starttls.enable", String.valueOf(!config.isMailSsl()));
        mail.put("mail.smtp.connectiontimeout", "5000");
        mail.put("mail.smtp.timeout", "5000");
        mail.put("mail.smtp.writetimeout", "5000");
        this.sender = impl;
    }

    public boolean enabled() {
        return sender != null;
    }

    public void send(String to, String resetUrl, Duration ttl) {
        if (sender == null) {
            throw new IllegalStateException("SMTP 通道未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getPasswordReset().getMailFrom());
        message.setTo(to);
        message.setSubject("【丰麟云枢】登录口令重置链接");
        message.setText("您好：\n\n请在 " + Math.max(1, ttl.toMinutes()) + " 分钟内打开以下链接重置登录口令"
                + "（链接仅可使用一次，逾期或使用后立即失效）：\n\n" + resetUrl
                + "\n\n如非本人操作，请忽略本邮件，并联系管理员检查账号安全。\n");
        sender.send(message);
        log.info("口令重置邮件已发送 to={}", mask(to));
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
