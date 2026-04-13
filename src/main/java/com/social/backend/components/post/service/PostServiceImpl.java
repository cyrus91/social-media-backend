package com.social.backend.components.post.service;

import lombok.extern.slf4j.Slf4j;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.dto.PollOptionResponse;
import com.social.backend.components.bookmark.repository.BookmarkRepository;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.dto.UpdatePostRequest;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.entity.PostImage;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.storage.service.StorageService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.social.backend.components.mention.MentionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final FollowRepository followRepository;
    private final StorageService storageService;
    private final NotificationRepository notificationRepository;
    private final MentionService mentionService;

    @Value("${storage.local.posts-dir}")
    private String postsDir;

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           LikeRepository likeRepository,
                           CommentRepository commentRepository, BookmarkRepository bookmarkRepository, PollRepository pollRepository, PollVoteRepository pollVoteRepository,
                           FollowRepository followRepository,
                           StorageService storageService, NotificationRepository notificationRepository,
                           MentionService mentionService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.pollRepository = pollRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.followRepository = followRepository;
        this.storageService = storageService;
        this.notificationRepository = notificationRepository;
        this.mentionService = mentionService;
    }

    // ============================================
    // CREATE
    // ============================================

    @Override
    @Transactional
    public PostResponse create(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        Post post = Post.builder()
                .content(request.getContent())
                .author(author)
                .build();

        Post savedPost = postRepository.save(post);
        mentionService.processMentions(request.getContent(), userId, savedPost.getId(), null,
                userRepository.findById(userId).map(u -> u.getUsername()).orElse(""));

        log.info("{}", " Post creato - ID: " + savedPost.getId());

        return mapToResponse(savedPost, userId);
    }

    @Override
    @Transactional
    public PostResponse createWithImage(Long userId, CreatePostRequest request, MultipartFile image) {
        // Validazione: almeno content o immagine
        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
                (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Il post deve avere almeno un contenuto testuale o un'immagine");
        }

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));

        // Upload immagine se presente
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String fileName = storageService.store(image, postsDir);
            imageUrl = storageService.getFileUrl(fileName, postsDir);
        }

        Post post = Post.builder()
                .content(request.getContent())
                .imageUrl(imageUrl)
                .author(author)
                .build();

        Post savedPost = postRepository.save(post);
        mentionService.processMentions(request.getContent(), userId, savedPost.getId(), null,
                userRepository.findById(userId).map(u -> u.getUsername()).orElse(""));

        log.info("{}", " Post con immagine creato - ID: " + savedPost.getId());

        return mapToResponse(savedPost, userId);
    }

    @Override
    @Transactional
    public PostResponse createWithImages(Long userId, CreatePostRequest request, List<MultipartFile> images) {
        // Validazione
        if (images != null && images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per post");
        }

        // Verifica che ci sia contenuto o immagini
        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
                (images == null || images.isEmpty())) {
            throw new IllegalArgumentException("Il post deve avere contenuto o almeno un'immagine");
        }

        // Trova autore
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));

        // Crea post
        Post post = Post.builder()
                .content(request.getContent())
                .author(author)
                .build();

        // Salva post (per avere ID)
        Post savedPost = postRepository.save(post);

        // Upload immagini
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);

                // Upload su storage
                String fileName = storageService.store(image, postsDir);
                String imageUrl = storageService.getFileUrl(fileName, postsDir);

                // Crea PostImage
                savedPost.addImage(imageUrl, i);
            }

            // Salva con immagini
            savedPost = postRepository.save(savedPost);
        }

        // Processa mention @username nel post
        mentionService.processMentions(request.getContent(), userId, savedPost.getId(), null,
                author.getUsername());

        return mapToResponse(savedPost, userId);
    }

    // ============================================
    // READ
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PostResponse getById(Long id, Long currentUserId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + id));
        return mapToResponse(post, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getAll(int page, int size, Long currentUserId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        //  USA FETCH JOIN per evitare N+1 queries!
        Page<Post> posts = postRepository.findAllWithAuthor(pageable);

        return posts.map(post -> mapToResponse(post, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getByAuthorId(Long authorId, int page, int size, Long currentUserId) {
        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + authorId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        //  USA FETCH JOIN!
        Page<Post> posts = postRepository.findByAuthorIdWithAuthor(authorId, pageable);

        return posts.map(post -> mapToResponse(post, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + userId);
        }

        // Ottieni lista utenti seguiti
        List<Long> followedUserIds = followRepository.findFollowingIdsByUserId(userId);

        log.info("{}", "📋 Feed per user " + userId + " - Segue " + followedUserIds.size() + " utenti");

        //  SE NON SEGUE NESSUNO, RITORNA PAGINA VUOTA!
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (followedUserIds == null || followedUserIds.isEmpty()) {
            log.info("⚠️ Utente non segue nessuno - ritorno feed vuoto");
            return Page.empty(pageable);
        }

        //  USA FETCH JOIN!
        Page<Post> posts = postRepository.findByAuthorIdInWithAuthor(followedUserIds, pageable);

        log.info("{}", " Feed caricato - " + posts.getNumberOfElements() + " post");

        return posts.map(post -> mapToResponse(post, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getExplorePosts(Long currentUserId, int page, int size) {
        // Ottieni utenti seguiti + se stesso
        Set<Long> excludedAuthorIds = followRepository.findByFollowerId(currentUserId)
                .stream()
                .map(follow -> follow.getFollowed().getId())
                .collect(Collectors.toSet());

        excludedAuthorIds.add(currentUserId);

        log.info("{}", "🌍 Explore per user " + currentUserId + " - Esclusi " + excludedAuthorIds.size() + " utenti");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        //  SE TUTTI ESCLUSI, USA findAllWithAuthor
        if (excludedAuthorIds.isEmpty()) {
            log.info("⚠️ Nessun utente escluso - mostro tutti i post");
            Page<Post> posts = postRepository.findAllWithAuthor(pageable);
            return posts.map(post -> mapToResponse(post, currentUserId));
        }

        //  USA FETCH JOIN!
        Page<Post> posts = postRepository.findByAuthorIdNotInWithAuthor(excludedAuthorIds, pageable);

        log.info("{}", " Explore caricato - " + posts.getNumberOfElements() + " post");

        return posts.map(post -> mapToResponse(post, currentUserId));
    }

    @Override
    public Page<PostResponse> searchByHashtag(String tag, int page, int size, Long currentUserId) {
        // Rimuovi il # se presente (es. "#napoli" → "napoli")
        String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return postRepository.findByHashtag(cleanTag, pageable)
                .map(post -> mapToResponse(post, currentUserId));
    }

    // ============================================
    // UPDATE
    // ============================================

    @Override
    @Transactional
    public PostResponse update(Long currentUserId, Long postId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + postId));

        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }

        post.setContent(request.getContent());
        Post updatedPost = postRepository.save(post);

        return mapToResponse(updatedPost, currentUserId);
    }

    @Override
    @Transactional
    public void removeImageFromPost(Long userId, Long postId, Long imageId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }

        // Trova l'immagine per ID (stabile, non per indice posizionale)
        PostImage imageToRemove = post.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Immagine non trovata con ID: " + imageId));

        String imageUrl = imageToRemove.getImageUrl();
        String publicId = extractPublicIdFromUrl(imageUrl);

        try {
            log.info("{}", "🗑️ Eliminazione immagine: " + publicId);
            storageService.delete(publicId);
            log.info("✅ Immagine eliminata da storage");
        } catch (Exception e) {
            log.error("{}", "⚠️ Errore eliminazione da storage: " + e.getMessage());
        }

        // Rimuovi dalla lista (orphanRemoval = true farà il DELETE)
        post.getImages().remove(imageToRemove);

        // Riordina i displayOrder rimanenti in modo sicuro
        List<PostImage> remaining = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .collect(Collectors.toList());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i);
        }

        postRepository.save(post);
    }

    /**
     * Estrae il public_id dall'URL di Cloudinary
     */
    private String extractPublicIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        // Se è già un public_id (non contiene http), ritorna così
        if (!imageUrl.startsWith("http")) {
            return imageUrl;
        }

        try {
            // Trova "/upload/"
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                return imageUrl;
            }

            String afterUpload = imageUrl.substring(uploadIndex + 8);

            // Rimuovi versione (v1234567/)
            if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
            }

            // Rimuovi estensione
            int lastDot = afterUpload.lastIndexOf(".");
            if (lastDot > 0) {
                afterUpload = afterUpload.substring(0, lastDot);
            }

            return afterUpload;

        } catch (Exception e) {
            log.error("{}", "⚠️ Errore parsing URL: " + imageUrl);
            return imageUrl;
        }
    }

    @Override
    @Transactional
    public PostResponse addImagesToPost(Long userId, Long postId, List<MultipartFile> images) {
        // Trova post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));

        // Verifica ownership
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }

        // Validazione: max 5 immagini totali
        int currentImageCount = post.getImages().size();
        if (currentImageCount + images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per post. Hai già " + currentImageCount);
        }

        // Upload nuove immagini
        for (MultipartFile image : images) {
            String fileName = storageService.store(image, postsDir);
            String imageUrl = storageService.getFileUrl(fileName, postsDir);

            post.addImage(imageUrl, currentImageCount++);
        }

        Post savedPost = postRepository.save(post);
        return mapToResponse(savedPost, userId);
    }

    @Override
    @Transactional
    public PostResponse addImageToPost(Long userId, Long postId, MultipartFile image) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + postId));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non sei autorizzato a modificare questo post");
        }

        // Elimina vecchia immagine se esiste
        if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
            String oldFileName = post.getImageUrl().substring(post.getImageUrl().lastIndexOf("/") + 1);
            try {
                storageService.delete(postsDir + "/" + oldFileName);
            } catch (Exception e) {
                log.error("{}", "⚠️ Impossibile eliminare vecchia immagine: " + e.getMessage());
            }
        }

        // Upload nuova immagine
        String fileName = storageService.store(image, postsDir);
        String imageUrl = storageService.getFileUrl(fileName, postsDir);

        post.setImageUrl(imageUrl);
        Post updatedPost = postRepository.save(post);

        return mapToResponse(updatedPost, userId);
    }

    // ============================================
    // DELETE
    // ============================================

    @Override
    @Transactional
    public void delete(Long currentUserId, Long postId) {
        // Trova il post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + postId));

        // Verifica che sia l'autore
        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi eliminare questo post");
        }

        log.info("{}", "🗑️ Eliminazione post ID: " + postId);

        // ELIMINA PRIMA I COMMENTI ASSOCIATI!
        commentRepository.deleteByPostId(postId);
        log.info(" Commenti eliminati");

        // ELIMINA I LIKE ASSOCIATI!
        likeRepository.deleteByPostId(postId);
        log.info(" Like eliminati");

        // ELIMINA LE NOTIFICHE ASSOCIATE!
        notificationRepository.deleteByPostId(postId);
        log.info(" Notifiche eliminate");

        // ELIMINA IL SONDAGGIO SE PRESENTE (votes prima per FK)
        pollRepository.findByPostIdWithOptions(postId).ifPresent(poll -> {
            pollVoteRepository.deleteByPollId(poll.getId());
            pollRepository.delete(poll);
            log.info("\u2705 Sondaggio eliminato");
        });

        //  INFINE ELIMINA IL POST!
        postRepository.delete(post);
        log.info(" Post eliminato");
    }

    // ============================================
    // COUNT
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public int countByAuthor(Long authorId) {
        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + authorId);
        }
        return postRepository.countByAuthorId(authorId);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));
        // Non contare le view dell'autore sul proprio post
        if (post.getAuthor().getId().equals(currentUserId)) return;
        postRepository.incrementViewCount(postId);
    }

    private PostResponse mapToResponse(Post post, Long currentUserId) {
        // Conteggi
        int likeCount = likeRepository.countByPostId(post.getId());
        int commentCount = commentRepository.countByPostId(post.getId());
        boolean liked = currentUserId != null &&
                likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        boolean bookmarked = currentUserId != null &&
                bookmarkRepository.existsByUserIdAndPostId(currentUserId, post.getId());

        // Carica sondaggio se presente
        PollResponse pollData = null;
        try {
            pollData = pollRepository.findByPostIdWithOptions(post.getId()).map(p -> {
                long total = p.getOptions().stream().mapToLong(o -> o.getVoteCount() != null ? o.getVoteCount() : 0L).sum();
                Long votedOptionId = currentUserId != null
                        ? pollVoteRepository.findVotedOptionId(p.getId(), currentUserId).orElse(null)
                        : null;
                return PollResponse.builder()
                        .id(p.getId())
                        .question(p.getQuestion())
                        .options(p.getOptions().stream().map(o -> PollOptionResponse.builder()
                                .id(o.getId()).text(o.getText())
                                .voteCount(o.getVoteCount() != null ? o.getVoteCount() : 0L)
                                .percentage(total > 0 ? (o.getVoteCount() != null ? o.getVoteCount() : 0L) * 100.0 / total : 0)
                                .build()).toList())
                        .totalVotes(total)
                        .votedOptionId(votedOptionId)
                        .expired(p.isExpired())
                        .expiresAt(p.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant())
                        .build();
            }).orElse(null);
        } catch (Exception e) {
            log.error("{}", "⚠️ Errore caricamento poll per post " + post.getId() + ": " + e.getMessage());
        }

        //  Estrai PostImageDto da PostImage entities (con ID stabile per il frontend)
        List<com.social.backend.components.post.dto.PostImageDto> imageDtos = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .map(img -> com.social.backend.components.post.dto.PostImageDto.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();

        //  Estrai URLs per retrocompatibilità
        List<String> imageUrls = imageDtos.stream()
                .map(com.social.backend.components.post.dto.PostImageDto::getImageUrl)
                .toList();

        //  RETROCOMPATIBILITÀ: Se vuoto, usa imageUrl deprecato
        if (imageUrls.isEmpty() && post.getImageUrl() != null) {
            imageUrls = List.of(post.getImageUrl());
        }

        //  DEBUG LOG
        log.info("{}", "📊 Post #" + post.getId() + " - Immagini: " + imageUrls.size());

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .images(imageDtos)             //  OGGETTI COMPLETI CON ID
                .imageUrls(imageUrls)          //  RETROCOMPATIBILITÀ
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .authorAvatarUrl(post.getAuthor().getAvatarUrl())
                .createdAt(post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .updatedAt(post.getUpdatedAt() != null ?
                        post.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .viewCount(post.getViewCount())
                .liked(liked)
                .bookmarked(bookmarked)
                .poll(pollData)
                .build();
    }
}