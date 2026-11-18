package com.example.samdi.domain.achievement.service;

import com.example.samdi.domain.achievement.dto.UserAchievementResponse;
import com.example.samdi.domain.achievement.entity.UserAchievement;
import com.example.samdi.domain.achievement.repository.AchievementRepository;
import com.example.samdi.domain.achievement.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.samdi.domain.achievement.entity.Achievement;
import java.util.ArrayList;

import java.util.List;

@Service
@RequiredArgsConstructor
// 업적 정보와 진행 기록을 합쳐서 보여줄 클래스
public class UserAchievementService {
    private final AchievementRepository achievementRepository; // 업적 이름, 목표 등 공통적인 것들
    private final UserAchievementRepository userAchievementRepository; // 개인 수치

    public List<UserAchievementResponse> getMyAchievements(Long userId) {
        // 업적 정의 조회
        List<Achievement> achievements = achievementRepository.findAllByHiddenFalse();

        // 사용자의 업적 진행 기록 조회
        List<UserAchievement> userAchievements = userAchievementRepository.findAllByIdUserId(userId);

        // 업적 정보와 개인 진행도를 합친 응답을 담을 목록
        List<UserAchievementResponse> responses = new ArrayList<>();

        // 업적 정의를 꺼내는 반복문
        for (Achievement achievement : achievements) {
            // 사용자 기록이 없으면 진행도를 0으로 표시
            int progress = 0;

            // 현재 업적을 얼마나 진행했는지 찾는 반복문
            for (UserAchievement userAchievement : userAchievements) {
                if (achievement.getId().equals(
                        userAchievement.getId().getAchievementId())) {
                    progress = userAchievement.getProgress();
                    break;
                }
            }

            // 업적 정보와 현재 진행도를 응답 목록에 추가
            responses.add(new UserAchievementResponse(
                    achievement.getId(),
                    achievement.getName(),
                    achievement.getDescription(),
                    achievement.getTargetValue(),
                    progress
            ));
        }

        // 업적 목록 반환
        return responses;
    }

}
