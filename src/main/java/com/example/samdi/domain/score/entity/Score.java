package com.example.samdi.domain.score.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class Score {
    @Id
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long userId;

    @Column(name = "highest_score", nullable = false)
    private int highestScore = 0;

    public void updateHighestScore(int score) {
        if (score > this.highestScore) {
            this.highestScore = score;
        }
    }

    public int getHighestScore() {
        return highestScore;
    }
}
