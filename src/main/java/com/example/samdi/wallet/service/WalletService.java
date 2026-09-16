package com.example.samdi.wallet.service;

import com.example.samdi.wallet.domain.Transaction;
import com.example.samdi.wallet.domain.TransactionType;
import com.example.samdi.wallet.domain.Wallet;
import com.example.samdi.wallet.exception.WalletNotFoundException;
import com.example.samdi.wallet.repository.TransactionRepository;
import com.example.samdi.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.samdi.wallet.dto.*;
import jakarta.persistence.EntityManager;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final EntityManager entityManager;

    @Transactional
    public void createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 지갑이 존재합니다. userId=" + userId);
        }
        walletRepository.save(new Wallet(userId));
    }

    // 게임 이벤트용 내부 API다. 클라이언트가 금액을 결정하는 컨트롤러에 직접 노출하지 않는다.
    @Transactional
    public Long charge(Long userId, Long amount, TransactionType type, Long referenceId) {
        if (!type.isIncome()) {
            throw new IllegalArgumentException("charge에는 수입 유형만 사용할 수 있습니다. type=" + type);
        }
        Wallet wallet = getWalletForUpdate(userId);
        wallet.increase(amount);
        transactionRepository.save(new Transaction(wallet.getId(), amount, type, wallet.getBalance(), referenceId));
        return wallet.getBalance();
    }
    @Transactional
    public Long deduct(Long userId, Long amount, TransactionType type, Long referenceId) {
        if (type.isIncome()) {
            throw new IllegalArgumentException("deduct에는 지출 유형만 사용할 수 있습니다. type=" + type);
        }
        Wallet wallet = getWalletForUpdate(userId);
        wallet.decrease(amount);
        transactionRepository.save(new Transaction(wallet.getId(), -amount, type, wallet.getBalance(), referenceId));
        return wallet.getBalance();
    }


    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        return getWalletOrThrow(userId).getBalance();
    }

    @Transactional
    public TransactionResponse registerIncome(Long userId, TransactionRequest request) {
        return register(userId, request, true);
    }

    @Transactional
    public TransactionResponse registerOutcome(Long userId, TransactionRequest request) {
        return register(userId, request, false);
    }

    private TransactionResponse register(Long userId, TransactionRequest request, boolean income) {
        long amount = request.validatedAmount();
        if (request.type().isIncome() != income) {
            throw new IllegalArgumentException("입금/지출 구분과 거래 분류(type)가 일치하지 않습니다.");
        }
        long signedAmount = income ? amount : -amount;
        Wallet wallet = getWalletForUpdate(userId);

        // (type, referenceId)를 재시도 키로 사용해 동일 요청의 중복 입출금을 막는다.
        var existing = transactionRepository.findByTypeAndReferenceId(request.type(), request.referenceId());
        if (existing.isPresent()) {
            Transaction transaction = existing.get();

            // 키만 같고 내용이 다르면 재시도가 아니라 충돌이다.
            if (!Objects.equals(transaction.getWalletId(), wallet.getId())
                    || transaction.getAmount() != signedAmount
                    || !transaction.getCreatedAt().toLocalDate().equals(request.transactionDate())) {
                throw new IllegalArgumentException("이미 사용된 요청 식별값입니다. 기존 내역을 확인해 주세요.");
            }
            return TransactionResponse.from(transaction, wallet.getVersion());
        }
        Transaction transaction = new Transaction(wallet.getId(), signedAmount, request.type(), 0L, request.referenceId());
        transaction.revise(signedAmount, request.transactionDate());
        transactionRepository.saveAndFlush(transaction);
        recalculate(wallet);
        return TransactionResponse.from(transaction, wallet.getVersion());
    }

    @Transactional(readOnly = true)
    public WalletResponse getSummary(Long userId) {
        Wallet wallet = getWalletOrThrow(userId);
        long income = 0;
        long outcome = 0;
        for (Transaction transaction : transactionRepository.findByWalletIdOrderByCreatedAtAscIdAsc(wallet.getId())) {
            long amount = transaction.getAmount();
            if (amount > 0) income = Math.addExact(income, amount);
            if (amount < 0) outcome = Math.addExact(outcome, Math.negateExact(amount));
        }
        return new WalletResponse(Math.subtractExact(income, outcome), income, outcome, wallet.getVersion());
    }

    private void recalculate(Wallet wallet) {
        long balance = 0;
        long income = 0;
        long outcome = 0;
        // 과거 날짜의 거래가 등록되면 이후 balanceAfter도 달라지므로 전체를 거래일순으로 재계산한다.
        for (Transaction transaction : transactionRepository.findByWalletIdOrderByCreatedAtAscIdAsc(wallet.getId())) {
            long amount = transaction.getAmount();
            if (amount > 0) income = Math.addExact(income, amount);
            if (amount < 0) outcome = Math.addExact(outcome, Math.negateExact(amount));
            balance = Math.addExact(balance, amount);
            transaction.updateBalanceAfter(balance);
        }
        wallet.reconcileRecordedBalance(Math.subtractExact(income, outcome));
        entityManager.flush();
    }

    private Wallet getWalletOrThrow(Long userId) {
        requireUserId(userId);
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    // 여러 입출금이 같은 지갑에 동시에 반영되는 것을 막기 위해 비관적 락으로 조회한다.
    private Wallet getWalletForUpdate(Long userId) {
        requireUserId(userId);
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("인증된 사용자 ID가 필요합니다.");
        }
    }
}
