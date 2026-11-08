package com.example.samdi.domain.user.repository;

import com.example.samdi.domain.user.entity.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {
    @Modifying
    @Query(value = "INSERT INTO login_attempts (login_id, failures) VALUES (:loginId, 0) ON DUPLICATE KEY UPDATE login_id = VALUES(login_id)", nativeQuery = true)
    void ensureRow(@Param("loginId") String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LoginAttempt attempt where attempt.loginId = :loginId")
    Optional<LoginAttempt> findForUpdate(@Param("loginId") String loginId);

    @Query(value = "SELECT login_id FROM login_attempts WHERE (window_started_at IS NULL OR window_started_at <= :cutoff) AND (blocked_until IS NULL OR blocked_until <= :now) ORDER BY window_started_at, login_id LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<String> findExpiredForCleanup(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    @Modifying
    @Query("delete from LoginAttempt attempt where attempt.loginId in :loginIds")
    int deleteForCleanup(@Param("loginIds") List<String> loginIds);
}
