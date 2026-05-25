package rs.raf.banka2_bek.notification.model;

import lombok.Getter;

/**
 * [B1 — Foundation] Central registry of all notification types in the system.
 *
 * <p><b>Two orthogonal channel flags:</b>
 * <ul>
 *   <li>{@code sendsEmail} — kad je {@code true}, {@code NotificationServiceImpl.notify()}
 *       publishuje {@code IN_APP_GENERIC} poruku na RabbitMQ preko
 *       {@link rs.raf.banka2_bek.notification.NotificationPublisher#sendInAppGenericMail}.
 *       {@code notification-service} consumer rendera branded generic email template.</li>
 *   <li>{@code sendsInApp} — [BE-NTF-02] kad je {@code true}, persistira se in-app
 *       notifikacioni red u banka_core_db (vidljiv kroz notification bell na FE-u).
 *       Sluzi za per-type in-app suppression: neki dogadjaji su samo email,
 *       neki samo in-app, vecina oba.</li>
 * </ul>
 * Default za sve trenutno definisane tipove je {@code sendsInApp = true} —
 * cuva backward-compat (pre BE-NTF-02 svi su upisivani u DB). {@code GENERAL}
 * fallback takodje cuva in-app vidljivost (in-app zapis je default ako tip
 * nije eksplicitno mappovan).
 */
@Getter
public enum NotificationType {

    // [B4 — Petar] Financial / account events — both channels
    PAYMENT(true, true),
    TRANSFER(true, true),
    LIMIT_CHANGE(true, true),
    CARD_BLOCKED(true, true),
    CARD_UNBLOCKED(true, true),
    LOAN_CREATED(true, true),
    LOAN_APPROVED(true, true),
    LOAN_REJECTED(true, true),

    // [B4 — Petar] Order lifecycle events — in-app only (no email noise per order tick)
    ORDER_PENDING(false, true),
    ORDER_APPROVED(false, true),
    ORDER_DECLINED(false, true),
    ORDER_EXECUTED(false, true),
    ORDER_PARTIAL_FILL(false, true),
    ORDER_CANCELLED(false, true),

    // [B4 — Petar] OTC events — in-app only
    OTC_COUNTER_OFFER(false, true),
    OTC_ACCEPTED(false, true),
    OTC_DECLINED(false, true),
    OTC_CONTRACT_EXPIRING(false, true),

    // [B2 — Andjela] Account security events — both channels (kriticno, email + in-app)
    ACCOUNT_LOCKED(true, true),

    // [B5 — Aleksa Vucinic] Price alert triggered by scheduler when threshold crossed.
    PRICE_ALERT_TRIGGERED(true, true),

    // [B8 — Nikola Djurovic] Recurring order events — in-app only
    RECURRING_ORDER_SKIPPED(false, true),

    // [B1] Fallback type for ad-hoc notifications — in-app default channel
    GENERAL(false, true);

    private final boolean sendsEmail;
    private final boolean sendsInApp;

    NotificationType(boolean sendsEmail, boolean sendsInApp) {
        this.sendsEmail = sendsEmail;
        this.sendsInApp = sendsInApp;
    }
}
