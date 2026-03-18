package com.social.backend.components.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username obbligatorio")
    @Size(min = 3, max = 20, message = "Username deve essere tra 3 e 20 caratteri")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Username può contenere solo lettere, numeri e underscore"
    )
    private String username;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 191, message = "L'email non può superare 191 caratteri")
    private String email;

    @NotBlank(message = "La password è obbligatoria")
    @Size(min = 6, message = "La password deve essere almeno 6 caratteri")
    private String password;

    @Size(max = 500, message = "La bio non può superare 500 caratteri")
    private String bio;

    private String avatarUrl;
}