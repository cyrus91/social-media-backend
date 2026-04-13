package com.social.backend.components.poll.entity;

import com.social.backend.components.post.entity.Post;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "poll")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Column(nullable = false, length = 300)
    private String question;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PollOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}