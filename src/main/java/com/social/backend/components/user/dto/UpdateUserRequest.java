package com.social.backend.components.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Email(message = "Email non valida")
    @Size(max = 191, message = "L'email non può superare 191 caratteri")
    private String email;

    @Size(max = 500, message = "La bio non può superare 500 caratteri")
    private String bio;

    private String avatarUrl;

    @Size(min = 6, message = "La password deve essere almeno 6 caratteri")
    private String newPassword;  // Opzionale - per cambiare password
}