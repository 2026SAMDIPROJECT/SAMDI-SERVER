package com.example.samdi.wallet.dto;

import com.example.samdi.wallet.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(BigDecimal amount, LocalDate transactionDate,
                                 TransactionType type, Long referenceId) {
    public long validatedAmount() {
        long value = validateAmount(amount);
        if (transactionDate == null || type == null || referenceId == null || referenceId <= 0) {
            throw new IllegalArgumentException("transactionDate, type, 양의 정수 referenceId는 필수입니다.");
        }
        return value;
    }

    public static long validateAmount(BigDecimal amount) {
        // JSON 숫자를 double로 바꾸면 큰 금액의 정밀도가 손실될 수 있어 BigDecimal로 원 단위 정수를 검증한다.
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0) {
            throw new IllegalArgumentException("금액은 원 단위 양의 정수여야 합니다.");
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("금액이 처리 가능한 정수 범위를 초과합니다.");
        }
    }
}
