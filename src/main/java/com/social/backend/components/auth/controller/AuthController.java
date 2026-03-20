package com.social.backend.components.auth.controller;

import com.social.backend.components.auth.dto.*;
import com.social.backend.components.auth.service.AuthService;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Autenticazione e gestione token")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrazione", description = "Registra un nuovo utente e invia email di verifica")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Effettua il login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (DisabledException e) {
            // Email non verificata
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "EMAIL_NOT_VERIFIED",
                            "message", "Devi verificare la tua email prima di accedere"
                    ));
        }
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verifica Email", description = "Verifica l'email tramite token")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verificata con successo!"));
        } catch (RuntimeException e) {
            if ("TOKEN_EXPIRED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(Map.of("error", "TOKEN_EXPIRED",
                                "message", "Il link è scaduto. Richiedi un nuovo link di verifica."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_TOKEN",
                            "message", "Token non valido"));
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Reinvia Email Verifica", description = "Reinvia l'email di verifica")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            authService.resendVerification(email);
            return ResponseEntity.ok(Map.of("message", "Email di verifica reinviata!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token")
    public TokenRefreshResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refreshToken(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout")
    public void logout(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Utente non autenticato");
        }
        User currentUser = userDetails.getUser();
        authService.logout(currentUser.getId());
    }
}