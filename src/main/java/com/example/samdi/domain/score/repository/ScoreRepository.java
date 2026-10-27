package com.example.samdi.domain.score.repository;

import com.example.samdi.domain.score.entity.Score;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Long> {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Score> findById(Long id);
}
