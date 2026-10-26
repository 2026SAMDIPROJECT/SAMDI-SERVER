package com.example.samdi.domain.user.dto.response;

public record SignupResponse(
        Long userId,
        String email,
        String loginId
) {
}
