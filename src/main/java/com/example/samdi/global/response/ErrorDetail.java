package com.example.samdi.global.response;

public record ErrorDetail(
        String field,
        String reason
) {
}
