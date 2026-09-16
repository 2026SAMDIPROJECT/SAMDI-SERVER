package com.example.samdi.wallet.dto;

import com.example.samdi.wallet.domain.Wallet;

public record WalletResponse(Long balance, Long totalIncome, Long totalOutcome, Long walletVersion) {

    public WalletResponse(Long balance) {
        this(balance, null, null, null);
    }

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getBalance(), null, null, wallet.getVersion());
    }
}
