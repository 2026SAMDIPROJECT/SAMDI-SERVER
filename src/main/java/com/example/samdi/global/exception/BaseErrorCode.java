package com.example.samdi.global.exception;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    String getCode();
    String getMessage();
    HttpStatus getHttpStatus();
}
