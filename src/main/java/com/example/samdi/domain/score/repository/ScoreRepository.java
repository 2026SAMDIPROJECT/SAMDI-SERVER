package com.example.samdi.domain.score.repository;

import com.example.samdi.domain.score.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    @Query(value = "SELECT nickname FROM users WHERE id = :userId FOR UPDATE",
            nativeQuery = true)
    Optional<String> findNicknameForUpdate(@Param("userId") Long userId);
}
