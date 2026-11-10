package com.example.samdi.domain.user.service;

import com.example.samdi.domain.user.entity.LoginAttempt;
import com.example.samdi.domain.user.exception.LoginRateLimitException;
import com.example.samdi.domain.user.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class LoginAttemptService {
    private final LoginAttemptRepository repository;
    private final int maxFailures;
    private final long windowMinutes;
    private final long blockMinutes;

    public LoginAttemptService(LoginAttemptRepository repository,
                               @Value("${samdi.security.login.max-failures}") int maxFailures,
                               @Value("${samdi.security.login.window-minutes}") long windowMinutes,
                               @Value("${samdi.security.login.block-minutes}") long blockMinutes) {
        if (maxFailures <= 0 || windowMinutes <= 0 || blockMinutes <= 0) {
            throw new IllegalArgumentException("로그인 제한 설정은 0보다 커야 합니다.");
        }
        this.repository = repository;
        this.maxFailures = maxFailures;
        this.windowMinutes = windowMinutes;
        this.blockMinutes = blockMinutes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LoginAttempt begin(String loginId) {
        repository.ensureRow(loginId);
        LoginAttempt attempt = repository.findForUpdate(loginId).orElseThrow();
        if (attempt.isBlocked(LocalDateTime.now())) {
            throw new LoginRateLimitException();
        }
        return attempt;
    }

    public void fail(LoginAttempt attempt) {
        attempt.fail(LocalDateTime.now(), maxFailures, windowMinutes, blockMinutes);
    }

    public void success(LoginAttempt attempt) {
        attempt.reset();
    }
}
