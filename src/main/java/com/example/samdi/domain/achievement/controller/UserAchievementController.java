package com.example.samdi.domain.achievement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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

@Tag(name = "업적", description = "로그인한 사용자의 공개 업적과 진행도를 조회합니다.")
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class UserAchievementController {

    private final UserAchievementService userAchievementService;

    // JWT 인증을 통과한 사용자 ID로 본인의 업적 목록과 진행도를 조회하는 메소드
    @Operation(summary = "내 업적 진행도 조회",
            description = "**인증** Bearer 액세스 토큰\n\n공개 업적의 이름, 설명, 달성 목표와 내 진행도를 배열로 반환합니다. 진행 기록이 없는 업적의 `progress`는 0입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 업적 목록 반환"),
            @ApiResponse(responseCode = "401", description = "인증 정보가 유효하지 않음")
    })
    @GetMapping("/me")
    public List<UserAchievementResponse> getMyAchievements(@Parameter(hidden = true) @AuthenticationPrincipal String userId) {
        // 인증된 사용자 정보가 없으면 조회를 거부
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userAchievementService.getMyAchievements(Long.parseLong(userId));
    }
}
