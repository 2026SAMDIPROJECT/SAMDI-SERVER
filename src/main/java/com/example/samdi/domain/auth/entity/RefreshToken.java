package com.example.samdi.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id", unique = true)
    private Long userId;

    @Column(nullable = false, name = "token_hash")
    private String tokenHash;

    @Column(nullable = false, name ="session_id")
    private String sessionId;

    @Column(nullable = false, name = "expires_at")
    private LocalDateTime expiresAt;

    public void updateToken(String tokenHash, String sessionId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }
}
