package com.social.backend.components.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String bio;
    private String displayName;
    private String website;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Integer followerCount;
    private Integer followingCount;
    private Integer postCount;
    private String role;
    private boolean banned;
    private boolean emailVerified;
}