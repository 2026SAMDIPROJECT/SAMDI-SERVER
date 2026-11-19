package com.example.samdi.domain.score.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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

@Tag(name = "점수", description = "게임별 최고 점수와 한 번의 플레이에서 얻은 최고 총점을 저장합니다.")
@RestController
@RequestMapping("/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @Operation(summary = "플레이 최고 점수 저장",
            description = "**인증** Bearer 액세스 토큰\n\n**필수 입력** `game1Score`, `game2Score`. 같은 플레이의 게임별 점수를 0 이상의 정수로 보냅니다. 게임별 최고점은 각각 비교하고, 두 점수의 합은 기존 최고 총점과 비교합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SubmitScoreRequest.class),
                            examples = @ExampleObject(value = "{\"game1Score\":80,\"game2Score\":100}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 처리 완료", content = @Content),
            @ApiResponse(responseCode = "400", description = "점수 누락, 음수 또는 잘못된 요청 본문", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 정보가 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "사용자 정보가 없음", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Void> submitScore(
            @Parameter(hidden = true) @AuthenticationPrincipal String authenticatedUserId,
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

        Integer game1Score = request.game1Score();
        Integer game2Score = request.game2Score();
        if (game1Score == null || game2Score == null || game1Score < 0 || game2Score < 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            scoreService.updateHighestScore(userId, game1Score, game2Score);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
