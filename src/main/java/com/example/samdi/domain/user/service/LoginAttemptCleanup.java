package com.example.samdi.domain.user.service;

import com.example.samdi.domain.user.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
public class LoginAttemptCleanup {
    private final LoginAttemptRepository repository;
    private final long windowMinutes;
    private final int batchSize;

    public LoginAttemptCleanup(LoginAttemptRepository repository,
                               @Value("${samdi.security.login.window-minutes}") long windowMinutes,
                               @Value("${samdi.security.login.cleanup-batch-size}") int batchSize) {
        if (windowMinutes <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("로그인 기록 정리 설정은 0보다 커야 합니다.");
        }
        this.repository = repository;
        this.windowMinutes = windowMinutes;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${samdi.security.login.cleanup-interval}", initialDelayString = "${samdi.security.login.cleanup-interval}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        var loginIds = repository.findExpiredForCleanup(now.minusMinutes(windowMinutes), now, batchSize);
        if (!loginIds.isEmpty()) {
            repository.deleteForCleanup(loginIds);
        }
    }
}
