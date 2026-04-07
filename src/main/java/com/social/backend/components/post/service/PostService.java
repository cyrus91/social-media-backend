package com.social.backend.components.post.service;

import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.dto.UpdatePostRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PostService {

    // CREATE
    PostResponse create(Long userId, CreatePostRequest request);
    PostResponse createWithImage(Long userId, CreatePostRequest request, MultipartFile image);

    // READ
    PostResponse getById(Long id, Long currentUserId);
    Page<PostResponse> getAll(int page, int size, Long currentUserId);
    Page<PostResponse> getByAuthorId(Long authorId, int page, int size, Long currentUserId);

    // ✅ FEED - PARAMETRI SEMPLIFICATI!
    Page<PostResponse> getFeed(Long userId, int page, int size);
    Page<PostResponse> getExplorePosts(Long currentUserId, int page, int size);

    // SEARCH
    Page<PostResponse> searchByHashtag(String tag, int page, int size, Long currentUserId);

    // UPDATE
    PostResponse update(Long currentUserId, Long postId, UpdatePostRequest request);
    PostResponse addImageToPost(Long userId, Long postId, MultipartFile image);

    // DELETE
    void delete(Long currentUserId, Long postId);

    // COUNT
    int countByAuthor(Long authorId);
    void incrementViewCount(Long postId, Long currentUserId);

    PostResponse createWithImages(Long userId, CreatePostRequest request, List<MultipartFile> images);

    void removeImageFromPost(Long userId, Long postId, Long imageId);
    PostResponse addImagesToPost(Long userId, Long postId, List<MultipartFile> images);
}