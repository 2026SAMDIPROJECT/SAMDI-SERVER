CREATE TABLE email_verifications (
    email VARCHAR(254) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_expires_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    proof_expires_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
