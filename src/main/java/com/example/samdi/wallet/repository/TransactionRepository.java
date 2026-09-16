package com.example.samdi.wallet.repository;

import com.example.samdi.wallet.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.samdi.wallet.domain.TransactionType;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTypeAndReferenceId(TransactionType type, Long referenceId);

    List<Transaction> findByWalletIdOrderByCreatedAtAscIdAsc(Long walletId);
}
