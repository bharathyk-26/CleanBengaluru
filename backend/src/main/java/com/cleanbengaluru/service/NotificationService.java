package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.NotificationResponse;
import com.cleanbengaluru.entity.Notification;
import com.cleanbengaluru.entity.User;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Database-backed notifications.
 *
 * ADDING EMAIL / PUSH LATER: keep `create(...)` as the single entry point, then publish a
 * Spring ApplicationEvent from it. An @Async @EventListener can send email via
 * spring-boot-starter-mail or a push via Firebase, without touching any business service.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void create(User user, String title, String message, Long reportId) {
        if (user == null) return;
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .reportId(reportId)
                .read(false)
                .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> forUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        // A user may only mark their own notification as read.
        if (!n.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Notification", notificationId);
        }
        n.setRead(true);
        return NotificationResponse.from(notificationRepository.save(n));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadForUser(userId);
    }
}
