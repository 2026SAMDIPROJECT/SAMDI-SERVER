package com.example.samdi.domain.achievement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "user_achievements")
@Getter

@NoArgsConstructor(access = AccessLevel.PROTECTED)
 // 사용자별 업적 진행 상황을 저장
 //사용자 한 명의 업적 진행 상황을 DB의 user_achievements 테이블과 연결하는 엔티티
public class UserAchievement {

    @EmbeddedId
    private UserAchievementId id; // (user_id, achievement_id) 복합 기본키

    // 현재 집계된 진행 수치  기록은 0부터 시작
    @Column(name = "progress", nullable = false)
    @ColumnDefault("0")
    private int progress = 0;

    // 최초 달성 시각. 미달성 상태에서는 null
    @Column(name = "unlocked_at", columnDefinition = "DATETIME")
    @ColumnDefault("NULL")
    private LocalDateTime unlockedAt;

    // 보상 수령 시각
    @Column(name = "reward_claimed_at", columnDefinition = "DATETIME")
    @ColumnDefault("NULL")
    private LocalDateTime rewardClaimedAt;

    // 두 ID를 받아 해당 사용자의 업적 기록을 만드는 생성자
    public UserAchievement(Long userId, Long achievementId) {
        this.id = new UserAchievementId(userId, achievementId);
    }


    // 복합키 클래스: 사용자 ID와 업적 ID를 하나의 기본키로 묶는다.
    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class UserAchievementId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "user_id", nullable = false, updatable = false)
        private Long userId;

        @Column(name = "achievement_id", nullable = false, updatable = false)
        private Long achievementId;

        // 두 ID가 비어 있지 않은지 검사하고 복합키에 저장하는 생성자
        public UserAchievementId(Long userId, Long achievementId) {
            this.userId = Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
            if (achievementId == null) {
                throw new IllegalArgumentException("업적 ID는 필수입니다.");
            }
            this.achievementId = achievementId;
        }
    }
}
