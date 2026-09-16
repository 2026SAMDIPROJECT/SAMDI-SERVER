package com.example.samdi.wallet.exception;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(Long userId) {
        super("유저를 찾을 수 없습니다. userId=" + userId);
    }
}
