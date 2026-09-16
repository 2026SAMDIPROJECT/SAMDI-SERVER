package com.example.samdi.wallet.repository;

import com.example.samdi.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface WalletRepository extends JpaRepository<Wallet, Long> {


    Optional<Wallet> findByUserId(Long userId);

    // charge/deduct 동시 요청 시 갱신 유실을 막기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}
