package com.example.samdi.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements BaseErrorCode{

    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요하거나 만료되었습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 없거나 만료되었습니다."),
    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    EMAIL_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 입력 횟수를 초과했습니다."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 다시 발송하려면 잠시 기다려 주세요."),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증번호 발송에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    REQUEST_ERROR(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드 입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
