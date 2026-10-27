package com.example.samdi.domain.score.dto.request;

//integer를 사용해서 null과 0을 구분
public record SubmitScoreRequest(Integer score) {
}
