package com.social.backend.components.poll.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {
    private Long optionId;
}