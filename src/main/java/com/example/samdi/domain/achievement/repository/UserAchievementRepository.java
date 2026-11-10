package com.example.samdi.domain.achievement.repository;

import com.example.samdi.domain.achievement.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievement.UserAchievementId> {

    // 사용자 ID에 해당하는 업적 기록을 모두 조회하는 메소드(개인적인거)
    //현재 업적 진도를 얼마나 나갔는지 등
    List<UserAchievement> findAllByIdUserId(Long userId);

}
