package com.social.backend.components.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowCountResponse {

    private Long userId;
    private long followersCount;
    private long followingCount;
}