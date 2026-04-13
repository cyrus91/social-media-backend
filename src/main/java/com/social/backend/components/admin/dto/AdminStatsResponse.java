package com.social.backend.components.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStatsResponse {
    private long totalUsers;
    private long totalPosts;
    private long totalComments;
    private long bannedUsers;
    private long adminUsers;
}