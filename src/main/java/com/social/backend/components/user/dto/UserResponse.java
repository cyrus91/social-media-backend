package com.social.backend.components.user.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String bio;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Integer followerCount;
    private Integer followingCount;
    private Integer postCount;

    public UserResponse(Long id, String username, String email, String bio, String avatarUrl, LocalDateTime createdAt, Integer followerCount, Integer followingCount, Integer postCount) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.postCount = postCount;

    }

    public UserResponse() {

    }
}
