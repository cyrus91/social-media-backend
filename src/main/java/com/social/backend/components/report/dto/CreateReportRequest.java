package com.social.backend.components.report.dto;

import com.social.backend.components.report.entity.Report;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateReportRequest {

    @NotNull
    private Long postId;

    @NotNull
    private Report.ReportReason reason;

    private String notes;
}