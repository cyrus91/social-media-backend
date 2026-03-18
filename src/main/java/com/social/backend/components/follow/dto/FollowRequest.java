package com.social.backend.components.follow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FollowRequest {

    @NotNull(message = "Il followedId è obbligatorio")
    @NotNull
    private Long followedId;
}