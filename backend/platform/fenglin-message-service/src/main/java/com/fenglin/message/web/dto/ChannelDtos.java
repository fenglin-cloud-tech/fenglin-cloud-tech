package com.fenglin.message.web.dto;

import java.util.Map;

/**
 * PLAT-02 消息通道请求/响应。
 */
public final class ChannelDtos {

    private ChannelDtos() {
    }

    public record ChannelRequest(Long id, String channelType, String name, Map<String, Object> config,
                                 Boolean enabled, Boolean isDefault, String remark) {
    }

    public record ChannelView(Long id, String channelType, String name, String config, Boolean enabled,
                              Boolean isDefault, String remark) {
    }
}
