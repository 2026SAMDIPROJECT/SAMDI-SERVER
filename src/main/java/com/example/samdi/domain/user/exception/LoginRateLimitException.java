package com.example.samdi.domain.user.exception;

public class LoginRateLimitException extends RuntimeException {
    public LoginRateLimitException() {
        super(UserErrorCode.LOGIN_RATE_LIMITED.getMessage());
    }
}
