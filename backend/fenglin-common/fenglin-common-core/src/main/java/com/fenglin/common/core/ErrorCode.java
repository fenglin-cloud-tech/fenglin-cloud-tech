package com.fenglin.common.core;

/**
 * 统一错误码契约。错误码格式：服务前缀 + 4 位数字，平台公共码使用 FL-COMMON 前缀。
 */
public interface ErrorCode {

    String code();

    String message();

    int httpStatus();
}
