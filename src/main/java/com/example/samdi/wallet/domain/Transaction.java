package com.example.samdi.wallet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// (type, referenceId) 유니크 제약으로 같은 요청이 두 번 저장되는 것을 막는다.
// referenceId가 null이면 MariaDB의 UNIQUE 제약이 중복을 막지 않으므로, 수동 등록 API에서는 반드시 양수를 받는다.
@Table(name = "wallet_transaction", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "reference_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long walletId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private Long balanceAfter; // 거래 후 잔액

    private Long referenceId; // 어떤 배달/이벤트 때문인지 (선택)

    private LocalDateTime createdAt;

    public Transaction(Long walletId, Long amount, TransactionType type,
                       Long balanceAfter, Long referenceId) {
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.balanceAfter = balanceAfter;
        this.referenceId = referenceId;
        this.createdAt = LocalDateTime.now();
    }

    public void revise(Long signedAmount, java.time.LocalDate transactionDate) {
        if (signedAmount == null || signedAmount == 0 || transactionDate == null
                || (signedAmount > 0) != type.isIncome()) {
            throw new IllegalArgumentException("거래 유형에 맞는 금액과 거래일이 필요합니다.");
        }
        // 별도 거래일 컬럼을 추가하지 않기 위해 기존 createdAt에 해당 날짜의 자정을 저장한다.
        this.amount = signedAmount;
        this.createdAt = transactionDate.atStartOfDay();
    }

    public void updateBalanceAfter(Long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }
}
