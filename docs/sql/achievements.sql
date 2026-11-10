CREATE TABLE IF NOT EXISTS achievements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    target_value INT NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_achievements (
    user_id BIGINT UNSIGNED NOT NULL,
    achievement_id BIGINT NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    unlocked_at DATETIME NULL DEFAULT NULL,
    reward_claimed_at DATETIME NULL DEFAULT NULL,

    PRIMARY KEY (user_id, achievement_id),

    CONSTRAINT fk_user_achievements_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT fk_user_achievements_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievements (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
