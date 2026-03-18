package com.social.backend.components.notification.controller;

import com.social.backend.components.notification.dto.NotificationResponse;
import com.social.backend.components.notification.service.NotificationService;
import com.social.backend.components.user.entity.User;
import com.social.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Gestione notifiche")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Ottieni notifiche", description = "Ottieni tutte le notifiche dell'utente autenticato (paginate)")
    public Page<NotificationResponse> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User currentUser = userDetails.getUser();
        Pageable pageable = PageRequest.of(page, size);
        return notificationService.getNotifications(currentUser.getId(), pageable);
    }

    @GetMapping("/unread")
    @Operation(summary = "Ottieni notifiche non lette", description = "Ottieni solo le notifiche non lette")
    public Page<NotificationResponse> getUnreadNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User currentUser = userDetails.getUser();
        Pageable pageable = PageRequest.of(page, size);
        return notificationService.getUnreadNotifications(currentUser.getId(), pageable);
    }

    @GetMapping("/count")
    @Operation(summary = "Conta notifiche non lette", description = "Restituisce il numero di notifiche non lette")
    public ResponseEntity<Map<String, Long>> countUnreadNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        User currentUser = userDetails.getUser();
        Long count = notificationService.countUnreadNotifications(currentUser.getId());

        Map<String, Long> response = new HashMap<>();
        response.put("unreadCount", count);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Marca come letta", description = "Marca una notifica specifica come letta")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {

        User currentUser = userDetails.getUser();
        notificationService.markAsRead(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Marca tutte come lette", description = "Marca tutte le notifiche come lette")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User currentUser = userDetails.getUser();
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina notifica", description = "Elimina una notifica specifica")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id) {

        User currentUser = userDetails.getUser();
        notificationService.deleteNotification(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}