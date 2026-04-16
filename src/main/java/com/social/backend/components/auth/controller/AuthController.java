package com.social.backend.components.auth.controller;

import com.social.backend.common.exception.UnauthorizedException;
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
            throw new UnauthorizedException("Utente non autenticato");
        }
        User currentUser = userDetails.getUser();
        authService.logout(currentUser.getId());
    }

    // ==================== PASSWORD RESET ====================

    @PostMapping("/forgot-password")
    @Operation(summary = "Richiedi reset password", description = "Invia email con link di reset")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        try {
            authService.requestPasswordReset(body.get("email"));
            return ResponseEntity.ok(Map.of("message", "Email di reset inviata. Controlla la tua casella."));
        } catch (RuntimeException e) {
            if ("OAUTH2_ACCOUNT".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "OAUTH2_ACCOUNT",
                        "message", "Questo account usa il login con Google. Non puoi resettare la password."
                ));
            }
            // Risposta generica per sicurezza (non rivela se l'email esiste)
            return ResponseEntity.ok(Map.of("message", "Se l'email esiste, riceverai un link di reset."));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reimposta password", description = "Imposta nuova password tramite token")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        try {
            String token = body.get("token");
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "La password deve essere di almeno 6 caratteri"));
            }
            authService.resetPassword(token, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password reimpostata con successo!"));
        } catch (RuntimeException e) {
            if ("TOKEN_EXPIRED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                        "error", "TOKEN_EXPIRED", "message", "Il link è scaduto. Richiedi un nuovo reset."
                ));
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Token non valido o già usato."));
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambia password", description = "Cambia password quando autenticato")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody Map<String, String> body) {
        try {
            authService.changePassword(
                    userDetails.getUser().getId(),
                    body.get("currentPassword"),
                    body.get("newPassword")
            );
            return ResponseEntity.ok(Map.of("message", "Password aggiornata con successo!"));
        } catch (RuntimeException e) {
            return switch (e.getMessage()) {
                case "WRONG_PASSWORD" ->
                        ResponseEntity.badRequest().body(Map.of("error", "Password attuale non corretta."));
                case "OAUTH2_ACCOUNT" -> ResponseEntity.badRequest().body(Map.of(
                        "error", "OAUTH2_ACCOUNT", "message", "Questo account usa il login con Google."
                ));
                default -> ResponseEntity.badRequest().body(Map.of("error", "Errore nel cambio password."));
            };
        }
    }
}