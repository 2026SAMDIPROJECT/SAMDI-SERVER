package com.example.samdi.domain.auth.email;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {
    @Modifying
    @Query(value = "INSERT INTO email_verifications (email, code_hash, code_expires_at, last_sent_at, attempts) VALUES (:email, '', :initial, :initial, 0) ON DUPLICATE KEY UPDATE email = VALUES(email)", nativeQuery = true)
    void ensureRow(@Param("email") String email, @Param("initial") LocalDateTime initial);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select verification from EmailVerification verification where verification.email = :email")
    Optional<EmailVerification> findForUpdate(@Param("email") String email);
}
