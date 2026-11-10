package com.example.samdi.domain.achievement.dto;

// 화면에 보내줄 업적 정보
public record UserAchievementResponse(Long achievementId, String name, String description, int targetValue, int progress) {
}
