package com.social.backend.components.poll.service;

import com.social.backend.components.poll.dto.CreatePollRequest;
import com.social.backend.components.poll.dto.PollResponse;

public interface PollService {
    PollResponse createForPost(Long postId, CreatePollRequest request);
    PollResponse getByPostId(Long postId, Long currentUserId);
    PollResponse vote(Long pollId, Long optionId, Long userId);
}