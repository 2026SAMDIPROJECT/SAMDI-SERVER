package com.example.samdi.domain.user.service;

import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.SignupRequest;
import com.example.samdi.domain.user.entity.User;
import com.example.samdi.domain.user.exception.DuplicateEmailException;
import com.example.samdi.domain.user.exception.DuplicateLoginIdException;
import com.example.samdi.domain.user.repository.UserRepository;
import com.example.samdi.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalAnswers.delegatesTo;

@SpringBootTest(properties = {
        "samdi.test.mariadb=true",
        "spring.datasource.url=${SAMDI_TEST_MARIADB_URL}",
        "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "SAMDI_TEST_MARIADB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/game_db")
class MariaDbUserIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void signupAndEmailLoginUseExistingSchema() {
        String loginId = UUID.randomUUID().toString();
        String email = loginId + "@example.com";
        var response = userService.signup(new SignupRequest(email, "Test1234!", "Test1234!", loginId));
        User user = userRepository.findById(response.userId()).orElseThrow();

        assertEquals(loginId, user.getLoginId());
        assertTrue(passwordEncoder.matches("Test1234!", user.getPasswordHash()));
        assertNotEquals("Test1234!", user.getPasswordHash());
        assertNotNull(user.getCreatedAt());
        assertEquals(response.userId().toString(), jwtProvider.getUserId(
                userService.login(new LoginRequest("  " + email.toUpperCase() + "  ", "Test1234!")).accessToken()));
        assertNull(jdbcTemplate.queryForObject("SELECT email_verified_at FROM users WHERE user_id = ?",
                Object.class, user.getId()));
    }

    @Test
    void databaseUniqueConstraintsAreConvertedAfterPrecheckRace() {
        String loginId = UUID.randomUUID().toString();
        String email = loginId + "@example.com";
        userService.signup(new SignupRequest(email, "Test1234!", "Test1234!", loginId));
        UserRepository repository = mock(UserRepository.class, delegatesTo(userRepository));
        doReturn(false).when(repository).existsByEmail(anyString());
        doReturn(false).when(repository).existsByLoginId(anyString());
        UserService service = new UserService(repository, passwordEncoder, jwtProvider);

        assertThrows(DuplicateEmailException.class, () -> service.signup(
                new SignupRequest(email, "Test1234!", "Test1234!", UUID.randomUUID().toString())));
        assertThrows(DuplicateLoginIdException.class, () -> service.signup(
                new SignupRequest(UUID.randomUUID() + "@example.com", "Test1234!", "Test1234!", loginId)));
    }

    @Test
    void uniqueChecksFollowDatabaseCaseInsensitiveCollation() {
        String loginId = "player-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.com";
        userService.signup(new SignupRequest(email, "Test1234!", "Test1234!", loginId));

        assertTrue(userRepository.existsByLoginId(loginId.toUpperCase()));
        assertThrows(DuplicateLoginIdException.class, () -> userService.signup(new SignupRequest(
                UUID.randomUUID() + "@example.com", "Test1234!", "Test1234!", loginId.toUpperCase())));
    }

    @Test
    void databaseDefaultCreatedAtAppliesWhenColumnIsOmitted() {
        String loginId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO users (login_id, email, password_hash) VALUES (?, ?, ?)",
                loginId, loginId + "@example.com", passwordEncoder.encode("Test1234!"));

        assertNotNull(userRepository.findByEmail(loginId + "@example.com").orElseThrow().getCreatedAt());
    }

    @Test
    void unsignedBigintExceedsJavaLongRange() throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE id_range (id BIGINT UNSIGNED)");
            try {
                statement.executeUpdate("INSERT INTO id_range VALUES (9223372036854775807), (18446744073709551615)");
                try (var result = statement.executeQuery("SELECT id FROM id_range ORDER BY id")) {
                    assertTrue(result.next());
                    assertEquals(Long.MAX_VALUE, result.getLong(1));
                    assertTrue(result.next());
                    assertEquals(new BigInteger("18446744073709551615"), result.getBigDecimal(1).toBigIntegerExact());
                    assertThrows(SQLException.class, () -> result.getLong(1));
                }
            } finally {
                statement.execute("DROP TEMPORARY TABLE id_range");
            }
        }
    }
}
