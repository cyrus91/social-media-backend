package com.social.backend.components.poll.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "poll_option")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(nullable = false, length = 150)
    private String text;

    @Column(name = "vote_count", nullable = false)
    @Builder.Default
    private Long voteCount = 0L;
}