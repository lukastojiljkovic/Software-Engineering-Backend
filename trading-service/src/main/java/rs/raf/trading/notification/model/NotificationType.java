package rs.raf.trading.notification.model;

import lombok.Getter;

/**
 * [B4 — port iz banka2_bek] Notifikacioni tipovi za trgovinski domen.
 *
 * <p>Trading-service publish-uje samo trgovinske evente (order lifecycle + OTC).
 * Ostali tipovi (PAYMENT, TRANSFER, LIMIT_CHANGE, CARD_*, LOAN_*, ACCOUNT_LOCKED)
 * zive u banka2_bek/notification/model i nisu deo trgovinskog domena.
 *
 * <p>Trading {@code NotificationServiceImpl} svaki event publish-uje na RabbitMQ
 * kao {@code IN_APP_GENERIC} (best-effort, bez perzistencije — banka2_bek je
 * vlasnik {@code notifications} tabele).
 */
@Getter
public enum NotificationType {

    // Order lifecycle events
    ORDER_PENDING(false),
    ORDER_APPROVED(false),
    ORDER_DECLINED(false),
    ORDER_EXECUTED(false),
    ORDER_PARTIAL_FILL(false),
    ORDER_CANCELLED(false),

    // OTC events
    OTC_COUNTER_OFFER(false),
    OTC_ACCEPTED(false),
    OTC_DECLINED(false),
    OTC_CONTRACT_EXPIRING(false),

    // Fallback
    GENERAL(false);

    private final boolean sendsEmail;

    NotificationType(boolean sendsEmail) {
        this.sendsEmail = sendsEmail;
    }
}
