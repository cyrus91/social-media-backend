package com.social.backend.components.post.service;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.comment.repository.CommentRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final StorageService storageService;
    private final NotificationRepository notificationRepository;

    @Value("${storage.local.posts-dir}")
    private String postsDir;

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           LikeRepository likeRepository,
                           CommentRepository commentRepository,
                           FollowRepository followRepository,
                           StorageService storageService, NotificationRepository notificationRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.followRepository = followRepository;
        this.storageService = storageService;
        this.notificationRepository = notificationRepository;
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

        System.out.println(" Post creato - ID: " + savedPost.getId());

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

        System.out.println(" Post con immagine creato - ID: " + savedPost.getId());

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

        System.out.println("📋 Feed per user " + userId + " - Segue " + followedUserIds.size() + " utenti");

        //  SE NON SEGUE NESSUNO, RITORNA PAGINA VUOTA!
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (followedUserIds == null || followedUserIds.isEmpty()) {
            System.out.println("⚠️ Utente non segue nessuno - ritorno feed vuoto");
            return Page.empty(pageable);
        }

        //  USA FETCH JOIN!
        Page<Post> posts = postRepository.findByAuthorIdInWithAuthor(followedUserIds, pageable);

        System.out.println(" Feed caricato - " + posts.getNumberOfElements() + " post");

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

        System.out.println("🌍 Explore per user " + currentUserId + " - Esclusi " + excludedAuthorIds.size() + " utenti");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        //  SE TUTTI ESCLUSI, USA findAllWithAuthor
        if (excludedAuthorIds.isEmpty()) {
            System.out.println("⚠️ Nessun utente escluso - mostro tutti i post");
            Page<Post> posts = postRepository.findAllWithAuthor(pageable);
            return posts.map(post -> mapToResponse(post, currentUserId));
        }

        //  USA FETCH JOIN!
        Page<Post> posts = postRepository.findByAuthorIdNotInWithAuthor(excludedAuthorIds, pageable);

        System.out.println(" Explore caricato - " + posts.getNumberOfElements() + " post");

        return posts.map(post -> mapToResponse(post, currentUserId));
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
    public void removeImageFromPost(Long userId, Long postId, int imageIndex) {
        // Trova post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato"));

        // Verifica ownership
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }

        // Trova immagine per indice
        List<PostImage> images = post.getImages();

        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new IllegalArgumentException("Indice immagine non valido");
        }

        PostImage imageToRemove = images.get(imageIndex);

        // Estrai public_id (gestisce sia URL completi che public_id diretti)
        String imageUrl = imageToRemove.getImageUrl();
        String publicId = extractPublicIdFromUrl(imageUrl);

        // Elimina da Cloudinary
        try {
            System.out.println("🗑️ Eliminazione immagine: " + publicId);
            storageService.delete(publicId);
            System.out.println(" Immagine eliminata da storage");
        } catch (Exception e) {
            System.err.println("⚠️ Errore eliminazione da storage: " + e.getMessage());
            // Continua per rimuovere dal DB
        }

        // Rimuovi dal database
        images.remove(imageIndex);

        // Riordina gli indici
        for (int i = 0; i < images.size(); i++) {
            images.get(i).setDisplayOrder(i);
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
            System.err.println("⚠️ Errore parsing URL: " + imageUrl);
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
                System.err.println("⚠️ Impossibile eliminare vecchia immagine: " + e.getMessage());
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

        System.out.println("🗑️ Eliminazione post ID: " + postId);

        // ELIMINA PRIMA I COMMENTI ASSOCIATI!
        commentRepository.deleteByPostId(postId);
        System.out.println(" Commenti eliminati");

        // ELIMINA I LIKE ASSOCIATI!
        likeRepository.deleteByPostId(postId);
        System.out.println(" Like eliminati");

        // ELIMINA LE NOTIFICHE ASSOCIATE!
        notificationRepository.deleteByPostId(postId);
        System.out.println(" Notifiche eliminate");

        //  INFINE ELIMINA IL POST!
        postRepository.delete(post);
        System.out.println(" Post eliminato");
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

    // ============================================
    // MAPPER (SINGLE SOURCE OF TRUTH!)
    // ============================================

    private PostResponse mapToResponse(Post post, Long currentUserId) {
        // Conteggi
        int likeCount = likeRepository.countByPostId(post.getId());
        int commentCount = commentRepository.countByPostId(post.getId());
        boolean liked = currentUserId != null &&
                likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());

        //  Estrai URLs da PostImage entities
        List<String> imageUrls = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder)) // ORDINA
                .map(PostImage::getImageUrl)
                .toList();

        //  RETROCOMPATIBILITÀ: Se vuoto, usa imageUrl deprecato
        if (imageUrls.isEmpty() && post.getImageUrl() != null) {
            imageUrls = List.of(post.getImageUrl());
        }

        //  DEBUG LOG
        System.out.println("📊 Post #" + post.getId() + " - Immagini: " + imageUrls.size());

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrls(imageUrls)  //  LISTA COMPLETA
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))  //  Prima immagine
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .authorAvatarUrl(post.getAuthor().getAvatarUrl())
                .createdAt(post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .updatedAt(post.getUpdatedAt() != null ?
                        post.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .liked(liked)
                .build();
    }
}