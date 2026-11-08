package com.fenglin.message.web.dto;

public record TemplateView(String code, String name, String channel, String titleTpl, String contentTpl,
                           String variables, String locale, String status) {
}
