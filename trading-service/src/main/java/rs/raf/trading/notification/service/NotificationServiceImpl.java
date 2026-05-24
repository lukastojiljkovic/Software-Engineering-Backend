package rs.raf.trading.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.notification.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

/**
 * [B4 — port iz banka2_bek] Trgovinski {@link NotificationService}.
 *
 * <p>Publish-uje RabbitMQ {@code IN_APP_GENERIC} poruke ka {@code notification-service}
 * po istom obrascu kao {@code rs.raf.trading.margin.event.MarginAccountBlockedNotificationListener}.
 * Email primaoca razresava preko {@link BankaCoreClient#getUserById(String, Long)};
 * ako razresenje padne (banka-core nedostupan / nepoznat id), notifikacija se preskace.
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
        try {
            // Razresi email primaoca preko banka-core /internal/users RPC-a.
            // Pad lookup-a NE smerooi trgovinsku tx — samo loguj i izadji.
            String email;
            String firstName;
            try {
                var userDto = bankaCoreClient.getUserById(recipientType, recipientId);
                email = userDto.email();
                firstName = userDto.firstName();
            } catch (BankaCoreClientException ex) {
                log.warn("Notification preskocena: banka-core lookup pao za {} {} (type={}): {}",
                        recipientType, recipientId, notificationType, ex.getMessage());
                return;
            }

            if (email == null || email.isBlank()) {
                log.warn("Notification preskocena: nema email-a za {} {} (type={})",
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
            log.warn("Neuspeh publish-a notifikacije (type={}, recipient={} {}): {}",
                    notificationType, recipientType, recipientId, ex.getMessage());
        }
    }
}
