package com.example.samdi.wallet.dto;

import com.example.samdi.wallet.domain.Transaction;
import com.example.samdi.wallet.domain.TransactionType;
import java.time.LocalDateTime;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        Long amount,
        TransactionType type,
        Long balanceAfter,
        LocalDateTime createdAt,
        LocalDate transactionDate,
        Long referenceId,
        Long walletVersion
) {

    public static TransactionResponse from(Transaction transaction) {
        return from(transaction, null);
    }

    public static TransactionResponse from(Transaction transaction, Long walletVersion) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt(),
                transaction.getCreatedAt().toLocalDate(),
                transaction.getReferenceId(),
                walletVersion
        );
    }
}
