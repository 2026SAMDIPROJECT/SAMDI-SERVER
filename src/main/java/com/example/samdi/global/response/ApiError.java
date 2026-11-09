package com.example.samdi.global.response;

import java.util.List;

public record ApiError(
        String code,
        List<ErrorDetail> details
) {
}
