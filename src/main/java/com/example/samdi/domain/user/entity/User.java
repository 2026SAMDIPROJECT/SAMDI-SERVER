package com.example.samdi.domain.user.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = User.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"),
                @UniqueConstraint(name = User.LOGIN_ID_UNIQUE_CONSTRAINT, columnNames = "login_id")
        }
)
public class User {

    public static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";
    public static final String LOGIN_ID_UNIQUE_CONSTRAINT = "uq_users_login_id";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", columnDefinition = "bigint unsigned")
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    protected User() {
    }

    @PrePersist
    public void onCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
    }

    @Builder
    public User(String email, String passwordHash, String loginId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.loginId = loginId;
    }
}
