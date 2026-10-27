package com.example.samdi.domain.score.controller;

import com.example.samdi.domain.score.dto.request.SubmitScoreRequest;
import com.example.samdi.domain.score.service.ScoreService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    public ResponseEntity<Void> submitScore(
            @AuthenticationPrincipal String authenticatedUserId,
            @RequestBody SubmitScoreRequest request
    ) {
        if (authenticatedUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId;

        try {
            userId = Long.valueOf(authenticatedUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (userId <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.score() == null || request.score() < 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            scoreService.updateHighestScore(userId, request.score());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
