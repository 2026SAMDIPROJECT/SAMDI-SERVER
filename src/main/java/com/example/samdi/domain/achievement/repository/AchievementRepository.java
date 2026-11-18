package com.example.samdi.domain.achievement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.samdi.domain.achievement.entity.Achievement;
import java.util.List;

// 업적 정의를 조회(공통적인 것)
public interface AchievementRepository extends JpaRepository<Achievement,Long> {
    // 숨김 업적을 제외한 업적 정의를 모두 조회하는 메소드
    List<Achievement> findAllByHiddenFalse();
}
