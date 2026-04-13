package com.social.backend.components.poll.entity;

import com.social.backend.components.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "poll_vote",
        uniqueConstraints = @UniqueConstraint(columnNames = {"poll_id", "user_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    @PrePersist
    protected void onCreate() {
        if (votedAt == null) votedAt = LocalDateTime.now();
    }
}