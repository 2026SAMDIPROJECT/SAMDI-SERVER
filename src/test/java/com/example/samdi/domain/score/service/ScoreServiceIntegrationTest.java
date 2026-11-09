package com.example.samdi.domain.score.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScoreService.class)
class ScoreServiceIntegrationTest {

    @Autowired
    private ScoreService scoreService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesHighestScoresToDatabase() {
        String loginId = "score-test-" + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO users
                    (nickname, login_id, email, password, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, loginId, loginId, loginId + "@example.invalid", "test-only-placeholder");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = ?", Long.class, loginId
        );

        scoreService.updateHighestScore(userId, 80, 100);
        entityManager.flush();
        entityManager.clear();

        scoreService.updateHighestScore(userId, 100, 60);
        entityManager.flush();

        Map<String, Object> savedScore = jdbcTemplate.queryForMap("""
                SELECT nickname, game1, game2, highest_score
                FROM score WHERE user_id = ?
                """, userId);

        assertEquals(loginId, savedScore.get("nickname"));
        assertEquals(100, savedScore.get("game1"));
        assertEquals(100, savedScore.get("game2"));
        assertEquals(180L, savedScore.get("highest_score"));
    }
}
