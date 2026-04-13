package com.social.backend.components.poll.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollResponse {
    private Long id;
    private String question;
    private List<PollOptionResponse> options;
    private Long totalVotes;
    private Long votedOptionId;
    private boolean expired;
    private Instant expiresAt;
}