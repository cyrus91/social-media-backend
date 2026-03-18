package com.social.backend.components.follow.service;

import com.social.backend.components.follow.dto.FollowRequest;
import com.social.backend.components.follow.dto.FollowResponse;

import java.util.List;

public interface FollowService {

    FollowResponse create(Long followerId, FollowRequest request);

    void delete(Long followerId, Long followedId);

    List<FollowResponse> listFollowers(Long userId);

    List<FollowResponse> listFollowing(Long userId);

    int countFollowers(Long userId);

    int countFollowing(Long userId);

    boolean isFollowing(Long followerId, Long followedId);
}