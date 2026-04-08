package com.social.backend.components.report.entity;

import com.social.backend.components.post.entity.Post;
import com.social.backend.components.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report",
        indexes = {
                @Index(name = "idx_report_post", columnList = "post_id"),
                @Index(name = "idx_report_reporter", columnList = "reporter_id"),
                @Index(name = "idx_report_status", columnList = "status")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(length = 500)
    private String notes; // note aggiuntive dell'utente

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum ReportReason {
        SPAM,
        HATE_SPEECH,
        VIOLENCE,
        NUDITY,
        FALSE_INFORMATION,
        HARASSMENT,
        OTHER
    }

    public enum ReportStatus {
        PENDING,
        REVIEWED,
        DISMISSED
    }
}