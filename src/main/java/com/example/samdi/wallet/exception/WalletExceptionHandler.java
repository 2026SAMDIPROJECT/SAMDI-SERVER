package com.example.samdi.wallet.exception;

import com.example.samdi.wallet.dto.ErrorResponse;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WalletExceptionHandler {

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingAuthentication(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("인증된 사용자 ID가 필요합니다."));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ErrorResponse> handleStorageConflict(Exception e) {
        // DB 제약이나 잠금 세부 내용을 외부에 노출하지 않고 재시도 가능한 409로 일괄 응답한다.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("중복 요청 또는 변경 충돌입니다. 최신 내역을 확인한 후 다시 시도해 주세요."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("입력 형식이 잘못되었습니다. 금액은 원 단위 정수, 날짜는 YYYY-MM-DD로 입력해 주세요."));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> handleOverflow(ArithmeticException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("금액 합계가 처리 가능한 정수 범위를 초과합니다."));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(e.getMessage()));
    }
}
