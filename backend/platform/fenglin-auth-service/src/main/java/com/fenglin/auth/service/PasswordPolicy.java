package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthErrorCode;
import com.fenglin.common.core.BizException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 口令策略：≥12 位，大小写字母 + 数字 + 符号；存储使用 BCrypt 成本 12（《数据库设计》§7）。
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "口令长度不得少于 12 位");
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (char c : rawPassword.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        if (!(upper && lower && digit && symbol)) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "口令需同时包含大写字母、小写字母、数字和符号");
        }
    }

    public String encode(String rawPassword) {
        validate(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && encoder.matches(rawPassword, passwordHash);
    }
}
