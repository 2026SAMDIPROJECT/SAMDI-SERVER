package com.example.samdi.domain.score.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

// 점수를 생략한 경우(null)와 0점을 구분한다.
public record SubmitScoreRequest(
        @Schema(description = "첫 번째 게임 점수", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer game1Score,
        @Schema(description = "두 번째 게임 점수", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer game2Score
) {
}
