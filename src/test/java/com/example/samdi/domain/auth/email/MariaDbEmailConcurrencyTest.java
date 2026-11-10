package com.example.samdi.domain.auth.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "samdi.test.mariadb=true")
@EnabledIfEnvironmentVariable(named = "SAMDI_TEST_MARIADB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/game_db")
class MariaDbEmailConcurrencyTest extends EmailVerificationIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired private EmailVerificationService service;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private EmailSender sender;

    @org.springframework.beans.factory.annotation.Autowired private com.example.samdi.domain.user.service.UserService users;
    @org.springframework.beans.factory.annotation.Autowired private com.example.samdi.global.security.jwt.JwtProvider jwt;

    private com.example.samdi.domain.user.dto.response.LoginResponse loginNewUser() {
        String id = UUID.randomUUID().toString();
        String email = id + "@example.invalid";
        org.mockito.Mockito.clearInvocations(sender);
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(email);
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(email), code.capture());
        String proof = service.verify(email, code.getValue());
        users.signup(new com.example.samdi.domain.user.dto.request.SignupRequest(email, "Test1234!", "Test1234!", id, proof, "nick-" + java.util.UUID.randomUUID()));
        return users.login(new com.example.samdi.domain.user.dto.request.LoginRequest(id, "Test1234!"));
    }

    @Test
    void rotationRejectsReuseAndLogoutRejectsRefresh() {
        var original = loginNewUser();
        var rotated = users.refresh(original.refreshToken());
        assertNotEquals(original.refreshToken(), rotated.refreshToken());
        assertEquals(jwt.getUserId(original.accessToken()), jwt.getUserId(rotated.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> users.refresh(original.refreshToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> users.refresh(rotated.refreshToken() + "tampered"));
        users.logout(Long.valueOf(jwt.getUserId(rotated.accessToken())), jwt.getSessionId(rotated.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> users.refresh(rotated.refreshToken()));
    }

    @Test
    void simultaneousRefreshAllowsOneRotation() throws Exception {
        var login = loginNewUser();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); try { users.refresh(login.refreshToken()); return true; } catch (com.example.samdi.domain.user.exception.InvalidCredentialsException e) { return false; } });
            var second = executor.submit(() -> { start.await(); try { users.refresh(login.refreshToken()); return true; } catch (com.example.samdi.domain.user.exception.InvalidCredentialsException e) { return false; } });
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void logoutAffectsOnlyTheAuthenticatedUser() {
        var first = loginNewUser();
        var second = loginNewUser();
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.getUserId(second.refreshToken()));
        users.logout(Long.valueOf(jwt.getUserId(first.accessToken())), jwt.getSessionId(first.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> users.refresh(first.refreshToken()));
        assertNotNull(users.refresh(second.refreshToken()).accessToken());
    }

    @org.springframework.beans.factory.annotation.Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void refreshRejectsTokenWhenStoredSessionDoesNotMatch() {
        var login = loginNewUser();
        Long userId = Long.valueOf(jwt.getUserId(login.accessToken()));
        String sessionId = jwt.getSessionId(login.accessToken());
        jdbc.update("UPDATE refresh_tokens SET session_id = ? WHERE user_id = ?", UUID.randomUUID().toString(), userId);
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> users.refresh(login.refreshToken()));
        jdbc.update("UPDATE refresh_tokens SET session_id = ? WHERE user_id = ?", sessionId, userId);
        assertNotNull(users.refresh(login.refreshToken()).accessToken());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        var login = loginNewUser();
        String userId = jwt.getUserId(login.accessToken());
        var key = (javax.crypto.SecretKey) org.springframework.test.util.ReflectionTestUtils.getField(jwt, "secretKey");
        var now = java.time.Instant.now();
        String expired = io.jsonwebtoken.Jwts.builder().subject(userId)
                .issuedAt(java.util.Date.from(now.minusSeconds(120)))
                .expiration(java.util.Date.from(now.minusSeconds(60)))
                .claim("type", "refresh").claim("sessionId", "expired-test")
                .signWith(key).compact();
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class, () -> users.refresh(expired));
        assertNotNull(users.refresh(login.refreshToken()).accessToken());
    }

    @Test
    void concurrentVerificationAllowsOneProof() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(email);
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(email), code.capture());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); try { service.verify(email, code.getValue()); return "OK"; } catch (EmailSendException e) { return e.getErrorCode().name(); } });
            var second = executor.submit(() -> { start.await(); try { service.verify(email, code.getValue()); return "OK"; } catch (EmailSendException e) { return e.getErrorCode().name(); } });
            start.countDown();
            assertEquals(java.util.Set.of("OK", "EMAIL_CODE_EXPIRED"), java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
    }

    @Test
    void concurrentConsumptionAllowsOneTransaction() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(email);
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(email), code.capture());
        String proof = service.verify(email, code.getValue());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> consume = () -> {
                start.await();
                try {
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> service.consumeProof(email, proof));
                    return "OK";
                } catch (EmailSendException e) { return e.getErrorCode().name(); }
            };
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            start.countDown();
            assertEquals(java.util.Set.of("OK", "EMAIL_NOT_VERIFIED"), java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
    }

    @Test
    void resendAndVerificationAreSerialized() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        var initial = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(email);
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(email), initial.capture());
        String oldCode = initial.getValue();
        jdbc.update("UPDATE email_verifications SET last_sent_at = ? WHERE email = ?", java.time.LocalDateTime.now().minusMinutes(2), email);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> newCode = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(call -> {
            newCode.set(call.getArgument(1));
            sending.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException();
            return null;
        }).when(sender).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var resend = executor.submit(() -> service.send(email));
            try {
                assertTrue(sending.await(10, TimeUnit.SECONDS));
                var verify = executor.submit(() -> {
                    try { service.verify(email, oldCode); return "OK"; }
                    catch (EmailSendException e) { return e.getErrorCode().name(); }
                });
                release.countDown();
                resend.get(15, TimeUnit.SECONDS);
                String result = verify.get(15, TimeUnit.SECONDS);
                assertEquals(oldCode.equals(newCode.get()) ? "OK" : "EMAIL_CODE_MISMATCH", result);
                if (!oldCode.equals(newCode.get())) assertNotNull(service.verify(email, newCode.get()));
            } finally { release.countDown(); }
        }
    }

    @Test
    void concurrentSignupsWithSameLoginIdCreateOnlyOneUser() throws Exception {
        String loginId = UUID.randomUUID().toString();
        String firstEmail = UUID.randomUUID() + "@example.invalid";
        String secondEmail = UUID.randomUUID() + "@example.invalid";
        var firstCode = org.mockito.ArgumentCaptor.forClass(String.class);
        var secondCode = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(firstEmail);
        service.send(secondEmail);
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(firstEmail), firstCode.capture());
        org.mockito.Mockito.verify(sender).send(org.mockito.ArgumentMatchers.eq(secondEmail), secondCode.capture());
        String firstProof = service.verify(firstEmail, firstCode.getValue());
        String secondProof = service.verify(secondEmail, secondCode.getValue());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); try { users.signup(new com.example.samdi.domain.user.dto.request.SignupRequest(firstEmail, "Test1234!", "Test1234!", loginId, firstProof, "nick-" + java.util.UUID.randomUUID())); return "OK"; } catch (com.example.samdi.domain.user.exception.DuplicateLoginIdException e) { return "DUPLICATE"; } });
            var second = executor.submit(() -> { start.await(); try { users.signup(new com.example.samdi.domain.user.dto.request.SignupRequest(secondEmail, "Test1234!", "Test1234!", loginId, secondProof, "nick-" + java.util.UUID.randomUUID())); return "OK"; } catch (com.example.samdi.domain.user.exception.DuplicateLoginIdException e) { return "DUPLICATE"; } });
            start.countDown();
            assertEquals(java.util.Set.of("OK", "DUPLICATE"), java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = ?", Integer.class, loginId));
    }

    @Test
    void concurrentFirstSendDoesNotSendTwice() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); try { service.send(email); return "SENT"; } catch (EmailSendException e) { return e.getErrorCode().name(); } catch (RuntimeException e) { return e.getClass().getName(); } });
            var second = executor.submit(() -> { start.await(); try { service.send(email); return "SENT"; } catch (EmailSendException e) { return e.getErrorCode().name(); } catch (RuntimeException e) { return e.getClass().getName(); } });
            start.countDown();
            var results = java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(java.util.Set.of("SENT", "EMAIL_RESEND_TOO_SOON"), results);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email = ?", Integer.class, email));
        org.mockito.Mockito.verify(sender, org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
    }
}
