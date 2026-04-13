package com.social.backend.components.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "`user`",
        indexes = {
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_email", columnList = "email")
        })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Setter
    @Column(nullable = false, unique = true, length = 191)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Setter
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Setter
    @Column(name = "website", length = 255)
    private String website;

    @Setter
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Setter
    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified = false;

    @Setter
    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Setter
    @Column(name = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    @Setter
    @Column(name = "role", nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER'")
    private String role = "USER";

    @Setter
    @Column(name = "banned", nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    @Setter
    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    @Setter
    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Setter
    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = "USER";
    }

    public User(String username, String email, String passwordHash, String bio, String avatarUrl) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.emailVerified = false;
        this.role = "USER";
        this.banned = false;
    }
}