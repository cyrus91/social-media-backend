package com.social.backend.components.admin.controller;

import com.social.backend.components.admin.dto.AdminPostResponse;
import com.social.backend.components.admin.dto.AdminStatsResponse;
import com.social.backend.components.admin.service.AdminService;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse getStats() {
        return adminService.getStats();
    }

    @GetMapping("/users")
    public Page<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminService.getAllUsers(page, size);
    }

    @PutMapping("/users/{userId}/ban")
    public Map<String, Object> banUser(@PathVariable Long userId) {
        return adminService.toggleBan(userId);
    }

    @PutMapping("/users/{userId}/role")
    public Map<String, Object> changeRole(
            @PathVariable Long userId,
            @RequestParam Role role) {
        return adminService.changeRole(userId, role);
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
    }

    @GetMapping("/posts")
    public Page<AdminPostResponse> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminService.getAllPosts(page, size);
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long postId) {
        adminService.deletePost(postId);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        adminService.deleteComment(commentId);
    }
}