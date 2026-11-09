package com.example.samdi.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts", indexes = @Index(name = "idx_login_attempts_window_started_at", columnList = "window_started_at"))
public class LoginAttempt {
    @Id
    @Column(name = "login_id", length = 50)
    private String loginId;
    @Column(name = "failures", nullable = false)
    private int failures;
    @Column(name = "window_started_at")
    private LocalDateTime windowStartedAt;
    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    protected LoginAttempt() {}

    public boolean isBlocked(LocalDateTime now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    public void fail(LocalDateTime now, int maxFailures, long windowMinutes, long blockMinutes) {
        if (windowStartedAt == null || !windowStartedAt.plusMinutes(windowMinutes).isAfter(now)
                || blockedUntil != null && !blockedUntil.isAfter(now)) {
            reset();
            windowStartedAt = now;
        }
        failures++;
        if (failures >= maxFailures) {
            blockedUntil = now.plusMinutes(blockMinutes);
        }
    }

    public void reset() {
        failures = 0;
        windowStartedAt = null;
        blockedUntil = null;
    }
}
