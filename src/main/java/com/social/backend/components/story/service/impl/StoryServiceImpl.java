package com.social.backend.components.story.service.impl;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.NotFoundException;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.story.dto.StoryResponse;
import com.social.backend.components.story.dto.StoryViewerResponse;
import com.social.backend.components.story.dto.UserStoriesGroup;
import com.social.backend.components.story.entity.Story;
import com.social.backend.components.story.entity.StoryView;
import com.social.backend.components.story.repository.StoryRepository;
import com.social.backend.components.story.repository.StoryViewRepository;
import com.social.backend.components.story.service.StoryService;
import com.social.backend.components.storage.service.StorageService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final StorageService storageService;

    private static final List<String> IMAGE_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final List<String> VIDEO_TYPES = Arrays.asList("video/mp4", "video/quicktime", "video/webm");
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB

    @Override
    @Transactional
    public StoryResponse createStory(Long authorId, MultipartFile media, String caption) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));

        String contentType = media.getContentType();
        Story.MediaType mediaType;
        String mediaUrl;

        if (contentType != null && IMAGE_TYPES.contains(contentType)) {
            if (media.getSize() > MAX_IMAGE_SIZE) throw new RuntimeException("Immagine troppo grande (max 10MB)");
            mediaType = Story.MediaType.IMAGE;
            mediaUrl = storageService.store(media, "stories");
        } else if (contentType != null && VIDEO_TYPES.contains(contentType)) {
            if (media.getSize() > MAX_VIDEO_SIZE) throw new RuntimeException("Video troppo grande (max 50MB)");
            mediaType = Story.MediaType.VIDEO;
            mediaUrl = storageService.storeRaw(media, "stories/videos");
        } else {
            throw new RuntimeException("Formato non supportato. Usa JPG, PNG, GIF, MP4 o WebM.");
        }

        LocalDateTime now = LocalDateTime.now();
        Story story = Story.builder()
                .author(author)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .caption(caption != null ? caption.trim() : null)
                .createdAt(now)
                .expiresAt(now.plusHours(24))
                .build();

        story = storyRepository.save(story);
        return StoryResponse.from(story, false, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserStoriesGroup> getFeedStories(Long currentUserId) {
        List<Long> followingIds = followRepository.findFollowingIdsByUserId(currentUserId);

        // Includi le proprie storie + quelle degli utenti seguiti
        List<Long> authorIds = new ArrayList<>(followingIds);
        if (!authorIds.contains(currentUserId)) authorIds.add(currentUserId);

        if (authorIds.isEmpty()) return Collections.emptyList();

        LocalDateTime now = LocalDateTime.now();
        List<Story> stories = storyRepository.findActiveFeedStories(authorIds, now);
        if (stories.isEmpty()) return Collections.emptyList();

        List<Long> storyIds = stories.stream().map(Story::getId).collect(Collectors.toList());
        List<Long> viewedIds = storyViewRepository.findViewedStoryIds(currentUserId, storyIds);
        Set<Long> viewedSet = new HashSet<>(viewedIds);

        // Raggruppa per autore mantenendo ordine: proprie storie prima, poi per non-viste
        Map<Long, List<Story>> byAuthor = new LinkedHashMap<>();
        for (Story s : stories) {
            byAuthor.computeIfAbsent(s.getAuthor().getId(), k -> new ArrayList<>()).add(s);
        }

        List<UserStoriesGroup> groups = byAuthor.entrySet().stream()
                .map(entry -> {
                    List<Story> userStories = entry.getValue();
                    User author = userStories.get(0).getAuthor();
                    boolean hasUnread = userStories.stream().anyMatch(s -> !viewedSet.contains(s.getId()));
                    boolean isOwn = author.getId().equals(currentUserId);

                    List<StoryResponse> storyResponses = userStories.stream()
                            .map(s -> StoryResponse.from(s, viewedSet.contains(s.getId()),
                                    storyViewRepository.countByStoryId(s.getId())))
                            .collect(Collectors.toList());

                    return UserStoriesGroup.builder()
                            .authorId(author.getId())
                            .authorUsername(author.getUsername())
                            .authorAvatarUrl(author.getAvatarUrl())
                            .stories(storyResponses)
                            .hasUnread(hasUnread)
                            .isOwn(isOwn)
                            .build();
                })
                .collect(Collectors.toList());

        // Ordina: proprie storie prime, poi non lette, poi lette
        groups.sort((a, b) -> {
            if (a.isOwn() && !b.isOwn()) return -1;
            if (!a.isOwn() && b.isOwn()) return 1;
            if (a.isHasUnread() && !b.isHasUnread()) return -1;
            if (!a.isHasUnread() && b.isHasUnread()) return 1;
            return 0;
        });

        return groups;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getUserStories(Long authorId, Long currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        List<Story> stories = storyRepository.findActiveByAuthorId(authorId, now);

        List<Long> storyIds = stories.stream().map(Story::getId).collect(Collectors.toList());
        List<Long> viewedIds = storyIds.isEmpty() ? Collections.emptyList()
                : storyViewRepository.findViewedStoryIds(currentUserId, storyIds);
        Set<Long> viewedSet = new HashSet<>(viewedIds);

        return stories.stream()
                .map(s -> StoryResponse.from(s, viewedSet.contains(s.getId()),
                        storyViewRepository.countByStoryId(s.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAsViewed(Long storyId, Long viewerId) {
        if (storyViewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) return;

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new NotFoundException("Storia non trovata"));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));

        // Non registrare la propria visualizzazione
        if (story.getAuthor().getId().equals(viewerId)) return;

        StoryView view = StoryView.builder()
                .story(story)
                .viewer(viewer)
                .viewedAt(LocalDateTime.now())
                .build();
        storyViewRepository.save(view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryViewerResponse> getViewers(Long storyId, Long requesterId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new NotFoundException("Storia non trovata"));

        if (!story.getAuthor().getId().equals(requesterId)) {
            throw new ForbiddenException("Solo l'autore può vedere chi ha visualizzato la storia");
        }

        return storyViewRepository.findByStoryId(storyId).stream()
                .map(sv -> StoryViewerResponse.builder()
                        .userId(sv.getViewer().getId())
                        .username(sv.getViewer().getUsername())
                        .avatarUrl(sv.getViewer().getAvatarUrl())
                        .viewedAt(sv.getViewedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteStory(Long storyId, Long requesterId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new NotFoundException("Storia non trovata"));

        if (!story.getAuthor().getId().equals(requesterId)) {
            throw new ForbiddenException("Non puoi eliminare questa storia");
        }

        try { storageService.delete(story.getMediaUrl()); } catch (Exception ignored) {}
        storyRepository.delete(story);
    }

    @Override
    @Scheduled(fixedRate = 3600000) // ogni ora
    @Transactional
    public void deleteExpiredStories() {
        LocalDateTime now = LocalDateTime.now();
        // Prima elimina le view (FK constraint), poi le storie
        storyViewRepository.deleteByExpiredStories(now);
        int deleted = storyRepository.deleteExpiredStories(now);
        if (deleted > 0) System.out.println("🧹 Storie scadute eliminate: " + deleted);
    }
}