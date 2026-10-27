package com.example.samdi.domain.score.service;

import com.example.samdi.domain.score.entity.Score;
import com.example.samdi.domain.score.repository.ScoreRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ScoreService {

    private final ScoreRepository scoreRepository;
    public ScoreService(ScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    @Transactional
    public void updateHighestScore(Long userId, int score) {
        if (score < 0) {
            throw new IllegalArgumentException("점수는 0점 이사이어야 합니다.");
        }

        Score userScore = scoreRepository.findById(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException("사용자를 찾을 수 없습니다.")
                );
        userScore.updateHighestScore(score);
    }
}
