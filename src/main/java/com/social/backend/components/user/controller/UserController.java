package com.social.backend.components.user.controller;

import com.social.backend.common.exception.UnauthorizedException;
import com.social.backend.components.storage.dto.FileUploadResponse;
import com.social.backend.components.user.dto.UpdateUserRequest;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.service.UserService;
import com.social.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Gestione profili utente")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ========== ENDPOINT UTENTE CORRENTE ==========

    @GetMapping("/me")
    @Operation(summary = "Profilo corrente", description = "Ottieni il profilo dell'utente autenticato")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Autenticazione richiesta. Token JWT non valido o mancante.");
        }
        User currentUser = userDetails.getUser();
        return userService.getCurrentUser(currentUser.getId());
    }

    @PutMapping("/me")
    @Operation(summary = "Aggiorna profilo", description = "Aggiorna email, bio, avatar o password dell'utente corrente")
    public UserResponse updateCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateUserRequest request) {
        if (userDetails == null) {
            throw new UnauthorizedException("Autenticazione richiesta. Token JWT non valido o mancante.");
        }
        User currentUser = userDetails.getUser();
        return userService.updateCurrentUser(currentUser.getId(), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Avatar", description = "Carica immagine profilo utente")
    public FileUploadResponse uploadAvatar(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam("file") MultipartFile file) {

        if (userDetails == null) {
            throw new UnauthorizedException("Autenticazione richiesta. Token JWT non valido o mancante.");
        }

        User currentUser = userDetails.getUser();
        String avatarUrl = userService.updateAvatar(currentUser.getId(), file);

        return FileUploadResponse.builder()
                .fileName(avatarUrl.substring(avatarUrl.lastIndexOf("/") + 1))
                .fileUrl(avatarUrl)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .build();
    }

    @PutMapping("/me/bio")
    @Operation(summary = "Aggiorna Bio", description = "Aggiorna solo la bio dell'utente")
    public UserResponse updateBio(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody Map<String, String> body) {

        if (userDetails == null) {
            throw new UnauthorizedException("Autenticazione richiesta. Token JWT non valido o mancante.");
        }

        User currentUser = userDetails.getUser();
        String bio = body.get("bio");

        System.out.println("📝 Aggiornamento bio per user: " + currentUser.getId());

        return userService.updateBio(currentUser.getId(), bio);
    }

    @DeleteMapping("/me")
    @Operation(summary = "Elimina account", description = "Elimina permanentemente il proprio account")
    public ResponseEntity<Map<String, String>> deleteAccount(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        if (userDetails == null) {
            throw new UnauthorizedException("Autenticazione richiesta");
        }

        User currentUser = userDetails.getUser();
        userService.deleteUser(currentUser.getId());

        return ResponseEntity.ok(Map.of("message", "Account eliminato"));
    }

    // ========== ENDPOINT PUBBLICI ==========

    @GetMapping("/id/{id}")
    @Operation(summary = "Ottieni utente per ID", description = "Restituisce i dati di un utente specifico per ID")
    public UserResponse getUserById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/{username}")
    @Operation(summary = "Ottieni utente per username", description = "Restituisce i dati di un utente specifico per username")
    public UserResponse getUserByUsername(@PathVariable String username) {
        return userService.getByUsername(username);
    }

    // ============================================
    // GET - RICERCA UTENTI (PUBBLICO)
    // ============================================
    @GetMapping("/search")
    @Operation(summary = "Ricerca utenti", description = "Cerca utenti per username")
    public List<UserResponse> searchUsers(@RequestParam String q) {
        System.out.println("🔍 Ricerca utenti: " + q);
        List<UserResponse> results = userService.searchUsers(q);
        System.out.println("✅ Trovati " + results.size() + " utenti");
        return results;
    }
}