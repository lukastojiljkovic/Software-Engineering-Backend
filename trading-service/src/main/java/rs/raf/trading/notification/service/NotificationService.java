package rs.raf.trading.notification.service;

import rs.raf.trading.notification.model.NotificationType;

/**
 * [B4 — port iz banka2_bek] Trgovinski notifikacioni servis.
 *
 * <p>Trading-service NEMA bazu sa {@code notifications} tabelom (vlasnik je
 * banka2_bek). Implementacija (vidi {@code NotificationServiceImpl}) publish-uje
 * RabbitMQ {@code IN_APP_GENERIC} poruke ka {@code notification-service}, koji
 * ih dalje rutira (email + eventualna in-app perzistencija u banka2_bek bazi
 * ako se u buducnosti doda RPC kroz {@code BankaCoreClient}).
 *
 * <p>Best-effort: trading code-base zove notify(...) i NE rolluje back svoju
 * transakciju ako notifikacija padne (isti obrazac kao monolit).
 */
public interface NotificationService {

    void notify(
            Long recipientId,
            String recipientType,
            NotificationType notificationType,
            String title,
            String body,
            String referenceType,
            Long referenceId
    );
}
