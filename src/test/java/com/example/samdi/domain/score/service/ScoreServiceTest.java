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
    void keepHighestScoreAcrossSubmissions() {
        Score userScore = new Score();

        when(scoreRepository.findById(1L))
                .thenReturn(Optional.of(userScore));

        assertEquals(0, userScore.getHighestScore());

        scoreService.updateHighestScore(1L, 100);
        assertEquals(100, userScore.getHighestScore());

        scoreService.updateHighestScore(1L, 80);
        assertEquals(100, userScore.getHighestScore());

        scoreService.updateHighestScore(1L, 150);
        assertEquals(150, userScore.getHighestScore());

        scoreService.updateHighestScore(1L, 150);
        scoreService.updateHighestScore(1L, 0);
        assertEquals(150, userScore.getHighestScore());
    }

    @Test
    void rejectsNegativeScore() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scoreService.updateHighestScore(1L, -1)
        );

        verifyNoInteractions(scoreRepository);
    }

    @Test
    void rejectsUnknownUser() {
        when(scoreRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> scoreService.updateHighestScore(999L, 100)
        );
    }
}
