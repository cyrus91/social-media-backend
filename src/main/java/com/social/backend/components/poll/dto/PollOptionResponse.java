package com.social.backend.components.poll.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollOptionResponse {
    private Long id;
    private String text;
    private Long voteCount;
    private double percentage;
}