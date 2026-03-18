package com.social.backend.components.user.service;

import com.social.backend.components.user.dto.RegisterRequest;
import com.social.backend.components.user.dto.UpdateUserRequest;
import com.social.backend.components.user.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    UserResponse getById(Long id);

    UserResponse getByUsername(String username);

    UserResponse getCurrentUser(Long userId);

    UserResponse updateCurrentUser(Long userId, UpdateUserRequest request);

    String updateAvatar(Long userId, MultipartFile file);

    List<UserResponse> searchUsers(String query);

    UserResponse updateBio(Long userId, String bio);

    void deleteAvatar(Long userId);

    UserResponse getUserByUsername(String username);

    List<UserResponse> getAll();

    void deleteUser(Long userId);
}