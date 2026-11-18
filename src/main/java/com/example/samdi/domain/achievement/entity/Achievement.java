package com.example.samdi.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "achievements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id; // DB에서 자동으로 생성하는 정수형 업적 ID

    @Column(nullable = false, length = 100)
    private String name; // 업적 이름

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description; // 업적 조건

    @Column(nullable = false, length = 50)
    private String category; // 업적 종류(일반, 경쟁, 히든...)

    @Column(name = "condition_type", nullable = false, length = 50)
    private String conditionType; // 집계할 행동(승리, 플레이 횟수 등)

    @Column(name = "target_value", nullable = false)
    private int targetValue; // 목표 수치

    @Column(name = "hidden", nullable = false)
    @ColumnDefault("false")
    private boolean hidden = false; // 플레이어에게 숨겨진 업적(업적 획득 전까진 알 수 없는 업적)

    // 업적 정보를 받아 정의를 만들고, ID는 DB 저장 시 자동으로 부여받는 생성자
    public Achievement(String name, String description, String category, String conditionType, int targetValue, boolean hidden) {
        if (targetValue <= 0) { // 목표 수치가 0이하면 업적 못만들게 하는 예외처리하는 생성자
            throw new IllegalArgumentException("업적 목표 수치는 1 이상이어야 합니다.");
        }

        this.name = name;
        this.description = description;
        this.category = category;
        this.conditionType = conditionType;
        this.targetValue = targetValue;
        this.hidden = hidden;
    }

}
