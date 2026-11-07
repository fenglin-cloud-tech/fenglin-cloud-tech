package com.fenglin.common.core;

import java.time.Instant;

/**
 * 统一响应体。code 为 "0" 表示成功。
 */
public record ApiResponse<T>(String code, String message, T data, String requestId, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CommonErrorCode.SUCCESS.code(), CommonErrorCode.SUCCESS.message(), data,
                RequestContext.requestId(), Instant.now());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.message());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, RequestContext.requestId(), Instant.now());
    }
}
