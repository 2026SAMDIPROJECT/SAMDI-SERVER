CREATE TABLE login_attempts (
    login_id VARCHAR(50) NOT NULL,
    failures INT NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) DEFAULT NULL,
    blocked_until DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (login_id),
    KEY idx_login_attempts_window_started_at (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
