package com.fenglin.common.security;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

/**
 * 口令策略（平台统一）：≥12 位且包含大写、小写、数字、符号；存储使用 BCrypt 成本 12。
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*?";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "口令长度不得少于 12 位");
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
            throw new BizException(CommonErrorCode.BAD_REQUEST, "口令需同时包含大写字母、小写字母、数字和符号");
        }
    }

    public static String encode(String rawPassword) {
        validate(rawPassword);
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && ENCODER.matches(rawPassword, passwordHash);
    }

    /**
     * 生成满足策略的 16 位随机初始口令（用于租户管理员/新用户首次开通）。
     */
    public static String generate() {
        char[] chars = new char[16];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);
        for (int i = 4; i < chars.length; i++) {
            chars[i] = pick(ALL);
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
