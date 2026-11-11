package com.fenglin.auth.web.dto;

/**
 * 自助改密结果：revokedSessions 为被吊销的其他在线会话数（当前会话保留）。
 */
public record ChangePasswordResult(boolean changed, int revokedSessions) {
}
