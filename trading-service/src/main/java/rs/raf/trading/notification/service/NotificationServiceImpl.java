package rs.raf.trading.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;
import rs.raf.banka2.contracts.internal.InternalNotificationRequest;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.notification.model.NotificationType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * [B4 — port iz banka2_bek] Trgovinski {@link NotificationService}.
 *
 * <p>Dva nezavisna kanala notifikacija:
 * <ol>
 *   <li><b>Email</b> — RabbitMQ {@code IN_APP_GENERIC} poruka ka
 *       {@code notification-service}. Aktivno samo kad
 *       {@code NotificationType.sendsEmail() == true}. Email primaoca razresava
 *       preko {@link BankaCoreClient#getUserById(String, Long)}; ako razresenje
 *       padne (banka-core nedostupan / nepoznat id), email se preskace.</li>
 *   <li><b>In-app</b> — async POST {@code /internal/notifications} ka banka-core
 *       da bi se notifikacija pojavila u FE NotificationBell-u i u
 *       {@code notifications} tabeli. Aktivno samo kad
 *       {@code NotificationType.sendsInApp() == true}. Async kroz
 *       {@link CompletableFuture#runAsync} — ne blokira trgovinski poziv.</li>
 * </ol>
 *
 * <p>Best-effort: bilo koja greska (broker pad, banka-core 5xx) se loguje na WARN
 * i NE rolluje back trgovinsku transakciju.
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final RabbitTemplate rabbitTemplate;
    private final BankaCoreClient bankaCoreClient;

    public NotificationServiceImpl(RabbitTemplate rabbitTemplate, BankaCoreClient bankaCoreClient) {
        this.rabbitTemplate = rabbitTemplate;
        this.bankaCoreClient = bankaCoreClient;
    }

    @Override
    public void notify(Long recipientId,
                       String recipientType,
                       NotificationType notificationType,
                       String title,
                       String body,
                       String referenceType,
                       Long referenceId) {
        if (recipientId == null || recipientType == null) {
            log.warn("Notification preskocena: recipientId/recipientType=null (type={}, title={})",
                    notificationType, title);
            return;
        }

        // Email kanal — samo ako tip eksplicitno trazi email.
        if (notificationType != null && notificationType.isSendsEmail()) {
            publishEmail(recipientId, recipientType, notificationType, title, body);
        }

        // In-app kanal — samo ako tip eksplicitno trazi in-app perzistenciju.
        // Async (CompletableFuture.runAsync) — ne blokira pozivnu trgovinsku
        // transakciju; bankaCoreClient.postNotification je vec best-effort
        // (sam swallow-uje sve greske kroz internal try/catch).
        if (notificationType != null && notificationType.isSendsInApp()) {
            InternalNotificationRequest internalReq = new InternalNotificationRequest(
                    recipientId,
                    recipientType,
                    notificationType.name(),
                    title,
                    body,
                    referenceType,
                    referenceId,
                    UUID.randomUUID().toString()
            );
            CompletableFuture.runAsync(() -> bankaCoreClient.postNotification(internalReq));
        }
    }

    /**
     * Publishuje email notifikaciju kao RabbitMQ {@code IN_APP_GENERIC} poruku.
     * Razresava email primaoca preko banka-core /internal/users RPC-a; ako
     * razresenje padne, email se preskace (ali in-app kanal nezavisno nastavlja).
     */
    private void publishEmail(Long recipientId,
                              String recipientType,
                              NotificationType notificationType,
                              String title,
                              String body) {
        try {
            String email;
            String firstName;
            try {
                var userDto = bankaCoreClient.getUserById(recipientType, recipientId);
                email = userDto.email();
                firstName = userDto.firstName();
            } catch (BankaCoreClientException ex) {
                log.warn("Email notifikacija preskocena: banka-core lookup pao za {} {} (type={}): {}",
                        recipientType, recipientId, notificationType, ex.getMessage());
                return;
            }

            if (email == null || email.isBlank()) {
                log.warn("Email notifikacija preskocena: nema email-a za {} {} (type={})",
                        recipientType, recipientId, notificationType);
                return;
            }

            Map<String, String> data = new HashMap<>();
            data.put("email", email);
            data.put("firstName", firstName != null ? firstName : "");
            data.put("title", title != null ? title : "");
            data.put("body", body != null ? body : "");

            rabbitTemplate.convertAndSend(
                    NotificationRabbit.EXCHANGE,
                    NotificationRabbit.EMAIL_ROUTING_KEY,
                    new NotificationMessage(NotificationKind.IN_APP_GENERIC, data));
        } catch (RuntimeException ex) {
            log.warn("Neuspeh email publish-a (type={}, recipient={} {}): {}",
                    notificationType, recipientType, recipientId, ex.getMessage());
        }
    }
}
