package com.example.samdi.domain.score.dto.request;

// 점수를 생략한 경우(null)와 0점을 구분한다.
public record SubmitScoreRequest(Integer game1Score, Integer game2Score) {
}
