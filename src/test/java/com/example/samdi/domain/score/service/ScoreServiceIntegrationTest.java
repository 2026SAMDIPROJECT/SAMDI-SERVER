package com.example.samdi.domain.score.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
    void savesHighestScoreToDatabase() {
        // 기존 사용자와 겹치지 않는 테스트용 로그인 아이디
        String loginId = "score-test-" + UUID.randomUUID();

        // 테스트에서 사용할 임시 사용자 생성
        jdbcTemplate.update("""
                INSERT INTO users
                    (user_name, email, password,
                     highest_score, created_at, updated_at)
                VALUES (?, ?, ?, 0,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                loginId,
                loginId + "@example.invalid",
                "test-only-placeholder"
        );

        // 생성한 사용자의 숫자 기본키 조회
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE user_name = ?",
                Long.class,
                loginId
        );

        // 최초 최고 점수는 0
        assertEquals(0, readHighestScoreFromDatabase(userId));

        // 100점을 제출하면 100점으로 갱신
        scoreService.updateHighestScore(userId, 100);
        assertEquals(100, readHighestScoreFromDatabase(userId));

        // 80점을 제출해도 최고 점수는 100점 유지
        scoreService.updateHighestScore(userId, 80);
        assertEquals(100, readHighestScoreFromDatabase(userId));

        // 150점을 제출하면 150점으로 갱신
        scoreService.updateHighestScore(userId, 150);
        assertEquals(150, readHighestScoreFromDatabase(userId));
    }

    private int readHighestScoreFromDatabase(Long userId) {
        // JPA 객체의 변경 내용을 실제 DB에 반영
        entityManager.flush();

        // SQL로 DB에 반영된 값을 직접 조회
        return jdbcTemplate.queryForObject(
                "SELECT highest_score FROM users WHERE id = ?",
                Integer.class,
                userId
        );
    }
}