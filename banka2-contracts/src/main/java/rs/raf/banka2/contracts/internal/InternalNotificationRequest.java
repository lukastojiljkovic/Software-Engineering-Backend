package rs.raf.banka2.contracts.internal;

/**
 * Cross-DB request za perzistenciju in-app notifikacije u banka-core
 * {@code notifications} tabelu. Trading-service publishuje email kao
 * RabbitMQ event (kao do sada), a DODATNO zove {@code POST /internal/notifications}
 * u banka-core da bi se notifikacija pojavila u FE NotificationBell-u i
 * {@code /notifications} listi za korisnika.
 *
 * <p>{@code type} je string ime {@code NotificationType} enum-a u banka-core
 * (npr. "ORDER_EXECUTED", "OTC_ACCEPTED", "RECURRING_ORDER_SKIPPED"). Ako u
 * banka-core enum-u tip ne postoji, banka-core mapira u {@code GENERAL}
 * fallback.
 *
 * <p>{@code recipientType} je "CLIENT" ili "EMPLOYEE" (mora se podudarati sa
 * {@code UserRole} konstantama u banka-core).
 *
 * <p>{@code referenceType}/{@code referenceId} su opcioni — kad su postavljeni,
 * FE moze deep-link-ovati na originalni resurs (npr. {@code "ORDER"} + orderId).
 *
 * <p>{@code idempotencyKey} (obicno UUID) sprecava dupli upis pri retry-u —
 * banka-core kesira odgovor po kljucu, naredni POST sa istim kljucem vraca
 * 200 OK bez ponovnog upisa.
 */
public record InternalNotificationRequest(
        Long recipientId,
        String recipientType,
        String type,
        String title,
        String message,
        String referenceType,
        Long referenceId,
        String idempotencyKey
) {
}
