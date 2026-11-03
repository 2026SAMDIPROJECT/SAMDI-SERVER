package com.example.samdi.domain.auth.repository;

import com.example.samdi.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    Optional<RefreshToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.userId = :userId")
    Optional<RefreshToken> findByUserIdForUpdate(@Param("userId") Long userId);
}
