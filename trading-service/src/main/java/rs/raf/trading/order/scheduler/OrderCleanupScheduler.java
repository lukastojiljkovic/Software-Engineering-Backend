package rs.raf.trading.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.trading.notification.model.NotificationType;
import rs.raf.trading.notification.service.NotificationService;
import rs.raf.trading.order.model.Order;
import rs.raf.trading.order.model.OrderStatus;
import rs.raf.trading.order.repository.OrderRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler za ciscenje ordera kojima je prosao settlement datum.
 *
 * Pokrece se svaki dan u 01:00 ujutru. Pronalazi PENDING ili APPROVED
 * ordere za hartije ciji je settlementDate prosao i postavlja ih na
 * DECLINED.
 *
 * Specifikacija: Celina 3 - "Kod hartija koje imaju settlement date,
 * i gde je taj datum prosao, postoji samo Decline opcija."
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2c): {@code @Scheduled} anotacija je
 * zadrzana verbatim, ali je USPAVANA — {@code TradingServiceApplication} nema
 * {@code @EnableScheduling} do cutover-a (2f). Monolit jos uvek vrti svoju
 * kopiju ovog cleanup posla.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupScheduler {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void cleanupExpiredOrders() {
        log.info("Pokrecem ciscenje ordera sa isteklim settlement datumom...");

        List<Order> candidates = orderRepository.findActiveNonDone();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int declinedCount = 0;

        for (Order order : candidates) {
            LocalDate settlement = order.getListing() != null
                    ? order.getListing().getSettlementDate()
                    : null;
            if (settlement == null || !settlement.isBefore(today)) {
                continue;
            }
            order.setStatus(OrderStatus.DECLINED);
            order.setApprovedBy("SYSTEM - Settlement date expired");
            order.setLastModification(now);
            orderRepository.save(order);
            log.info("Order {} (user={}, listing={}) declined - settlement {} passed",
                    order.getId(), order.getUserId(),
                    order.getListing().getTicker(), settlement);
            try {
                notificationService.notify(
                        order.getUserId(),
                        order.getUserRole(),
                        NotificationType.ORDER_CANCELLED,
                        "Nalog automatski otkazan",
                        "Vaš nalog za " + order.getListing().getTicker() + " je automatski otkazan jer je datum dospeća prošao.",
                        "ORDER",
                        order.getId()
                );
            } catch (Exception ex) {
                log.warn("Failed to send order cancelled notification for order #{}: {}", order.getId(), ex.getMessage());
            }
            declinedCount++;
        }

        log.info("Ciscenje zavrseno. Ukupno odbijeno: {}", declinedCount);
    }
}
