package com.giki.mess.api;

/**
 * What the boundary returns instead of throwing: either data or a structured error.
 * Callers (UI, web layer) never see a stack trace or an internal class name.
 */
public record ApiResult<T>(boolean success, T data, ErrorInfo error) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, null);
    }

    public static <T> ApiResult<T> failure(String code, String message) {
        return new ApiResult<>(false, null, new ErrorInfo(code, message));
    }
}
