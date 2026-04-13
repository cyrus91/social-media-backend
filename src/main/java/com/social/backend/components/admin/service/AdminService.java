package com.social.backend.components.admin.service;

import com.social.backend.components.admin.dto.AdminPostResponse;
import com.social.backend.components.admin.dto.AdminStatsResponse;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.enums.Role;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface AdminService {

    AdminStatsResponse getStats();

    Page<UserResponse> getAllUsers(int page, int size);

    Map<String, Object> toggleBan(Long userId);

    Map<String, Object> changeRole(Long userId, Role role);

    void deleteUser(Long userId);

    Page<AdminPostResponse> getAllPosts(int page, int size);

    void deletePost(Long postId);

    void deleteComment(Long commentId);
}