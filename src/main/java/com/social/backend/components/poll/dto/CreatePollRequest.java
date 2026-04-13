package com.social.backend.components.poll.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePollRequest {
    private String question;
    private List<String> options;
    private int durationHours;
}