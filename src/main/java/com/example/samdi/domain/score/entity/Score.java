package com.example.samdi.domain.score.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "score")
public class Score {

    @Id
    @Column(name = "user_id", columnDefinition = "bigint unsigned")
    private Long userId;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "game1", nullable = false)
    private int game1HighestScore = 0;

    @Column(name = "game2", nullable = false)
    private int game2HighestScore = 0;

    @Column(name = "highest_score", nullable = false)
    private long highestScore = 0;

    protected Score() {
    }

    public Score(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public void updateHighestScore(String nickname, int game1Score, int game2Score) {
        this.nickname = nickname;
        game1HighestScore = Math.max(game1HighestScore, game1Score);
        game2HighestScore = Math.max(game2HighestScore, game2Score);

        long totalScore = (long) game1Score + game2Score;
        highestScore = Math.max(highestScore, totalScore);
    }

    public int getGame1HighestScore() {
        return game1HighestScore;
    }

    public int getGame2HighestScore() {
        return game2HighestScore;
    }

    public long getHighestScore() {
        return highestScore;
    }
}
