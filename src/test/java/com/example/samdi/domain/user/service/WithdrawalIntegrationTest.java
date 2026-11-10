package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.email.EmailSender;
import com.example.samdi.domain.auth.repository.RefreshTokenRepository;
import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.entity.User;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import com.example.samdi.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WithdrawalIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private UserService service;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private EmailSender sender;
    @LocalServerPort private int port;

    @BeforeEach
    void prepareTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS score (user_id BIGINT UNSIGNED PRIMARY KEY, nickname VARCHAR(50) NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS achievements (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, description TEXT NOT NULL, category VARCHAR(50) NOT NULL, condition_type VARCHAR(50) NOT NULL, target_value INT NOT NULL, hidden BOOLEAN NOT NULL)");
        jdbc.update("INSERT INTO achievements (id, name, description, category, condition_type, target_value, hidden) SELECT 1, 'test', 'test', 'test', 'test', 1, false WHERE NOT EXISTS (SELECT 1 FROM achievements WHERE id = 1)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_achievements (user_id BIGINT UNSIGNED NOT NULL, achievement_id BIGINT NOT NULL, PRIMARY KEY (user_id, achievement_id), FOREIGN KEY (user_id) REFERENCES users(id), FOREIGN KEY (achievement_id) REFERENCES achievements(id))");
    }

    private User user() {
        String value = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().userName(value).nickname(value).email(value + "@example.invalid")
                .password(encoder.encode("Correct123!")).build());
    }

    private HttpResponse<String> withdraw(String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/me"))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.method("DELETE", HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void withdrawalDeletesOnlyOwnAccountAndRejectsOldTokens() throws Exception {
        User user = user();
        User other = user();
        var token = service.login(new LoginRequest(user.getUserName(), "Correct123!"));
        var otherToken = service.login(new LoginRequest(other.getUserName(), "Correct123!"));
        for (User current : new User[]{user, other}) {
            jdbc.update("INSERT INTO score (user_id, nickname) VALUES (?, ?)", current.getId(), current.getNickname());
            jdbc.update("INSERT INTO user_achievements (user_id, achievement_id) VALUES (?, 1)", current.getId());
        }

        assertEquals(401, withdraw(null, "{\"password\":\"Correct123!\"}").statusCode());
        assertEquals(400, withdraw(token.accessToken(), "{}").statusCode());
        assertEquals(401, withdraw(token.accessToken(), "{\"password\":\"Wrong123!\"}").statusCode());
        assertTrue(users.existsById(user.getId()));
        assertTrue(refreshTokens.findByUserId(user.getId()).isPresent());

        var response = withdraw(token.accessToken(), "{\"password\":\"Correct123!\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertFalse(users.existsById(user.getId()));
        assertTrue(refreshTokens.findByUserId(user.getId()).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM score WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_achievements WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(401, withdraw(token.accessToken(), "{\"password\":\"Correct123!\"}").statusCode());
        assertThrows(InvalidCredentialsException.class, () -> service.refresh(token.refreshToken()));
        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest(user.getUserName(), "Correct123!")));
        assertTrue(users.existsById(other.getId()));
        assertTrue(refreshTokens.findByUserId(other.getId()).isPresent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM score WHERE user_id = ?", Integer.class, other.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_achievements WHERE user_id = ?", Integer.class, other.getId()));
        assertNotNull(service.refresh(otherToken.refreshToken()));
    }

    @Test
    void withdrawalFailureRollsBackAllChanges() {
        User user = user();
        var token = service.login(new LoginRequest(user.getUserName(), "Correct123!"));
        jdbc.update("INSERT INTO score (user_id, nickname) VALUES (?, ?)", user.getId(), user.getNickname());
        jdbc.update("INSERT INTO user_achievements (user_id, achievement_id) VALUES (?, 1)", user.getId());
        jdbc.execute("CREATE TABLE withdrawal_test_dependency (user_id BIGINT UNSIGNED NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id))");
        try {
            jdbc.update("INSERT INTO withdrawal_test_dependency (user_id) VALUES (?)", user.getId());
            assertThrows(RuntimeException.class, () -> service.withdraw(user.getId(), "Correct123!"));
            assertTrue(users.existsById(user.getId()));
            assertTrue(refreshTokens.findByUserId(user.getId()).isPresent());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM score WHERE user_id = ?", Integer.class, user.getId()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_achievements WHERE user_id = ?", Integer.class, user.getId()));
            assertNotNull(service.refresh(token.refreshToken()));
        } finally {
            jdbc.execute("DROP TABLE withdrawal_test_dependency");
        }
    }
}
