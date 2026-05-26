package rs.raf.trading.margin.event;

import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rs.raf.banka2.contracts.NotificationKind;
import rs.raf.banka2.contracts.NotificationMessage;
import rs.raf.banka2.contracts.NotificationRabbit;

import java.util.HashMap;
import java.util.Map;

/**
 * Premosti {@link MarginAccountBlockedEvent} (in-JVM Spring event koji
 * {@code MarginAccountService.checkMaintenanceMargin} publish-uje pri margin
 * call-u) na RabbitMQ ka {@code notification-service}.
 *
 * <p><b>Faza 2f-5a (cutover):</b> u monolitu je notifikacioni listener za margin
 * blokadu bio u istom JVM-u. trading-service je odvojen proces — email salje
 * {@code notification-service}, pa se cross-JVM most pravi preko RabbitMQ-a
 * (publish {@code NotificationKind.MARGIN_ACCOUNT_BLOCKED}). Margin-call scheduler
 * je sad ziv ({@code @EnableScheduling} preko {@code SchedulingConfig}), pa se
 * ovaj event stvarno emituje.
 *
 * <p>Best-effort: pad RabbitMQ publish-a se loguje, NE rusi margin-call
 * transakciju (isti obrazac kao monolitov {@code NotificationPublisher}). Email
 * se preskace ako vlasnik nema email (banka-core ga nije razresio) — bez
 * primaoca nema sta da se posalje.
 */
@Component
public class MarginAccountBlockedNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(MarginAccountBlockedNotificationListener.class);

    private final RabbitTemplate rabbitTemplate;
    /**
     * W2-T1: counter koji broji svaki margin call event (blocked margin account).
     * Inkrementuje se UVEK, cak i kad email vlasnika nije dostupan — sam margin
     * block je signal koji nas zanima (alert MarginCallsSurge gleda rate).
     */
    private final Counter marginCallsTotal;

    public MarginAccountBlockedNotificationListener(RabbitTemplate rabbitTemplate,
                                                    @Qualifier("marginCallsTotal") Counter marginCallsTotal) {
        this.rabbitTemplate = rabbitTemplate;
        this.marginCallsTotal = marginCallsTotal;
    }

    @EventListener
    public void onMarginAccountBlocked(MarginAccountBlockedEvent event) {
        // W2-T1: broj margin call-a (i kad nema email vlasnika — event je svejedno desio se).
        try {
            marginCallsTotal.increment();
        } catch (RuntimeException ex) {
            log.warn("Failed to increment margin call counter: {}", ex.getMessage());
        }

        if (event.getEmail() == null || event.getEmail().isBlank()) {
            log.warn("Margin call: blokiran racun bez email-a vlasnika — notifikacija se preskace");
            return;
        }
        Map<String, String> data = new HashMap<>();
        data.put("email", event.getEmail());
        data.put("maintenanceMargin", event.getMaintenanceMargin());
        data.put("initialMargin", event.getInitialMargin());
        data.put("deficit", event.getDeficit());
        try {
            rabbitTemplate.convertAndSend(NotificationRabbit.EXCHANGE,
                    NotificationRabbit.EMAIL_ROUTING_KEY,
                    new NotificationMessage(NotificationKind.MARGIN_ACCOUNT_BLOCKED, data));
        } catch (RuntimeException ex) {
            log.warn("Neuspeh publish-a margin-call notifikacije za {}: {}",
                    event.getEmail(), ex.getMessage());
        }
    }
}
