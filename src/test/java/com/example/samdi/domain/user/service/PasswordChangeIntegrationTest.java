package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.email.EmailSender;
import com.example.samdi.domain.auth.repository.RefreshTokenRepository;
import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.PasswordChangeRequest;
import com.example.samdi.domain.user.entity.User;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import com.example.samdi.domain.user.repository.UserRepository;
import com.example.samdi.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordChangeIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmailSender sender;
    @LocalServerPort private int port;

    private HttpResponse<String> change(String token, String current, String next, String confirm) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/me/password"))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        String body = "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next
                + "\",\"newPasswordConfirm\":\"" + confirm + "\"}";
        return HttpClient.newHttpClient().send(request.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void passwordChangeValidatesAndRevokesRefreshToken() throws Exception {
        String id = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder().userName(id).email(id + "@example.invalid")
                .password(encoder.encode("OldPass123!")).nickname(id).build());
        var login = userService.login(new LoginRequest(id, "OldPass123!"));
        assertEquals(401, change(null, "OldPass123!", "NewPass123!", "NewPass123!").statusCode());
        assertEquals(401, change(login.accessToken(), "Wrong123!", "NewPass123!", "NewPass123!").statusCode());
        assertEquals(400, change(login.accessToken(), "OldPass123!", "NewPass123!", "Other123!").statusCode());
        assertEquals(400, change(login.accessToken(), "OldPass123!", "short", "short").statusCode());
        assertTrue(encoder.matches("OldPass123!", users.findById(user.getId()).orElseThrow().getPassword()));
        assertTrue(refreshTokens.findByUserId(user.getId()).isPresent());

        var response = change(login.accessToken(), "OldPass123!", "NewPass123!", "NewPass123!");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        String hash = users.findById(user.getId()).orElseThrow().getPassword();
        assertTrue(encoder.matches("NewPass123!", hash));
        assertFalse(encoder.matches("OldPass123!", hash));
        assertNotEquals("NewPass123!", hash);
        assertTrue(refreshTokens.findByUserId(user.getId()).isEmpty());
        assertThrows(InvalidCredentialsException.class, () -> userService.refresh(login.refreshToken()));
        assertThrows(InvalidCredentialsException.class, () -> userService.login(new LoginRequest(id, "OldPass123!")));
        assertNotNull(userService.login(new LoginRequest(id, "NewPass123!")).accessToken());
    }

    @Test
    void deletedUserIsRejected() throws Exception {
        String token = jwtProvider.createAccessToken("9223372036854775807", "session");
        assertEquals(404, change(token, "OldPass123!", "NewPass123!", "NewPass123!").statusCode());
    }

    @Test
    void loginCannotIssueSessionWithOldPasswordDuringChange() throws Exception {
        String id = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder().userName(id).email(id + "@example.invalid")
                .password(encoder.encode("OldPass123!")).nickname(id).build());
        userService.login(new LoginRequest(id, "OldPass123!"));
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loginStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var change = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                userService.changePassword(user.getId(), new PasswordChangeRequest("OldPass123!", "NewPass123!", "NewPass123!"));
                changed.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertTrue(changed.await(10, TimeUnit.SECONDS));
                var login = executor.submit(() -> {
                    loginStarted.countDown();
                    return userService.login(new LoginRequest(id, "OldPass123!"));
                });
                assertTrue(loginStarted.await(10, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class, () -> login.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                change.get(10, TimeUnit.SECONDS);
                var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> login.get(10, TimeUnit.SECONDS));
                assertInstanceOf(InvalidCredentialsException.class, error.getCause());
                assertTrue(refreshTokens.findByUserId(user.getId()).isEmpty());
            } finally {
                release.countDown();
            }
        }
    }
}
