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
    public void updateHighestScore(Long userId, int game1Score, int game2Score) {
        if (game1Score < 0 || game2Score < 0) {
            throw new IllegalArgumentException("점수는 0점 이상이어야 합니다.");
        }

        // 첫 제출에는 점수 행이 없으므로 사용자 행을 잠근다.
        String nickname = scoreRepository.findNicknameForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        Score score = scoreRepository.findById(userId)
                .orElseGet(() -> new Score(userId, nickname));

        score.updateHighestScore(nickname, game1Score, game2Score);
        scoreRepository.save(score);
    }
}
