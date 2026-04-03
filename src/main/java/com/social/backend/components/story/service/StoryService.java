package com.social.backend.components.story.service;

import com.social.backend.components.story.dto.StoryResponse;
import com.social.backend.components.story.dto.StoryViewerResponse;
import com.social.backend.components.story.dto.UserStoriesGroup;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StoryService {

    StoryResponse createStory(Long authorId, MultipartFile media, String caption);

    List<UserStoriesGroup> getFeedStories(Long currentUserId);

    List<StoryResponse> getUserStories(Long authorId, Long currentUserId);

    void markAsViewed(Long storyId, Long viewerId);

    List<StoryViewerResponse> getViewers(Long storyId, Long requesterId);

    void deleteStory(Long storyId, Long requesterId);

    void deleteExpiredStories();
}