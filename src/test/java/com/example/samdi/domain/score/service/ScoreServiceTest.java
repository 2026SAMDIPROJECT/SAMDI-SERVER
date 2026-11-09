package com.example.samdi.domain.score.service;

import com.example.samdi.domain.score.entity.Score;
import com.example.samdi.domain.score.repository.ScoreRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;

    @InjectMocks
    private ScoreService scoreService;

    @Test
    void keepHighestScoresAcrossSubmissions() {
        Score score = new Score(1L, "플레이어");
        when(scoreRepository.findNicknameForUpdate(1L)).thenReturn(Optional.of("플레이어"));
        when(scoreRepository.findById(1L)).thenReturn(Optional.of(score));

        scoreService.updateHighestScore(1L, 80, 100);
        scoreService.updateHighestScore(1L, 100, 60);

        assertEquals(100, score.getGame1HighestScore());
        assertEquals(100, score.getGame2HighestScore());
        assertEquals(180L, score.getHighestScore());
    }

    @Test
    void rejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class,
                () -> scoreService.updateHighestScore(1L, -1, 100));
        verifyNoInteractions(scoreRepository);
    }

    @Test
    void rejectsUnknownUser() {
        when(scoreRepository.findNicknameForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> scoreService.updateHighestScore(999L, 80, 100));
    }
}
