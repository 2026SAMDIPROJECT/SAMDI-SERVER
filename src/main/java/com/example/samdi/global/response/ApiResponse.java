package com.example.samdi.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiResponse<T>(
        boolean success,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiError error
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> failure(String message, ApiError error) {
        return new ApiResponse<>(false, message,null ,error);
    }
}
