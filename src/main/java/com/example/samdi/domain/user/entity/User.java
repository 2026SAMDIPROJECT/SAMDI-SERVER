package com.example.samdi.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uq_users_login_id", columnNames = "login_id")

    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", columnDefinition = "bigint unsigned")
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "login_id", nullable = false, length = 50)
    private String userName;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
    }
}
