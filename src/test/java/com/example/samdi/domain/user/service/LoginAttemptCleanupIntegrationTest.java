package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.email.EmailSender;
import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import com.example.samdi.domain.user.exception.LoginRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoginAttemptCleanupIntegrationTest {
    @Autowired private LoginAttemptCleanup cleanup;
    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor scheduledTasks;
    @MockitoBean private EmailSender sender;

    private String insert(int failures, LocalDateTime window, LocalDateTime blocked) {
        String loginId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO login_attempts (login_id, failures, window_started_at, blocked_until) VALUES (?, ?, ?, ?)",
                loginId, failures, window, blocked);
        return loginId;
    }

    private int count(String loginId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE login_id = ?", Integer.class, loginId);
    }

    @Test
    void cleanupTaskIsScheduled() {
        assertFalse(scheduledTasks.getScheduledTasks().isEmpty());
    }

    @Test
    void removesOnlyExpiredOrResetRecordsAndCanRunAgain() {
        LocalDateTime now = LocalDateTime.now();
        String expired = insert(5, now.minusMinutes(12), now.minusMinutes(1));
        String activeBlock = insert(5, now.minusMinutes(12), now.plusMinutes(1));
        String activeWindow = insert(4, now.minusMinutes(1), null);
        String reset = insert(0, null, null);
        cleanup.cleanup();
        assertEquals(0, count(expired));
        assertEquals(1, count(activeBlock));
        assertEquals(1, count(activeWindow));
        assertEquals(0, count(reset));
        cleanup.cleanup();
        assertEquals(1, count(activeBlock));
        assertEquals(1, count(activeWindow));
    }

    @Test
    void removesAtMostOneBatchPerRun() {
        cleanup.cleanup();
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < 101; i++) {
            ids.add(insert(0, null, null));
        }
        cleanup.cleanup();
        assertEquals(1, ids.stream().mapToInt(this::count).sum());
        cleanup.cleanup();
        assertEquals(0, ids.stream().mapToInt(this::count).sum());
    }

    @Test
    void skipsLockedLoginRecordWithoutLosingFailures() throws Exception {
        String loginId = insert(4, LocalDateTime.now().minusMinutes(10), null);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ? FOR UPDATE", Integer.class, loginId);
                jdbc.update("UPDATE login_attempts SET window_started_at = ? WHERE login_id = ?", LocalDateTime.now(), loginId);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS));
                var cleaned = executor.submit(() -> cleanup.cleanup());
                cleaned.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            update.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, count(loginId));
        assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        assertThrows(LoginRateLimitException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
    }

    @Test
    void concurrentCleanupAndLoginsKeepTheLimit() throws Exception {
        String loginId = insert(4, LocalDateTime.now().minusMinutes(10), null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(7)) {
            var clean = executor.submit(() -> { start.await(); cleanup.cleanup(); return null; });
            var results = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try { users.login(new LoginRequest(loginId, "wrong")); return "success"; }
                    catch (InvalidCredentialsException e) { return "invalid"; }
                    catch (LoginRateLimitException e) { return "limited"; }
                }));
            }
            start.countDown();
            clean.get(30, TimeUnit.SECONDS);
            int invalid = 0;
            int limited = 0;
            for (var result : results) {
                String value = result.get(30, TimeUnit.SECONDS);
                if (value.equals("invalid")) invalid++;
                if (value.equals("limited")) limited++;
            }
            assertEquals(5, invalid);
            assertEquals(1, limited);
        }
    }
}
