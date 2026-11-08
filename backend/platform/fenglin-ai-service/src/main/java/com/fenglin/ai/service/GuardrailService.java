package com.fenglin.ai.service;

import com.fenglin.ai.web.dto.AiDtos.GuardrailHit;
import com.fenglin.ai.web.dto.AiDtos.GuardrailResult;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HUB-API-1305 护栏检测：规则引擎（提示词注入 / 敏感数据 / 有害内容）。
 * 文档未定义规则清单，本实现按平台安全基线内置规则并返回命中的 type+rule。
 */
@Service
public class GuardrailService {

    private static final List<Rule> RULES = List.of(
            new Rule("PROMPT_INJECTION", "override_instruction",
                    Pattern.compile("(ignore|忽略|无视).{0,12}(previous|above|之前|以上|前面).{0,12}(instruction|prompt|指令|提示)",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("PROMPT_INJECTION", "system_prompt_leak",
                    Pattern.compile("(system\\s*prompt|系统提示词|你的设定|developer\\s*mode|开发者模式)",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("PROMPT_INJECTION", "role_hijack",
                    Pattern.compile("(you are now|从现在开始你是|假装你是|act as an? )",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("SENSITIVE_DATA", "id_card",
                    Pattern.compile("\\b\\d{17}[\\dXx]\\b")),
            new Rule("SENSITIVE_DATA", "bank_card",
                    Pattern.compile("\\b\\d{16,19}\\b")),
            new Rule("SENSITIVE_DATA", "mobile_phone",
                    Pattern.compile("\\b1[3-9]\\d{9}\\b")),
            new Rule("HARMFUL_CONTENT", "self_harm",
                    Pattern.compile("(自杀|自残|结束生命|suicide)", Pattern.CASE_INSENSITIVE)));

    public GuardrailResult check(String content, String direction) {
        if (content == null || content.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "content 不能为空");
        }
        String normalized = direction == null ? "INPUT" : direction.trim().toUpperCase();
        if (!"INPUT".equals(normalized) && !"OUTPUT".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "direction 仅支持 INPUT/OUTPUT");
        }
        List<GuardrailHit> hits = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(content).find()) {
                hits.add(new GuardrailHit(rule.type(), rule.rule()));
            }
        }
        boolean blocked = !hits.isEmpty();
        return new GuardrailResult(blocked, hits, blocked ? "BLOCK" : "PASS");
    }

    private record Rule(String type, String rule, Pattern pattern) {
    }
}
