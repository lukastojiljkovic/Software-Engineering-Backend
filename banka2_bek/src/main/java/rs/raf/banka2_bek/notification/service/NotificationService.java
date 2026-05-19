package rs.raf.banka2_bek.notification.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.notification.dto.NotificationDto;
import rs.raf.banka2_bek.notification.model.NotificationType;

public interface NotificationService {

    /**
     * [B1 — Foundation] Single entry point for raising a notification: persists
     * the in-app record and, when the type has {@code sendsEmail = true}, also
     * dispatches an email via the RabbitMQ pipeline to {@code notification-service}.
     * All other modules must call only this method.
     *
     * <p>Task integration points:
     * <ul>
     *   <li>[B2 — Andjela] Call from {@code AccountLockoutService} on account
     *       lock: {@code notify(userId, "CLIENT", ACCOUNT_LOCKED, ...)}</li>
     *   <li>[B4 — Petar] Call from payment, transfers, card, loan, order and
     *       otc services on each event listed in {@link NotificationType}.</li>
     *   <li>[B5 — Aleksa] Call from the price-alert scheduler when a threshold
     *       is crossed: {@code notify(ownerId, recipientType, PRICE_ALERT, ...)}</li>
     *   <li>[B8 — Nikola Djurovic] Call from {@code RecurringOrderScheduler}
     *       when an order is skipped:
     *       {@code notify(ownerId, recipientType, RECURRING_ORDER_SKIPPED, ...)}</li>
     * </ul>
     *
     * <p>{@code referenceType} and {@code referenceId} are optional but strongly
     * recommended — they allow the frontend to deep-link to the related resource
     * and allow B4 to load domain-specific data when rendering type-tailored
     * email templates.
     */
    void notify(
            Long recipientId,
            String recipientType,
            NotificationType notificationType,
            String title,
            String body,
            String referenceType,
            Long referenceId
    );

    Page<NotificationDto> getMyNotifications(Long recipientId, String recipientType, boolean onlyUnread, int page, int size);

    Long getUnreadCount(Long recipientId, String recipientType);

    NotificationDto markOneRead(Long notificationId, Long recipientId, String recipientType);

    void markAllRead(Long recipientId, String recipientType);
}
