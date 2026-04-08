package com.social.backend.components.report.dto;

import com.social.backend.components.report.entity.Report;
import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReportResponse {
    private Long id;
    private Long postId;
    private String postContent;
    private String postAuthorUsername;
    private Long reporterId;
    private String reporterUsername;
    private Report.ReportReason reason;
    private Report.ReportStatus status;
    private String notes;
    private Instant createdAt;
}