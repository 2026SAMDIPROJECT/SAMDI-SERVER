package com.example.samdi.domain.auth.email;

import com.example.samdi.global.exception.ErrorCode;

public class EmailSendException extends RuntimeException {
    private final ErrorCode errorCode;

    public EmailSendException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
