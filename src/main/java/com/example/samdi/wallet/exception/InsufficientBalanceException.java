package com.example.samdi.wallet.exception;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(Long userId, Long balance, Long amount) {
        super("잔액이 부족합니다. userId=" + userId + ", balance=" + balance + ", requested=" + amount);
    }
}
