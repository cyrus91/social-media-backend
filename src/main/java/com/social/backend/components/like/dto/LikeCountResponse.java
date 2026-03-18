package com.social.backend.components.like.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeCountResponse {

    private Long postId;
    private long count;
}