package com.fenglin.mdm.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;

/**
 * 往来单位名称归一化：判重口径（可解释、可复现，不设相似度阈值）。
 *
 * 规则顺序：
 * 1. Unicode NFKC（全角转半角、兼容字符统一）
 * 2. 去括号内备注（支持中英文括号，循环处理）
 * 3. 去所有空白（首尾 + 内部，含全角空格）
 * 4. 去标点符号（中英文）
 * 5. 去企业后缀（有限公司/有限责任公司/股份有限公司/集团/公司，循环）
 * 6. 英文转小写
 *
 * 归一化后完全相等 → 判重拦截；不相等 → 只作为疑似候选提示，不自动拦截。
 */
public final class PartnerNameNormalizer {

    private static final List<String> SUFFIXES = List.of(
            "股份有限公司", "有限责任公司", "有限公司", "集团公司", "集团", "公司");

    private static final String PUNCTUATION = "[\\p{Punct}，。、；：·‘’“”《》【】〈〉—－…￥]";

    private PartnerNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        value = stripParentheses(value);
        value = value.replaceAll("[\\s\\u3000]+", "");
        value = value.replaceAll(PUNCTUATION, "");
        value = value.toLowerCase();
        value = stripSuffix(value);
        return value;
    }

    /** dedup_key = sha256(tenant_id + '|' + normalized_name) 十六进制。 */
    public static String dedupKey(long tenantId, String normalizedName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((tenantId + "|" + normalizedName).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String stripParentheses(String value) {
        String previous;
        String current = value;
        do {
            previous = current;
            current = current.replaceAll("[（(][^（）()]*[）)]", "");
        } while (!current.equals(previous));
        return current;
    }

    private static String stripSuffix(String value) {
        String current = value;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : SUFFIXES) {
                if (current.length() > suffix.length() && current.endsWith(suffix)) {
                    current = current.substring(0, current.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        }
        return current;
    }
}
