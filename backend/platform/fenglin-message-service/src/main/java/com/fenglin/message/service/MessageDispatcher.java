package com.fenglin.message.service;

import com.fenglin.message.ws.MessageWebSocketHandler;
import org.springframework.stereotype.Component;

/**
 * 通道适配：首版实现 STATION 与 WS；邮件/短信/企微/飞书/钉钉缺少收件地址与供应商契约，
 * 在发送入口按 400 拒绝（见 MessageService），避免静默丢消息。
 */
@Component
public class MessageDispatcher {

    private final MessageWebSocketHandler webSocketHandler;

    public MessageDispatcher(MessageWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public DispatchResult dispatch(long tenantId, long receiverId, String channel,
                                   long messageId, String title, String content) {
        return switch (channel) {
            case "STATION" -> new DispatchResult("SENT", "DELIVERED", null);
            case "WS" -> {
                String payload = "{\"message_id\":" + messageId + ",\"title\":"
                        + quote(title) + ",\"content\":" + quote(content) + "}";
                if (webSocketHandler.push(tenantId, receiverId, payload)) {
                    yield new DispatchResult("SENT", "DELIVERED", null);
                }
                yield new DispatchResult("FAILED", "FAILED", "WS 会话离线");
            }
            default -> new DispatchResult("FAILED", "FAILED", "通道适配器未实现: " + channel);
        };
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    public record DispatchResult(String messageStatus, String receiptStatus, String error) {
    }
}
