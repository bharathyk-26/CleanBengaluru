package com.cleanbengaluru.controller;

import com.cleanbengaluru.dto.ApiResponse;
import com.cleanbengaluru.dto.NotificationResponse;
import com.cleanbengaluru.security.CustomUserDetails;
import com.cleanbengaluru.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> all(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Notifications",
                notificationService.forUser(principal.getId())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Unread count",
                Map.of("count", notificationService.unreadCount(principal.getId()))));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Marked as read",
                notificationService.markRead(id, principal.getId())));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read"));
    }
}
