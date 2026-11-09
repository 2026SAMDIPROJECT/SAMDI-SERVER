package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.email.EmailSender;
import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import com.example.samdi.domain.user.exception.LoginRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginAttemptIntegrationTest {
    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailSender sender;
    @LocalServerPort private int port;

    private String createUser() {
        String loginId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (login_id, email, password, created_at, nickname) VALUES (?, ?, ?, ?, ?)",
                loginId, loginId + "@example.invalid", passwordEncoder.encode("Test1234!"), LocalDateTime.now(), loginId);
        return loginId;
    }

    @Test
    void failuresBlockThenExpireAndSuccessfulLoginClearsFailures() {
        String loginId = createUser();
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        }
        assertEquals(5, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
        assertThrows(LoginRateLimitException.class, () -> users.login(new LoginRequest(loginId, "Test1234!")));
        jdbc.update("UPDATE login_attempts SET blocked_until = ? WHERE login_id = ?", LocalDateTime.now().minusSeconds(1), loginId);
        assertNotNull(users.login(new LoginRequest(loginId, "Test1234!")).accessToken());
        assertEquals(0, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
        assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        assertEquals(1, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
        assertNotNull(users.login(new LoginRequest(loginId, "Test1234!")).refreshToken());
        assertEquals(0, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
    }

    @Test
    void unknownAccountUsesSameLimits() {
        String loginId = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        }
        assertThrows(LoginRateLimitException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
    }

    @Test
    void expiredWindowStartsNewCount() {
        String loginId = UUID.randomUUID().toString();
        assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        jdbc.update("UPDATE login_attempts SET window_started_at = ? WHERE login_id = ?", LocalDateTime.now().minusMinutes(6), loginId);
        assertThrows(InvalidCredentialsException.class, () -> users.login(new LoginRequest(loginId, "wrong")));
        assertEquals(1, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
    }

    @Test
    void concurrentFailuresCannotBypassLimit() throws Exception {
        String loginId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 10; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try { users.login(new LoginRequest(loginId, "wrong")); return "success"; }
                    catch (InvalidCredentialsException e) { return "invalid"; }
                    catch (LoginRateLimitException e) { return "limited"; }
                }));
            }
            start.countDown();
            int invalid = 0;
            int limited = 0;
            for (var result : results) {
                String value = result.get(30, TimeUnit.SECONDS);
                if (value.equals("invalid")) invalid++;
                if (value.equals("limited")) limited++;
            }
            assertEquals(5, invalid);
            assertEquals(5, limited);
        }
        assertEquals(5, jdbc.queryForObject("SELECT failures FROM login_attempts WHERE login_id = ?", Integer.class, loginId));
    }

    @Test
    void rateLimitReturns429() throws Exception {
        String loginId = UUID.randomUUID().toString();
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + loginId + "\",\"password\":\"wrong\"}"))
                .build();
        for (int i = 0; i < 5; i++) {
            assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, response.statusCode());
        assertTrue(response.body().contains("LOGIN_RATE_LIMITED"));
    }

    @Test
    void existingAndUnknownAccountsReturnSameFailuresAndLimit() throws Exception {
        String existing = createUser();
        String unknown = UUID.randomUUID().toString();
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/auth/login");
        for (int i = 0; i < 6; i++) {
            var first = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + existing + "\",\"password\":\"wrong\"}")).build();
            var second = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + unknown + "\",\"password\":\"wrong\"}")).build();
            var existingResponse = client.send(first, HttpResponse.BodyHandlers.ofString());
            var unknownResponse = client.send(second, HttpResponse.BodyHandlers.ofString());
            assertEquals(i < 5 ? 401 : 429, existingResponse.statusCode());
            assertEquals(existingResponse.statusCode(), unknownResponse.statusCode());
            assertEquals(existingResponse.body(), unknownResponse.body());
        }
    }
}
