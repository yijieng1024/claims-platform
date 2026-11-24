package com.example.claims.reporting;

import io.swagger.v3.oas.annotations.Operation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** In-app notifications written by the event consumer; the UI polls the list (design section 3). */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final JdbcClient jdbc;

    NotificationController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record NotificationView(UUID id, UUID claimId, String claimNumber, String type, String message,
            OffsetDateTime createdAt, OffsetDateTime readAt) {
    }

    @Operation(summary = "My notifications: unread first, newest first, at most 50")
    @GetMapping
    List<NotificationView> mine(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
                        SELECT id, claim_id, claim_number, type, message, created_at, read_at FROM notifications
                        WHERE user_id = ? ORDER BY read_at IS NOT NULL, created_at DESC LIMIT 50""")
                .param(UUID.fromString(jwt.getSubject())).query(NotificationView.class).list();
    }

    @Operation(summary = "Mark one of my notifications as read (404 if it isn't mine)")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        int updated = jdbc.sql("UPDATE notifications SET read_at = coalesce(read_at, now()) WHERE id = ? AND user_id = ?")
                .params(id, UUID.fromString(jwt.getSubject())).update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }
}
