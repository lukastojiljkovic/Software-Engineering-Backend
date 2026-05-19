package rs.raf.trading.margin.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Spring {@code ApplicationEvent} koji se publish-uje kad margin racun bude
 * blokiran tokom dnevne provere maintenance margine (margin call).
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): u monolitu je listener na
 * ovaj event slao email vlasniku racuna. trading-service je odvojen proces —
 * notifikacioni listener se prevezuje na RabbitMQ ka {@code notification-service}
 * pri cutover-u (Faza 2f). Do tada je {@code MarginAccountService.checkMaintenanceMargin}
 * {@code @Scheduled} uspavan ({@code TradingServiceApplication} nema
 * {@code @EnableScheduling}), pa se ovaj event jos ne emituje automatski.
 *
 * TODO (Faza 2f): prevezati cross-JVM notifikaciju na RabbitMQ ka notification-service.
 */
@Getter
@AllArgsConstructor
public class MarginAccountBlockedEvent {
    private String email;
    private String maintenanceMargin;
    private String initialMargin;
    private String deficit;
}
