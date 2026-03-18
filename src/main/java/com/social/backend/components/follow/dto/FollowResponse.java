package com.social.backend.components.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FollowResponse {

    private Long followerId;
    private String followerUsername;
    private Long followedId;
    private String followedUsername;
    private LocalDateTime createdAt;

}