package com.social.backend.components.user.service;

import com.social.backend.common.exception.DuplicateResourceException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.auth.repository.RefreshTokenRepository;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.user.dto.UpdateUserRequest;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.social.backend.components.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${storage.local.avatars-dir}")
    private String avatarsDir;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           StorageService storageService, FollowRepository followRepository, PostRepository postRepository, NotificationRepository notificationRepository, LikeRepository likeRepository, CommentRepository commentRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.notificationRepository = notificationRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + id));

        // ✅ CALCOLA I CONTATORI!
        Long followerCount = followRepository.countByFollowedId(user.getId());
        Long followingCount = followRepository.countByFollowerId(user.getId());
        Long postCount = Long.valueOf(postRepository.countByAuthorId(user.getId()));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .followerCount(followerCount != null ? followerCount.intValue() : 0)
                .followingCount(followingCount != null ? followingCount.intValue() : 0)
                .postCount(postCount != null ? postCount.intValue() : 0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato: " + username));

        // ✅ CALCOLA I CONTATORI DAL DB!
        Long followerCount = followRepository.countByFollowedId(user.getId());
        Long followingCount = followRepository.countByFollowerId(user.getId());
        Long postCount = Long.valueOf(postRepository.countByAuthorId(user.getId()));

        System.out.println("📊 Stats per " + username + " - Followers: " + followerCount + ", Following: " + followingCount + ", Posts: " + postCount);

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .followerCount(followerCount != null ? followerCount.intValue() : 0)
                .followingCount(followingCount != null ? followingCount.intValue() : 0)
                .postCount(postCount != null ? postCount.intValue() : 0)
                .build();
    }

    @Override
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Aggiorna email se fornita e diversa dall'attuale
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email già in uso: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        // Aggiorna bio se fornita
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        // Aggiorna avatarUrl se fornito
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Aggiorna password se fornita
        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            user.setPasswordHash(encodedPassword);
        }

        User savedUser = userRepository.save(user);
        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) {
        // Trova utente
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Elimina vecchio avatar se esiste
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            String oldFileName = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1);
            storageService.delete(avatarsDir + "/" + oldFileName);
        }

        // Salva nuovo avatar
        String fileName = storageService.store(file, avatarsDir);
        String avatarUrl = storageService.getFileUrl(fileName, avatarsDir);

        // Aggiorna utente
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }

    @Override
    @Transactional
    public UserResponse updateBio(Long userId, String bio) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Validazione lunghezza bio
        if (bio != null && bio.length() > 500) {
            throw new IllegalArgumentException("La bio non può superare 500 caratteri");
        }

        user.setBio(bio);
        User updatedUser = userRepository.save(user);

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Elimina avatar se esiste
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            String oldFileName = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1);
            storageService.delete(avatarsDir + "/" + oldFileName);
        }

        // Rimuovi URL dal database
        user.setAvatarUrl(null);
        userRepository.save(user);
    }

    @Override
    public List<UserResponse> searchUsers(String query) {
        // Validazione
        if (query == null || query.trim().isEmpty()) {
            return List.of(); // Ritorna lista vuota
        }

        // Ricerca
        List<User> users = userRepository.searchByUsername(query.trim());

        // Limita a 10 risultati
        return users.stream()
                .limit(10)
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    private UserResponse mapToUserResponse(User user) {
        // CALCOLA I CONTATORI
        Long followerCount = followRepository.countByFollowedId(user.getId());
        Long followingCount = followRepository.countByFollowerId(user.getId());
        Long postCount = Long.valueOf(postRepository.countByAuthorId(user.getId()));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .followerCount(followerCount != null ? followerCount.intValue() : 0)
                .followingCount(followingCount != null ? followingCount.intValue() : 0)
                .postCount(postCount != null ? postCount.intValue() : 0)
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato: " + username));

        // CALCOLA I CONTATORI DAL DB!
        Long followerCount = followRepository.countByFollowedId(user.getId());
        Long followingCount = followRepository.countByFollowerId(user.getId());
        Long postCount = Long.valueOf(postRepository.countByAuthorId(user.getId()));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .followerCount(followerCount != null ? followerCount.intValue() : 0)
                .followingCount(followingCount != null ? followingCount.intValue() : 0)
                .postCount(postCount != null ? postCount.intValue() : 0)
                .build();
    }

    @Override
    public List<UserResponse> getAll() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(user -> {
                    // ✅ CALCOLA CONTATORI PER OGNI UTENTE!
                    Long followerCount = followRepository.countByFollowedId(user.getId());
                    Long followingCount = followRepository.countByFollowerId(user.getId());
                    Long postCount = Long.valueOf(postRepository.countByAuthorId(user.getId()));

                    return UserResponse.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .bio(user.getBio())
                            .avatarUrl(user.getAvatarUrl())
                            .createdAt(user.getCreatedAt())
                            .followerCount(followerCount != null ? followerCount.intValue() : 0)
                            .followingCount(followingCount != null ? followingCount.intValue() : 0)
                            .postCount(postCount != null ? postCount.intValue() : 0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));

        System.out.println("🗑️ Eliminazione account: " + user.getUsername());

        // Le relazioni con @ManyToOne NON hanno cascade, quindi JPA non elimina automaticamente.
        // Eliminiamo manualmente nell'ordine corretto per evitare constraint violations.

        // 1. Elimina notifiche (sia ricevute che generate)
        notificationRepository.deleteByRecipientId(userId);
        notificationRepository.deleteByActorId(userId);

        // 2. Elimina follow relationships
        followRepository.deleteByFollowerId(userId);
        followRepository.deleteByFollowedId(userId);

        // 3. Elimina like sui post altrui
        likeRepository.deleteByUserId(userId);

        // 4. Elimina commenti sui post altrui
        commentRepository.deleteByAuthorId(userId);

        // 5. Elimina post (che elimineranno a cascata like/commenti sui propri post)
        postRepository.deleteByAuthorId(userId);

        // 6. Elimina refresh token
        refreshTokenRepository.deleteByUser(user);

        // 7. Elimina utente
        userRepository.delete(user);

        System.out.println("✅ Account eliminato: " + user.getUsername());
    }
}