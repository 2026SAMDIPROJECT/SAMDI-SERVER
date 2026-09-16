package com.example.samdi.wallet.domain;

import com.example.samdi.wallet.exception.InsufficientBalanceException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallet", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long balance;

    @Version
    private Long version;

    public Wallet(Long userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    public void increase(Long amount) {
        validateAmount(amount);
        this.balance = Math.addExact(this.balance, amount);
    }

    public void decrease(Long amount) {
        validateAmount(amount);
        if (this.balance < amount) {
            throw new InsufficientBalanceException(userId, balance, amount);
        }
        this.balance -= amount;
    }

    // 수동 입출금 기록은 가계부 성격이므로 음수 잔액을 허용한다.
    // 잔액 부족을 막는 게임 결제용 decrease와 의도적으로 다른 규칙이다.
    public void reconcileRecordedBalance(long balance) {
        this.balance = balance;
    }

    // null이거나 0 이하인 금액으로 잔액이 조작되는 것을 방지
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다. amount=" + amount);
        }
    }
}
