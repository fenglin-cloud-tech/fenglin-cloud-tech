package com.fenglin.message.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 站内 WS 推送：网关暂未定义 WS 路由与令牌传递契约（缺口），
 * 首版以 handshake 查询参数 tenant_id/user_id 标识会话，由调用方保证来源可信。
 */
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private static final Pattern NUMERIC = Pattern.compile("\\d{1,19}");
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tenantId = query(session, "tenant_id");
        String userId = query(session, "user_id");
        if (tenantId == null || userId == null || !NUMERIC.matcher(tenantId).matches()
                || !NUMERIC.matcher(userId).matches()) {
            session.close();
            return;
        }
        sessions.put(key(Long.parseLong(tenantId), Long.parseLong(userId)), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }

    public boolean isOnline(long tenantId, long userId) {
        WebSocketSession session = sessions.get(key(tenantId, userId));
        return session != null && session.isOpen();
    }

    public boolean push(long tenantId, long userId, String payload) {
        WebSocketSession session = sessions.get(key(tenantId, userId));
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (Exception e) {
            sessions.remove(key(tenantId, userId));
            return false;
        }
    }

    private String query(WebSocketSession session, String name) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }

    private String key(long tenantId, long userId) {
        return tenantId + ":" + userId;
    }
}
