package com.example.samdi.domain.achievement.controller;

import com.example.samdi.domain.achievement.dto.UserAchievementResponse;
import com.example.samdi.domain.achievement.service.UserAchievementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class UserAchievementController {

    private final UserAchievementService userAchievementService;

    // JWT 인증을 통과한 사용자 ID로 본인의 업적 목록과 진행도를 조회하는 메소드
    @GetMapping("/me")
    public List<UserAchievementResponse> getMyAchievements(@AuthenticationPrincipal String userId) {
        // 인증된 사용자 정보가 없으면 조회를 거부
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userAchievementService.getMyAchievements(Long.parseLong(userId));
    }
}
