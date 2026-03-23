package com.social.backend.components.post.controller;

import com.social.backend.components.like.dto.LikeRequest;
import com.social.backend.components.like.service.LikeService;
import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.dto.UpdatePostRequest;
import com.social.backend.components.post.service.PostService;
import com.social.backend.components.storage.dto.FileUploadResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@Tag(name = "Posts", description = "Gestione post del social network")
public class PostController {

    private final PostService postService;
    private final LikeService likeService;

    public PostController(PostService postService, LikeService likeService) {
        this.postService = postService;
        this.likeService = likeService;
    }

    // ============================================
    // CREATE
    // ============================================

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea Post (Solo Testo)")
    public PostResponse createPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreatePostRequest request) {

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Il contenuto non può essere vuoto");
        }

        User currentUser = userDetails.getUser();
        return postService.create(currentUser.getId(), request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea Post con Immagini (max 5)")
    public PostResponse createPostWithImages(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {

        User currentUser = userDetails.getUser();

        // Validazione
        if (images != null && images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per post");
        }

        CreatePostRequest request = new CreatePostRequest();
        request.setContent(content);

        // Se c'è una sola immagine, usa il metodo vecchio (retrocompatibilità)
        if (images != null && images.size() == 1) {
            return postService.createWithImage(currentUser.getId(), request, images.get(0));
        }

        // Altrimenti usa il nuovo metodo multi-immagine
        return postService.createWithImages(currentUser.getId(), request, images);
    }

    // ============================================
    // READ
    // ============================================

    @GetMapping
    @Operation(summary = "Ottieni tutti i post")
    public Page<PostResponse> getAll(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long currentUserId = userDetails != null ? userDetails.getUser().getId() : null;
        return postService.getAll(page, size, currentUserId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ottieni post per ID")
    public PostResponse getById(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {

        Long currentUserId = userDetails != null ? userDetails.getUser().getId() : null;
        return postService.getById(id, currentUserId);
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "Ottieni post di un utente")
    public Page<PostResponse> getByAuthor(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long currentUserId = userDetails != null ? userDetails.getUser().getId() : null;
        return postService.getByAuthorId(authorId, page, size, currentUserId);
    }

    // ============================================
// GET FEED PERSONALIZZATO (PROTETTO)
// ============================================
    @GetMapping("/feed")
    @Operation(summary = "Feed personalizzato", description = "Restituisce i post degli utenti seguiti dall'utente autenticato")
    public Page<PostResponse> getFeed(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User currentUser = userDetails.getUser();

        // ✅ PASSA page e size SEPARATI!
        return postService.getFeed(currentUser.getId(), page, size);
    }

    // ============================================
// GET EXPLORE FEED (PUBBLICO/AUTENTICATO)
// ============================================
    @GetMapping("/explore")
    @Operation(summary = "Feed Esplora", description = "Post popolari/recenti (anche di utenti non seguiti)")
    public ResponseEntity<Page<PostResponse>> getExplorePosts(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // ✅ Se autenticato, escludi utenti seguiti
        if (userDetails != null) {
            User currentUser = userDetails.getUser();
            Page<PostResponse> posts = postService.getExplorePosts(currentUser.getId(), page, size);
            return ResponseEntity.ok(posts);
        }

        // Se non autenticato, mostra tutti i post
        Page<PostResponse> posts = postService.getAll(page, size, null);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/author/{authorId}/count")
    @Operation(summary = "Conta post di un utente")
    public Map<String, Integer> countByAuthor(@PathVariable Long authorId) {
        int count = postService.countByAuthor(authorId);
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        return response;
    }

    // ============================================
    // UPDATE
    // ============================================

    @PutMapping("/{id}")
    @Operation(summary = "Aggiorna post")
    public PostResponse update(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request) {

        User currentUser = userDetails.getUser();
        return postService.update(currentUser.getId(), id, request);
    }

    @PostMapping("/{id}/image")
    @Operation(summary = "Aggiungi immagine a post")
    public FileUploadResponse addImageToPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        User currentUser = userDetails.getUser();
        PostResponse post = postService.addImageToPost(currentUser.getId(), id, file);

        return FileUploadResponse.builder()
                .fileName(post.getImageUrl().substring(post.getImageUrl().lastIndexOf("/") + 1))
                .fileUrl(post.getImageUrl())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .build();
    }

    // ============================================
// POST IMAGE MANAGEMENT
// ============================================

    @DeleteMapping("/{postId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rimuovi immagine da post", description = "Usa l'ID dell'immagine (campo 'id' in 'images') per identificarla in modo stabile")
    public void removeImage(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId,
            @PathVariable Long imageId) {

        User currentUser = userDetails.getUser();
        postService.removeImageFromPost(currentUser.getId(), postId, imageId);
    }

    @PostMapping(value = "/{postId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Aggiungi immagini a post esistente")
    public PostResponse addImages(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId,
            @RequestParam("images") List<MultipartFile> images) {

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Nessuna immagine fornita");
        }

        if (images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per volta");
        }

        User currentUser = userDetails.getUser();
        return postService.addImagesToPost(currentUser.getId(), postId, images);
    }

    // ============================================
    // DELETE
    // ============================================

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina post")
    public void delete(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {

        User currentUser = userDetails.getUser();
        postService.delete(currentUser.getId(), id);
    }

    // ============================================
// POST - Toggle Like (Like/Unlike) di un post
// ============================================
    @PostMapping("/{postId}/like")
    @Operation(summary = "Toggle like su un post", description = "Mette like se non presente, rimuove se già presente")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId) {

        User currentUser = userDetails.getUser();
        Long userId = currentUser.getId();

        System.out.println("❤️ Toggle like - User: " + userId + ", Post: " + postId);

        // Controlla se esiste già il like
        boolean alreadyLiked = likeService.hasUserLikedPost(userId, postId);

        Map<String, Object> response = new HashMap<>();

        if (alreadyLiked) {
            // Rimuovi like
            likeService.delete(userId, postId);
            response.put("liked", false);
            response.put("message", "Like rimosso");
            System.out.println("💔 Like rimosso");
        } else {
            // Aggiungi like
            LikeRequest likeRequest = new LikeRequest();
            likeRequest.setPostId(postId);
            likeService.create(userId, likeRequest);
            response.put("liked", true);
            response.put("message", "Like aggiunto");
            System.out.println("❤️ Like aggiunto");
        }

        // Ritorna conteggio aggiornato
        int likeCount = likeService.countByPost(postId);
        response.put("likeCount", likeCount);

        return ResponseEntity.ok(response);
    }
}