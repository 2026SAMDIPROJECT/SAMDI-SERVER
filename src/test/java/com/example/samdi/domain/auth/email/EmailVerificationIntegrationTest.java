package com.example.samdi.domain.auth.email;

import com.example.samdi.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EmailVerificationIntegrationTest {
    @Autowired private EmailVerificationService service;
    @Autowired private com.example.samdi.domain.user.service.UserService userService;
    @Autowired private com.example.samdi.domain.user.repository.UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private EmailSender sender;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM email_verifications");
    }

    @Test
    void sendsAndStoresHashWithExpiry() {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(" USER@EXAMPLE.COM ");
        verify(sender).send(eq("user@example.com"), code.capture());
        assertTrue(code.getValue().matches("[0-9]{6}"));
        var row = jdbc.queryForMap("SELECT code_hash, code_expires_at, last_sent_at FROM email_verifications WHERE email = ?", "user@example.com");
        assertTrue(row.get("code_hash").toString().matches("[0-9a-f]{64}"));
        assertNotEquals(code.getValue(), row.get("code_hash"));
        assertEquals(((java.sql.Timestamp) row.get("last_sent_at")).toLocalDateTime().plusMinutes(5),
                ((java.sql.Timestamp) row.get("code_expires_at")).toLocalDateTime());
    }

    @Test
    void resendIsLimited() {
        service.send("user@example.com");
        assertEquals(ErrorCode.EMAIL_RESEND_TOO_SOON,
                assertThrows(EmailSendException.class, () -> service.send("USER@example.com")).getErrorCode());
        verify(sender, times(1)).send(anyString(), anyString());
    }

    @Test
    void resendAfterCooldownUpdatesExpiry() {
        service.send("user@example.com");
        jdbc.update("UPDATE email_verifications SET last_sent_at = ? WHERE email = ?", LocalDateTime.now().minusMinutes(2), "user@example.com");
        service.send("user@example.com");
        verify(sender, times(2)).send(anyString(), anyString());
    }

    @Test
    void verificationIssuesProofAndRejectsReuse() {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send("user@example.com");
        verify(sender).send(eq("user@example.com"), code.capture());
        String proof = service.verify("USER@example.com", code.getValue());
        assertTrue(proof.matches("[A-Za-z0-9_-]{43}"));
        String stored = jdbc.queryForObject("SELECT proof_hash FROM email_verifications WHERE email = ?", String.class, "user@example.com");
        assertNotEquals(proof, stored);
        assertEquals(ErrorCode.EMAIL_CODE_EXPIRED,
                assertThrows(EmailSendException.class, () -> service.verify("user@example.com", code.getValue())).getErrorCode());
    }

    @Test
    void wrongCodesAreLimited() {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send("user@example.com");
        verify(sender).send(eq("user@example.com"), code.capture());
        String wrong = code.getValue().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertEquals(ErrorCode.EMAIL_CODE_MISMATCH,
                    assertThrows(EmailSendException.class, () -> service.verify("user@example.com", wrong)).getErrorCode());
        }
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED,
                assertThrows(EmailSendException.class, () -> service.verify("user@example.com", wrong)).getErrorCode());
        assertEquals("", jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE email = ?", String.class, "user@example.com"));
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED,
                assertThrows(EmailSendException.class, () -> service.verify("user@example.com", code.getValue())).getErrorCode());
        assertEquals(ErrorCode.EMAIL_RESEND_TOO_SOON,
                assertThrows(EmailSendException.class, () -> service.send("user@example.com")).getErrorCode());
        jdbc.update("UPDATE email_verifications SET last_sent_at = ? WHERE email = ?", LocalDateTime.now().minusMinutes(2), "user@example.com");
        service.send("user@example.com");
        assertEquals(0, jdbc.queryForObject("SELECT attempts FROM email_verifications WHERE email = ?", Integer.class, "user@example.com"));
        var sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(eq("user@example.com"), sent.capture());
        assertNotNull(service.verify("user@example.com", sent.getAllValues().get(1)));
    }

    @Test
    void proofIsConsumedOnceAndRollbackPreservesIt() {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send("user@example.com");
        verify(sender).send(eq("user@example.com"), code.capture());
        String proof = service.verify("user@example.com", code.getValue());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(EmailSendException.class, () -> transaction.executeWithoutResult(status ->
                service.consumeProof("other@example.com", proof)));
        transaction.executeWithoutResult(status -> {
            service.consumeProof("user@example.com", proof);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> service.consumeProof("user@example.com", proof));
        assertThrows(EmailSendException.class, () -> transaction.executeWithoutResult(status ->
                service.consumeProof("user@example.com", proof)));
    }

    private String verifiedProof(String email) {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send(email);
        verify(sender).send(eq(email), code.capture());
        return service.verify(email, code.getValue());
    }

    private com.example.samdi.domain.user.dto.request.SignupRequest signup(String email, String loginId, String proof) {
        return new com.example.samdi.domain.user.dto.request.SignupRequest(email, "Test1234!", "Test1234!", loginId, proof);
    }

    @Test
    void signupRequiresValidProofAndPreservesResponse() {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String loginId = java.util.UUID.randomUUID().toString();
        assertThrows(EmailSendException.class, () -> userService.signup(signup(email, loginId, null)));
        assertFalse(userRepository.existsByEmail(email));
        String proof = verifiedProof(email);
        assertThrows(EmailSendException.class, () -> userService.signup(signup(email, loginId, "A".repeat(43))));
        assertThrows(EmailSendException.class, () -> userService.signup(signup("other-" + email, loginId, proof)));
        var response = userService.signup(signup(email, loginId, proof));
        assertEquals(email, response.email());
        assertEquals(loginId, response.userName());
        assertTrue(userRepository.existsByEmail(email));
        var login = userService.login(new com.example.samdi.domain.user.dto.request.LoginRequest(loginId, "Test1234!"));
        assertNotNull(login.accessToken());
        assertNotNull(login.refreshToken());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(EmailSendException.class, () -> transaction.executeWithoutResult(status -> service.consumeProof(email, proof)));
    }

    @Test
    void expiredProofCannotSignup() {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String proof = verifiedProof(email);
        jdbc.update("UPDATE email_verifications SET proof_expires_at = ? WHERE email = ?", LocalDateTime.now().minusSeconds(1), email);
        assertThrows(EmailSendException.class, () -> userService.signup(signup(email, java.util.UUID.randomUUID().toString(), proof)));
        assertFalse(userRepository.existsByEmail(email));
    }

    @Test
    void failedSignupRollsBackProofConsumption() {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String loginId = java.util.UUID.randomUUID().toString();
        String proof = verifiedProof(email);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            userService.signup(signup(email, loginId, proof));
            status.setRollbackOnly();
        });
        assertFalse(userRepository.existsByEmail(email));
        assertEquals(email, userService.signup(signup(email, loginId, proof)).email());
    }

    @Test
    void concurrentSignupsCannotConsumeSameProofTwice() throws Exception {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String proof = verifiedProof(email);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); try { userService.signup(signup(email, java.util.UUID.randomUUID().toString(), proof)); return true; } catch (RuntimeException e) { return false; } });
            var second = executor.submit(() -> { start.await(); try { userService.signup(signup(email, java.util.UUID.randomUUID().toString(), proof)); return true; } catch (RuntimeException e) { return false; } });
            start.countDown();
            assertNotEquals(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertTrue(userRepository.existsByEmail(email));
    }

    @Autowired private com.example.samdi.global.security.jwt.JwtProvider jwtProvider;

    @Test
    void earlierSessionCannotLogOutLatestSessionAndRotationKeepsSession() {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String loginId = java.util.UUID.randomUUID().toString();
        String proof = verifiedProof(email);
        userService.signup(signup(email, loginId, proof));
        var first = userService.login(new com.example.samdi.domain.user.dto.request.LoginRequest(loginId, "Test1234!"));
        var second = userService.login(new com.example.samdi.domain.user.dto.request.LoginRequest(loginId, "Test1234!"));
        Long userId = Long.valueOf(jwtProvider.getUserId(second.accessToken()));
        assertNotEquals(jwtProvider.getSessionId(first.accessToken()), jwtProvider.getSessionId(second.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> userService.logout(userId, jwtProvider.getSessionId(first.accessToken())));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> userService.refresh(first.refreshToken()));
        var rotated = userService.refresh(second.refreshToken());
        assertNotEquals(second.refreshToken(), rotated.refreshToken());
        assertEquals(jwtProvider.getSessionId(second.accessToken()), jwtProvider.getSessionId(rotated.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> userService.refresh(second.refreshToken()));
        userService.logout(userId, jwtProvider.getSessionId(second.accessToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> userService.refresh(rotated.refreshToken()));
        assertThrows(com.example.samdi.domain.user.exception.InvalidCredentialsException.class,
                () -> userService.logout(userId, jwtProvider.getSessionId(second.accessToken())));
    }

    @Test
    void expiredCodeIsRejected() {
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        service.send("user@example.com");
        verify(sender).send(eq("user@example.com"), code.capture());
        jdbc.update("UPDATE email_verifications SET code_expires_at = ? WHERE email = ?", LocalDateTime.now().minusSeconds(1), "user@example.com");
        assertEquals(ErrorCode.EMAIL_CODE_EXPIRED,
                assertThrows(EmailSendException.class, () -> service.verify("user@example.com", code.getValue())).getErrorCode());
    }

    @Test
    void mailFailureRollsBackStorage() {
        doThrow(new IllegalStateException()).when(sender).send(anyString(), anyString());
        assertEquals(ErrorCode.EMAIL_SEND_FAILED,
                assertThrows(EmailSendException.class, () -> service.send("user@example.com")).getErrorCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications", Integer.class));
    }
}
